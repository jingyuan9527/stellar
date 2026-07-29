package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.entity.AiChatMessage;
import com.stellar.entity.AiChatSession;
import com.stellar.entity.AiPersona;
import com.stellar.entity.SysUser;
import com.stellar.mapper.AiChatMessageMapper;
import com.stellar.mapper.AiChatSessionMapper;
import com.stellar.mapper.AiPersonaMapper;
import com.stellar.mapper.SysUserMapper;
import com.stellar.vo.AiChatResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 聊天会话服务：会话+消息 CRUD（游客按 IP），多轮流式（人设 system + RAG 注入 + 记忆注入）。
 * <p>流式结束落 assistant 消息到 ai_chat_message；token usage 由 AiChatService 记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatSessionService {

    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiPersonaMapper personaMapper;
    private final SysUserMapper userMapper;
    private final AiKnowledgeService knowledgeService;
    private final AiMemoryService memoryService;
    private final AiChatService aiChatService;
    private final AiChatToolService aiChatToolService;

    private static final int HISTORY_LIMIT = 20;
    private static final int RAG_TOP_K = 4;

    // ===== 会话 CRUD =====

    @Transactional(rollbackFor = Exception.class)
    public AiChatSession createSession(Long personaId, Long kbId, String title) {
        String subjectType = currentSubjectType();
        String subjectId = currentSubjectId();
        // 游客强制无知识库（RAG 仅登录可用）
        if (!"account".equals(subjectType)) {
            kbId = null;
        }
        AiChatSession session = new AiChatSession();
        session.setPersonaId(personaId);
        session.setKbId(kbId);
        session.setTitle(StringUtils.hasText(title) ? title.trim() : "新对话");
        session.setSubjectType(subjectType);
        session.setSubjectId(subjectId);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        log.info("[AI聊天] 创建会话 id={} subject={}:{}", session.getId(), subjectType, subjectId);
        return session;
    }

    /**
     * 当前主体的会话列表（登录按账号，游客按 IP）。
     */
    public List<AiChatSession> listMySessions() {
        return sessionMapper.selectList(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getSubjectType, currentSubjectType())
                .eq(AiChatSession::getSubjectId, currentSubjectId())
                .orderByDesc(AiChatSession::getUpdateTime));
    }

    /**
     * 管理后台分页查所有会话（带用户名）。
     */
    public Page<Map<String, Object>> pageAllSessions(int pageNum, int pageSize) {
        Page<AiChatSession> page = new Page<>(pageNum, pageSize);
        Page<AiChatSession> result = sessionMapper.selectPage(page,
                new LambdaQueryWrapper<AiChatSession>()
                        .orderByDesc(AiChatSession::getUpdateTime));
        return mapWithUsername(result);
    }

    public List<AiChatMessage> getMessages(Long sessionId) {
        checkOwnership(sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreateTime));
    }

    /**
     * 管理员查看任意会话消息（无归属校验，需登录鉴权）。
     */
    public List<AiChatMessage> getMessagesAdmin(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreateTime));
    }

    public void updateSession(Long id, String title) {
        AiChatSession s = sessionMapper.selectById(id);
        if (s == null) {
            throw new BusinessException("会话不存在");
        }
        s.setTitle(title);
        s.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(s);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long id) {
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getSessionId, id));
        sessionMapper.deleteById(id);
        log.info("[AI聊天] 删除会话 id={}", id);
    }

    /**
     * 管理员删除任意会话（无归属校验，需登录鉴权）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessionAdmin(Long id) {
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getSessionId, id));
        sessionMapper.deleteById(id);
        log.info("[AI聊天] 管理员删除会话 id={}", id);
    }

    /**
     * 删除当前主体的全部会话（游客清空历史用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteMySessions() {
        List<AiChatSession> mine = listMySessions();
        if (mine.isEmpty()) {
            return 0;
        }
        List<Long> ids = mine.stream().map(AiChatSession::getId).collect(Collectors.toList());
        messageMapper.delete(new LambdaQueryWrapper<AiChatMessage>().in(AiChatMessage::getSessionId, ids));
        sessionMapper.deleteBatchIds(ids);
        return ids.size();
    }

    // ===== 流式多轮聊天 =====

    /**
     * 多轮流式聊天。存 user 消息 → 组装 messages（人设+RAG+记忆+历史）→ 流式 → 完成存 assistant。
     * <p>登录用户带 tools（function calling：画图/TTS），assistant 消息可能带附件；
     * 游客纯文本无 tools。
     */
    public SseEmitter streamChat(Long sessionId, String userMessage, Long modelId, String voice) {
        AiChatSession session = checkOwnership(sessionId);
        if (!StringUtils.hasText(userMessage)) {
            throw new BusinessException("消息不能为空");
        }

        // 1. 存 user 消息
        AiChatMessage userMsg = new AiChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(userMessage);
        userMsg.setTokens(userMessage.length());
        userMsg.setCreateTime(LocalDateTime.now());
        messageMapper.insert(userMsg);

        // 2. 组装 messages
        List<Map<String, String>> messages = buildMessages(session, userMessage);

        // 3. 流式
        if (StpUtil.isLogin()) {
            // 登录：带 tools（function calling），落 assistant 消息含附件
            List<Map<String, Object>> messagesObj = messages.stream()
                    .map(m -> new HashMap<String, Object>(m))
                    .collect(Collectors.toList());
            return aiChatService.streamMultiChatWithTools(messagesObj, modelId,
                    aiChatToolService.getToolDefinitions(), voice, ar -> {
                        AiChatMessage aMsg = new AiChatMessage();
                        aMsg.setSessionId(sessionId);
                        aMsg.setRole("assistant");
                        aMsg.setContent(ar.content() == null ? "" : ar.content());
                        aMsg.setTokens(ar.content() == null ? 0 : ar.content().length());
                        aMsg.setAttachmentType(ar.attachmentType());
                        aMsg.setAttachmentFileId(ar.attachmentFileId());
                        aMsg.setCreateTime(LocalDateTime.now());
                        messageMapper.insert(aMsg);
                        updateSessionTitle(session, userMessage);
                    });
        }
        // 游客：纯文本，无 tools
        return aiChatService.streamMultiChat(messages, modelId, fullText -> {
            AiChatMessage aMsg = new AiChatMessage();
            aMsg.setSessionId(sessionId);
            aMsg.setRole("assistant");
            aMsg.setContent(fullText);
            aMsg.setTokens(fullText == null ? 0 : fullText.length());
            aMsg.setCreateTime(LocalDateTime.now());
            messageMapper.insert(aMsg);
            updateSessionTitle(session, userMessage);
        });
    }

    /** 首轮用用户消息截断生成标题；每轮更新会话时间 */
    private void updateSessionTitle(AiChatSession session, String userMessage) {
        if ("新对话".equals(session.getTitle())) {
            String t = userMessage.length() > 20 ? userMessage.substring(0, 20) + "…" : userMessage;
            session.setTitle(t);
        }
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(session);
    }

    // ===== 内部 =====

    /**
     * 组装 OpenAI messages：system（人设+记忆+RAG）+ 历史最近 N 条（含刚存的 user）。
     */
    private List<Map<String, String>> buildMessages(AiChatSession session, String currentUserMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        // system：人设 + 记忆 + RAG
        String systemText = buildSystemText(session, currentUserMessage);
        if (StringUtils.hasText(systemText)) {
            Map<String, String> sys = new HashMap<>();
            sys.put("role", "system");
            sys.put("content", systemText);
            messages.add(sys);
        }

        // 历史：最近 N 条（倒序取后正序），含刚存的 user 消息
        List<AiChatMessage> recent = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, session.getId())
                .orderByDesc(AiChatMessage::getCreateTime)
                .last("LIMIT " + HISTORY_LIMIT));
        Collections.reverse(recent);
        for (AiChatMessage m : recent) {
            if ("system".equals(m.getRole())) continue;
            Map<String, String> msg = new HashMap<>();
            msg.put("role", m.getRole());
            msg.put("content", m.getContent());
            messages.add(msg);
        }
        return messages;
    }

    /**
     * 拼装 system 文本：人设 systemPrompt 为基，登录用户追加长期记忆，关联知识库时追加 RAG 检索片段。
     */
    private String buildSystemText(AiChatSession session, String query) {
        StringBuilder sb = new StringBuilder();
        // 人设
        if (session.getPersonaId() != null) {
            AiPersona persona = personaMapper.selectById(session.getPersonaId());
            if (persona != null && StringUtils.hasText(persona.getSystemPrompt())) {
                sb.append(persona.getSystemPrompt()).append("\n\n");
            }
        }
        // 长期记忆（仅登录）
        if ("account".equals(session.getSubjectType())) {
            Long userId = parseLong(session.getSubjectId());
            if (userId != null) {
                List<String> memories = memoryService.listByUser(userId);
                if (!memories.isEmpty()) {
                    sb.append("以下是关于用户的长期记忆，请结合这些信息理解与回应用户：\n");
                    for (String mem : memories) {
                        sb.append("- ").append(mem).append('\n');
                    }
                    sb.append('\n');
                }
            }
        }
        // RAG 知识库检索（仅登录且关联了知识库）
        if ("account".equals(session.getSubjectType()) && session.getKbId() != null) {
            try {
                List<String> chunks = knowledgeService.search(session.getKbId(), query, RAG_TOP_K);
                if (!chunks.isEmpty()) {
                    sb.append("以下是与用户问题相关的知识库资料，请结合资料回答：\n");
                    for (int i = 0; i < chunks.size(); i++) {
                        sb.append("--- 资料").append(i + 1).append(" ---\n").append(chunks.get(i)).append('\n');
                    }
                    sb.append('\n');
                }
            } catch (Exception e) {
                log.warn("[AI聊天] RAG 检索失败 session={}: {}", session.getId(), e.getMessage());
            }
        }
        return sb.toString().trim();
    }

    /**
     * 校验会话归属当前主体。
     */
    private AiChatSession checkOwnership(Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        if (!currentSubjectType().equals(session.getSubjectType())
                || !currentSubjectId().equals(session.getSubjectId())) {
            throw new BusinessException("无权访问该会话");
        }
        return session;
    }

    private String currentSubjectType() {
        return StpUtil.isLogin() ? "account" : "ip";
    }

    private String currentSubjectId() {
        return StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : getClientIp();
    }

    private String getClientIp() {
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String ip = req.getHeader("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                ip = ip.split(",")[0].trim();
            }
            if (ip == null || ip.isBlank()) {
                ip = req.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isBlank()) {
                ip = req.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private Page<Map<String, Object>> mapWithUsername(Page<AiChatSession> page) {
        Map<Long, String> nameMap = new HashMap<>();
        if (!page.getRecords().isEmpty()) {
            List<Long> userIds = page.getRecords().stream()
                    .filter(s -> "account".equals(s.getSubjectType()))
                    .map(s -> parseLong(s.getSubjectId()))
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!userIds.isEmpty()) {
                userMapper.selectBatchIds(userIds).forEach(u -> nameMap.put(u.getId(), u.getUsername()));
            }
        }
        Page<Map<String, Object>> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<Map<String, Object>> records = new ArrayList<>();
        for (AiChatSession s : page.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", s.getId());
            row.put("title", s.getTitle());
            row.put("personaId", s.getPersonaId());
            row.put("kbId", s.getKbId());
            row.put("subjectType", s.getSubjectType());
            row.put("subjectId", s.getSubjectId());
            row.put("username", "account".equals(s.getSubjectType())
                    ? nameMap.get(parseLong(s.getSubjectId())) : null);
            row.put("createTime", s.getCreateTime());
            row.put("updateTime", s.getUpdateTime());
            records.add(row);
        }
        result.setRecords(records);
        return result;
    }

    private Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }
}
