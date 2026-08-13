package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.RagEvalCaseDTO;
import com.stellar.ai.entity.RagEvalCase;
import com.stellar.ai.entity.RagEvalResult;
import com.stellar.ai.entity.RagFeedback;
import com.stellar.ai.entity.AiChatMessage;
import com.stellar.ai.mapper.RagEvalCaseMapper;
import com.stellar.ai.mapper.RagEvalResultMapper;
import com.stellar.ai.mapper.RagFeedbackMapper;
import com.stellar.ai.mapper.AiChatMessageMapper;
import com.stellar.ai.service.rag.RagHit;
import com.stellar.ai.service.rag.RagSearchService;
import com.stellar.ai.service.rag.RetrievalResult;
import com.stellar.ai.vo.RagEvalRunVO;
import com.stellar.ai.vo.RagEvalRunVO.RagEvalDetailVO;
import com.stellar.ai.vo.RagEvalRunVO.RagEvalHitVO;
import com.stellar.ai.vo.RagFeedbackVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RAG 数据飞轮（期4）：golden set 评估用例 CRUD、离线跑分（纯检索路径 recall@k，不开 LLM）、
 * bad case 复盘（反馈分页带消息快照）。
 * <p>跑分用 {@link RagSearchService#searchTopK} 与线上检索路径共享 RRF 融合逻辑，
 * 保证"评估的数字 = 线上召回能力的数字"（改分块/阈值/融合后跑分防回归）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalService {

    private final RagEvalCaseMapper caseMapper;
    private final RagEvalResultMapper resultMapper;
    private final RagFeedbackMapper feedbackMapper;
    private final AiChatMessageMapper messageMapper;
    private final RagSearchService ragSearchService;
    private final ObjectMapper objectMapper;

    /** 跑分 top-k（与线上注入量一致的 recall@k） */
    @Value("${stellar.rag.top-k:4}")
    private int topK;

    private static final DateTimeFormatter RUN_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // ===== 评估用例 CRUD =====

    public Page<RagEvalCase> pageCases(int pageNum, int pageSize) {
        return caseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<RagEvalCase>().orderByAsc(RagEvalCase::getId));
    }

    public void createCase(RagEvalCaseDTO dto) {
        RagEvalCase c = new RagEvalCase();
        c.setQuery(dto.getQuery().trim());
        c.setKbId(dto.getKbId());
        c.setExpectedSources(toJson(dto.getExpectedSources()));
        c.setNote(dto.getNote());
        c.setCreateTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());
        caseMapper.insert(c);
        log.info("[RAG评估] 新增用例 id={} query={}", c.getId(), c.getQuery());
    }

    public void updateCase(RagEvalCaseDTO dto) {
        RagEvalCase c = caseMapper.selectById(dto.getId());
        if (c == null) {
            throw new BusinessException("评估用例不存在");
        }
        c.setQuery(dto.getQuery().trim());
        c.setKbId(dto.getKbId());
        c.setExpectedSources(toJson(dto.getExpectedSources()));
        c.setNote(dto.getNote());
        c.setUpdateTime(LocalDateTime.now());
        caseMapper.updateById(c);
        log.info("[RAG评估] 更新用例 id={} query={}", c.getId(), c.getQuery());
    }

    public void deleteCase(Long id) {
        // 保留历史跑分结果（只删用例定义）
        caseMapper.deleteById(id);
        log.info("[RAG评估] 删除用例 id={}", id);
    }

    // ===== 跑分 =====

    /**
     * 跑分：对全部评估用例走纯检索路径（不改写/不重排/不开 loop，不调 LLM），
     * 算 recall@k（期望来源命中率）并按批次落 rag_eval_result 供回归对比。
     */
    public RagEvalRunVO runEvaluation() {
        List<RagEvalCase> cases = caseMapper.selectList(
                new LambdaQueryWrapper<RagEvalCase>().orderByAsc(RagEvalCase::getId));
        if (cases.isEmpty()) {
            throw new BusinessException("评估集为空，先添加评估用例再跑分");
        }
        String runId = "R" + LocalDateTime.now().format(RUN_TS)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        long start = System.currentTimeMillis();
        int passCount = 0, failCount = 0;
        double recallSum = 0;
        List<RagEvalDetailVO> details = new ArrayList<>(cases.size());
        LocalDateTime now = LocalDateTime.now();

        for (RagEvalCase c : cases) {
            List<String> expected = parseSources(c.getExpectedSources());
            RetrievalResult rr = ragSearchService.searchTopK(c.getQuery(), c.getKbId(), true, topK);
            Set<String> topKeys = new HashSet<>();
            List<RagEvalHitVO> hitVOs = new ArrayList<>(rr.hits().size());
            for (RagHit h : rr.hits()) {
                topKeys.add(h.source() + ":" + h.sourceKey());
                hitVOs.add(new RagEvalHitVO(h.source(), h.title(), h.url(), h.score()));
            }
            long hit = expected.stream().filter(topKeys::contains).count();
            double recall = expected.isEmpty() ? 0 : (double) hit / expected.size();
            boolean pass = hit > 0;
            if (pass) {
                passCount++;
            } else {
                failCount++;
            }
            recallSum += recall;

            RagEvalResult r = new RagEvalResult();
            r.setRunId(runId);
            r.setCaseId(c.getId());
            r.setQuery(c.getQuery());
            r.setTopHits(toJson(hitVOs));
            r.setPass(pass ? 1 : 0);
            r.setRecall(recall);
            r.setCreateTime(now);
            resultMapper.insert(r);

            details.add(new RagEvalDetailVO(c.getId(), c.getQuery(), pass, recall, hitVOs));
        }
        double recallAvg = cases.isEmpty() ? 0 : recallSum / cases.size();
        log.info("[RAG评估] 跑分完成 runId={} total={} pass={} fail={} recallAvg={} 耗时={}ms",
                runId, cases.size(), passCount, failCount,
                String.format("%.4f", recallAvg), System.currentTimeMillis() - start);
        return new RagEvalRunVO(runId, cases.size(), passCount, failCount,
                Math.round(recallAvg * 10000) / 10000.0, details);
    }

    /** 某次跑分批次的全部结果（供前端按 runId 回看/对比）。 */
    public List<RagEvalResult> getRunResults(String runId) {
        return resultMapper.selectList(new LambdaQueryWrapper<RagEvalResult>()
                .eq(RagEvalResult::getRunId, runId)
                .orderByAsc(RagEvalResult::getId));
    }

    /** 最近的跑分批次（去重、按时间倒序），供前端下拉回看历史跑分。 */
    public List<String> listRecentRuns(int limit) {
        List<Object> objs = resultMapper.selectObjs(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<RagEvalResult>()
                .select("run_id")
                .groupBy("run_id")
                .orderByDesc("MAX(create_time)")
                .last("LIMIT " + Math.max(1, limit)));
        return objs.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
    }

    // ===== bad case 复盘（反馈分页，带消息快照）=====

    /**
     * 反馈分页（value 过滤，如 -1=坏样本），联查消息 content 与 RAG 引用，
     * 供复盘"哪些回答被认为没用 + 当时召回了什么资料"。
     */
    public Page<RagFeedbackVO> pageFeedback(Integer value, int pageNum, int pageSize) {
        LambdaQueryWrapper<RagFeedback> w = new LambdaQueryWrapper<>();
        if (value != null) {
            w.eq(RagFeedback::getValue, value);
        }
        w.orderByDesc(RagFeedback::getCreateTime);
        Page<RagFeedback> page = feedbackMapper.selectPage(new Page<>(pageNum, pageSize), w);

        Map<Long, AiChatMessage> msgMap = new HashMap<>();
        List<Long> msgIds = page.getRecords().stream().map(RagFeedback::getMessageId)
                .distinct().toList();
        if (!msgIds.isEmpty()) {
            messageMapper.selectBatchIds(msgIds)
                    .forEach(m -> msgMap.put(m.getId(), m));
        }
        Page<RagFeedbackVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<RagFeedbackVO> vos = new ArrayList<>(page.getRecords().size());
        for (RagFeedback fb : page.getRecords()) {
            AiChatMessage m = msgMap.get(fb.getMessageId());
            vos.add(new RagFeedbackVO(fb.getId(), fb.getMessageId(), fb.getValue(), fb.getComment(),
                    fb.getSubjectType(), fb.getSubjectId(),
                    m == null ? null : m.getContent(),
                    m == null ? List.of() : m.getRefs(),
                    fb.getCreateTime()));
        }
        voPage.setRecords(vos);
        return voPage;
    }

    // ===== 内部 =====

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BusinessException("序列化失败: " + e.getMessage());
        }
    }

    private List<String> parseSources(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            log.warn("[RAG评估] expected_sources 解析失败，按空处理: {}", json);
            return new ArrayList<>();
        }
    }
}