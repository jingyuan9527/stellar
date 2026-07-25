package com.stellar.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.dto.ConchAnswerDTO;
import com.stellar.dto.ConchAnswerQueryDTO;
import com.stellar.dto.ConchAskDTO;
import com.stellar.entity.ConchAnswer;
import com.stellar.entity.ConchRecord;
import com.stellar.entity.SysFile;
import com.stellar.mapper.ConchAnswerMapper;
import com.stellar.mapper.ConchRecordMapper;
import com.stellar.mapper.SysFileMapper;
import com.stellar.vo.ConchAnswerVO;
import com.stellar.vo.ConchAskResultVO;
import com.stellar.vo.ConchRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 神奇海螺服务：AI 语义匹配预设回答 + 随机兜底 + 提问历史记录。
 * <p>匹配流程：取启用预设 → LLM 返回 top-3 id → 随机选 1；失败/格式错则全库随机兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConchService {

    private final ConchAnswerMapper answerMapper;
    private final ConchRecordMapper recordMapper;
    private final SysFileMapper fileMapper;
    private final AiChatService aiChatService;
    private final SysSettingService sysSettingService;
    private final ObjectMapper objectMapper;

    /**
     * 提问：AI 从启用预设里选 top-3，随机取 1 条；失败兜底全库随机。
     */
    public ConchAskResultVO ask(ConchAskDTO dto) {
        String question = dto.getQuestion().trim();

        List<ConchAnswer> answers = answerMapper.selectList(new LambdaQueryWrapper<ConchAnswer>()
                .eq(ConchAnswer::getEnabled, 1)
                .orderByAsc(ConchAnswer::getSortOrder));
        if (answers.isEmpty()) {
            throw new BusinessException("海螺暂无预设回答，请联系管理员添加");
        }

        Map<Long, ConchAnswer> answerMap = answers.stream()
                .collect(Collectors.toMap(ConchAnswer::getId, a -> a));

        Long answerId = null;
        String matchSource = "random";

        // AI 语义匹配开关：关闭则纯随机，不调 LLM（省 token）
        boolean aiEnabled = sysSettingService.getAsBoolean("conch_ai_enabled", true);

        if (aiEnabled) {
            // AI 语义匹配（失败不影响主流程，兜底随机）
            try {
                String prompt = buildMatchPrompt(question, answers);
                String llmResult = aiChatService.chatCompletion(prompt);
                List<Long> ids = parseTopIds(llmResult).stream()
                        .filter(answerMap::containsKey)
                        .limit(3)
                        .collect(Collectors.toList());
                if (!ids.isEmpty()) {
                    answerId = ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
                    matchSource = "ai";
                }
            } catch (Exception e) {
                log.warn("[神奇海螺] AI 匹配失败，兜底随机: {}", e.getMessage());
            }
        } else {
            log.info("[神奇海螺] AI 匹配已关闭，纯随机");
        }

        // 兜底：全库随机抽 1
        if (answerId == null) {
            ConchAnswer random = answers.get(ThreadLocalRandom.current().nextInt(answers.size()));
            answerId = random.getId();
        }

        ConchAnswer picked = answerMap.get(answerId);

        // 记录提问历史（失败不影响回答）
        try {
            ConchRecord record = new ConchRecord();
            record.setQuestionText(question);
            record.setAnswerId(answerId);
            if (StpUtil.isLogin()) {
                record.setUserId(StpUtil.getLoginIdAsLong());
            }
            record.setCreateTime(LocalDateTime.now());
            recordMapper.insert(record);
        } catch (Exception e) {
            log.warn("[神奇海螺] 记录提问历史失败: {}", e.getMessage());
        }

        log.info("[神奇海螺] 问题=\"{}\" 命中=\"{}\"(id={}) source={}",
                question, picked.getAnswerText(), answerId, matchSource);

        ConchAskResultVO vo = new ConchAskResultVO();
        vo.setAnswerId(answerId);
        vo.setAnswerText(picked.getAnswerText());
        vo.setAudioUrl("/tts/conch/answer/" + answerId + "/audio");
        return vo;
    }

    /**
     * 取预设回答对应的音频文件（游客可读）。
     */
    public SysFile getAnswerFile(Long answerId) {
        ConchAnswer answer = answerMapper.selectById(answerId);
        if (answer == null) {
            throw new BusinessException("预设回答不存在");
        }
        SysFile file = fileMapper.selectFullById(answer.getFileId());
        if (file == null || file.getData() == null) {
            throw new BusinessException("音频文件不存在");
        }
        return file;
    }

    /**
     * 预设回答分页（管理后台）。
     */
    public Page<ConchAnswerVO> answerPage(ConchAnswerQueryDTO query) {
        Page<ConchAnswer> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<ConchAnswer> wrapper = new LambdaQueryWrapper<ConchAnswer>()
                .like(StringUtils.hasText(query.getAnswerText()),
                        ConchAnswer::getAnswerText, query.getAnswerText())
                .eq(query.getEnabled() != null, ConchAnswer::getEnabled, query.getEnabled())
                .orderByAsc(ConchAnswer::getSortOrder)
                .orderByDesc(ConchAnswer::getCreateTime);
        Page<ConchAnswer> result = answerMapper.selectPage(page, wrapper);
        Page<ConchAnswerVO> voPage = new Page<>();
        voPage.setTotal(result.getTotal());
        voPage.setSize(result.getSize());
        voPage.setCurrent(result.getCurrent());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(this::toAnswerVO).collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 新增预设回答（校验音频文件存在）。
     */
    public void createAnswer(ConchAnswerDTO dto) {
        if (fileMapper.selectById(dto.getFileId()) == null) {
            throw new BusinessException("音频文件不存在，请先上传");
        }
        ConchAnswer answer = new ConchAnswer();
        answer.setAnswerText(dto.getAnswerText().trim());
        answer.setMatchDescription(StringUtils.hasText(dto.getMatchDescription())
                ? dto.getMatchDescription().trim() : null);
        answer.setFileId(dto.getFileId());
        answer.setEnabled(1);
        answer.setSortOrder(0);
        answer.setCreateTime(LocalDateTime.now());
        answerMapper.insert(answer);
        log.info("[神奇海螺] 新增预设 id={} text=\"{}\"", answer.getId(), answer.getAnswerText());
    }

    /**
     * 编辑预设回答（fileId 变更时校验新文件存在）。
     */
    public void updateAnswer(ConchAnswerDTO dto) {
        ConchAnswer exist = answerMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException("预设回答不存在");
        }
        if (dto.getFileId() != null && !dto.getFileId().equals(exist.getFileId())) {
            if (fileMapper.selectById(dto.getFileId()) == null) {
                throw new BusinessException("音频文件不存在");
            }
            exist.setFileId(dto.getFileId());
        }
        exist.setAnswerText(dto.getAnswerText().trim());
        exist.setMatchDescription(StringUtils.hasText(dto.getMatchDescription())
                ? dto.getMatchDescription().trim() : null);
        answerMapper.updateById(exist);
        log.info("[神奇海螺] 编辑预设 id={}", dto.getId());
    }

    /**
     * 删除预设回答（逻辑删除）。
     */
    public void deleteAnswer(Long id) {
        answerMapper.deleteById(id);
        log.info("[神奇海螺] 删除预设 id={}", id);
    }

    /**
     * 切换启用状态。
     */
    public void toggleEnabled(Long id, Integer enabled) {
        ConchAnswer answer = answerMapper.selectById(id);
        if (answer == null) {
            throw new BusinessException("预设回答不存在");
        }
        answer.setEnabled(enabled);
        answerMapper.updateById(answer);
        log.info("[神奇海螺] 切换启用 id={} enabled={}", id, enabled);
    }

    /**
     * 提问历史分页（管理后台，关联查出命中回答文本）。
     */
    public Page<ConchRecordVO> recordPage(Integer pageNum, Integer pageSize) {
        Page<ConchRecord> page = new Page<>(pageNum, pageSize);
        Page<ConchRecord> result = recordMapper.selectPage(page,
                new LambdaQueryWrapper<ConchRecord>().orderByDesc(ConchRecord::getCreateTime));

        Set<Long> answerIds = result.getRecords().stream()
                .map(ConchRecord::getAnswerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> textMap = new HashMap<>();
        if (!answerIds.isEmpty()) {
            answerMapper.selectBatchIds(answerIds)
                    .forEach(a -> textMap.put(a.getId(), a.getAnswerText()));
        }

        Page<ConchRecordVO> voPage = new Page<>();
        voPage.setTotal(result.getTotal());
        voPage.setSize(result.getSize());
        voPage.setCurrent(result.getCurrent());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(r -> {
            ConchRecordVO vo = new ConchRecordVO();
            vo.setId(r.getId());
            vo.setQuestionText(r.getQuestionText());
            vo.setAnswerId(r.getAnswerId());
            vo.setAnswerText(r.getAnswerId() != null ? textMap.get(r.getAnswerId()) : null);
            vo.setUserId(r.getUserId());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).collect(Collectors.toList()));
        return voPage;
    }

    /**
     * 构建匹配提示词：要求 LLM 从预设列表选 top-3，只返回 JSON。
     */
    private String buildMatchPrompt(String question, List<ConchAnswer> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是\"神奇海螺\"，一个类似神奇八号球的占卜玩具。用户会问你问题，")
                .append("你需要从下面预设的回答列表里，选出3条语义上最贴合用户问题的回答，按贴合度从高到低排序。\n\n");
        sb.append("预设回答列表：\n");
        for (ConchAnswer a : answers) {
            sb.append("[").append(a.getId()).append("] 回答文本：").append(a.getAnswerText());
            if (StringUtils.hasText(a.getMatchDescription())) {
                sb.append(" | 匹配描述：").append(a.getMatchDescription());
            }
            sb.append("\n");
        }
        sb.append("\n用户问题：").append(question).append("\n\n");
        sb.append("请只返回一个 JSON，格式为 {\"ids\":[最贴合的id, 次贴合的id, 第三贴合的id]}，")
                .append("不要返回任何其他内容。");
        return sb.toString();
    }

    /**
     * 解析 LLM 返回的 top id 列表（兼容 markdown 代码块包裹）。
     */
    private List<Long> parseTopIds(String llmResult) {
        if (llmResult == null || llmResult.isBlank()) {
            return Collections.emptyList();
        }
        String text = llmResult.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            log.warn("[神奇海螺] LLM 返回非 JSON: raw={}", llmResult);
            return Collections.emptyList();
        }
        try {
            JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
            JsonNode idsNode = node.path("ids");
            if (!idsNode.isArray()) {
                log.warn("[神奇海螺] LLM 返回无 ids 数组: raw={}", llmResult);
                return Collections.emptyList();
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode n : idsNode) {
                ids.add(n.asLong());
            }
            return ids;
        } catch (Exception e) {
            log.warn("[神奇海螺] 解析 LLM JSON 失败: raw={} err={}", llmResult, e.getMessage());
            return Collections.emptyList();
        }
    }

    private ConchAnswerVO toAnswerVO(ConchAnswer a) {
        ConchAnswerVO vo = new ConchAnswerVO();
        vo.setId(a.getId());
        vo.setAnswerText(a.getAnswerText());
        vo.setMatchDescription(a.getMatchDescription());
        vo.setFileId(a.getFileId());
        vo.setEnabled(a.getEnabled());
        vo.setSortOrder(a.getSortOrder());
        vo.setCreateTime(a.getCreateTime());
        return vo;
    }
}
