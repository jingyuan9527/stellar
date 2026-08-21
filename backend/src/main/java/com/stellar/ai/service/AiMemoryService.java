package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.ai.entity.AiChatMessage;
import com.stellar.ai.entity.AiChatSession;
import com.stellar.ai.entity.AiMemory;
import com.stellar.ai.mapper.AiChatMessageMapper;
import com.stellar.ai.mapper.AiChatSessionMapper;
import com.stellar.ai.mapper.AiMemoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.stellar.system.service.UserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 长期记忆：定期整理会话为事实陈述（按账号），对话时注入 system prompt。
 * <p>定时任务每日 3 点整理近 7 天未整理的会话；支持手动触发某会话整理。
 * 幂等：source_session_id 关联，定时任务跳过已整理会话；手动触发不检查（可重复生成）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMemoryService {

    private final AiMemoryMapper memoryMapper;
    private final AiChatMessageMapper messageMapper;
    private final AiChatSessionMapper sessionMapper;
    private final UserService userService;
    private final AiChatService aiChatService;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    private static final int MAX_DIALOGUE_CHARS = 8000;
    private static final int RECENT_MEMORIES = 20;

    /**
     * 管理后台分页查询全部记忆（带用户名）。
     */
    public Page<Map<String, Object>> pageAll(int pageNum, int pageSize) {
        Page<AiMemory> page = new Page<>(pageNum, pageSize);
        Page<AiMemory> result = memoryMapper.selectPage(page,
                new LambdaQueryWrapper<AiMemory>().orderByDesc(AiMemory::getCreateTime));
        return mapWithUsername(result);
    }

    /**
     * 某用户的记忆分页（带用户名）。
     */
    public Page<Map<String, Object>> pageByUser(Long userId, int pageNum, int pageSize) {
        Page<AiMemory> page = new Page<>(pageNum, pageSize);
        Page<AiMemory> result = memoryMapper.selectPage(page,
                new LambdaQueryWrapper<AiMemory>()
                        .eq(AiMemory::getUserId, userId)
                        .orderByDesc(AiMemory::getCreateTime));
        return mapWithUsername(result);
    }

    /**
     * 聊天注入用：取该用户最近 N 条记忆内容。
     */
    public List<String> listByUser(Long userId) {
        List<AiMemory> list = memoryMapper.selectList(new LambdaQueryWrapper<AiMemory>()
                .eq(AiMemory::getUserId, userId)
                .orderByDesc(AiMemory::getCreateTime)
                .last("LIMIT " + RECENT_MEMORIES));
        List<String> result = new ArrayList<>();
        for (AiMemory m : list) {
            result.add(m.getContent());
        }
        return result;
    }

    public void update(Long id, String content) {
        AiMemory exist = memoryMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("记忆不存在");
        }
        exist.setContent(content);
        memoryMapper.updateById(exist);
    }

    public void delete(Long id) {
        memoryMapper.deleteById(id);
    }

    /**
     * 手动新增一条长期记忆（管理员指定用户与内容）。
     */
    public void create(Long userId, String content) {
        if (userService.getById(userId) == null) {
            throw new BusinessException("用户不存在");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("记忆内容不能为空");
        }
        AiMemory mem = new AiMemory();
        mem.setUserId(userId);
        mem.setContent(content.trim());
        mem.setCreateTime(LocalDateTime.now());
        memoryMapper.insert(mem);
        log.info("[长期记忆] 手动新增 userId={} contentLen={}", userId, content.length());
    }

    /**
     * 手动触发某会话整理为记忆（不幂等检查，可重复）。
     */
    public int summarizeSession(Long sessionId) {
        AiChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        if (!"account".equals(session.getSubjectType())) {
            throw new BusinessException("仅登录用户会话可整理记忆");
        }
        Long userId = parseLong(session.getSubjectId());
        if (userId == null) {
            throw new BusinessException("会话用户ID无效");
        }
        List<AiChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getCreateTime));
        if (messages.isEmpty()) {
            throw new BusinessException("会话无消息");
        }
        return doSummarize(session, userId, messages);
    }

    /**
     * 定时任务：每日 3 点整理近 7 天未整理的登录用户会话。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void summarizeScheduled() {
        List<Long> sessionIds = jdbcTemplate.queryForList(
                "SELECT s.id FROM ai_chat_session s " +
                        "WHERE s.subject_type='account' AND s.deleted=0 " +
                        "AND s.update_time > now() - interval '7 day' " +
                        "AND NOT EXISTS (SELECT 1 FROM ai_memory m WHERE m.source_session_id=s.id AND m.deleted=0) " +
                        "ORDER BY s.update_time ASC LIMIT 50",
                Long.class);
        if (sessionIds.isEmpty()) {
            return;
        }
        log.info("[长期记忆] 定时整理 {} 个会话", sessionIds.size());
        for (Long sid : sessionIds) {
            try {
                summarizeSession(sid);
            } catch (Exception e) {
                log.warn("[长期记忆] 整理会话 {} 失败: {}", sid, e.getMessage());
            }
        }
    }

    // ===== 内部 =====

    private int doSummarize(AiChatSession session, Long userId, List<AiChatMessage> messages) {
        StringBuilder dialogue = new StringBuilder();
        for (AiChatMessage m : messages) {
            dialogue.append(m.getRole()).append(": ").append(m.getContent()).append('\n');
            if (dialogue.length() > MAX_DIALOGUE_CHARS) {
                break;
            }
        }
        String systemPrompt = "你是一个记忆整理助手。请从下面的对话中提取关于用户的持久事实" +
                "（如偏好、背景、身份、重要约定等），每条一行，只输出事实本身，" +
                "不要解释、不要编号、不要前后缀。如果无可提取的事实，直接输出空。";
        List<Map<String, String>> reqMessages = new ArrayList<>();
        Map<String, String> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        reqMessages.add(sys);
        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", dialogue.toString());
        reqMessages.add(user);

        String result;
        try {
            result = aiChatService.chatCompletionWithMessages(reqMessages, null);
        } catch (Exception e) {
            log.error("[长期记忆] 摘要 LLM 调用失败 session={}: {}", session.getId(), e.getMessage(), e);
            throw new BusinessException("记忆摘要生成失败: " + e.getMessage());
        }
        List<String> facts = new ArrayList<>();
        if (result != null && !result.isBlank()) {
            for (String line : result.split("\n")) {
                String fact = line.strip();
                if (fact.isEmpty() || fact.startsWith("无") || "空".equals(fact)) {
                    continue;
                }
                facts.add(fact);
            }
        }
        Integer count = new TransactionTemplate(transactionManager).execute(status -> {
            int inserted = 0;
            for (String fact : facts) {
                AiMemory mem = new AiMemory();
                mem.setUserId(userId);
                mem.setContent(fact);
                mem.setSourceSessionId(session.getId());
                mem.setCreateTime(LocalDateTime.now());
                memoryMapper.insert(mem);
                inserted++;
            }
            return inserted;
        });
        log.info("[长期记忆] 会话 {} 整理出 {} 条记忆", session.getId(), count);
        return count == null ? 0 : count;
    }

    private Page<Map<String, Object>> mapWithUsername(Page<AiMemory> page) {
        Map<Long, String> nameMap = new HashMap<>();
        if (!page.getRecords().isEmpty()) {
            List<Long> userIds = page.getRecords().stream().map(AiMemory::getUserId).distinct().toList();
            nameMap.putAll(userService.getUsernameMap(userIds));
        }
        Page<Map<String, Object>> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<Map<String, Object>> records = new ArrayList<>();
        for (AiMemory m : page.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", m.getId());
            row.put("userId", m.getUserId());
            row.put("username", nameMap.get(m.getUserId()));
            row.put("content", m.getContent());
            row.put("sourceSessionId", m.getSourceSessionId());
            row.put("createTime", m.getCreateTime());
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
