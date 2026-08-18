package com.stellar.ai.service.rag;

import com.stellar.ai.entity.AiKnowledgeBase;
import com.stellar.ai.service.AiEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索管线（单轮 + 迭代 loop）：
 * <pre>
 * Router(路由:短查询直接单轮) → loop(≤ loop-max-rounds 轮, 每轮):
 *   1. 改写(QueryRewriter，带上一轮缺口 gap)
 *   2. 检索(知识库 KbRetriever + 备忘笔记 MemosRetriever) → RRF 融合 + 全局阈值
 *   3. 跨轮累计去重 → LLM 重排(Reranker)
 *   4. Judger 判定"资料够不够"：够 → 进入生成；不够且未达轮数上限 → 缺口喂回改写器再查
 * 达上限 → 取 top-k 注入生成
 * </pre>
 * 各阶段独立组件可替换（企业级为独立服务）；任一步失败都整体降级，不阻断聊天。
 * 仅登录用户触发（游客纯文本无 RAG）。另有 {@link #searchTopK} 纯检索路径供评估跑分（不开 LLM）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

    private final KbRetriever kbRetriever;
    private final MemosRetriever memosRetriever;
    private final QueryRewriter queryRewriter;
    private final Reranker reranker;
    private final Judger judger;
    private final QueryRouter router;
    private final AiEmbeddingService embeddingService;

    /** 最终注入条数（system 片段数），package-private 供单测设置 */
    @Value("${stellar.rag.top-k:4}")
    int topK;

    /** 重排前每源召回池大小（供重排挑选，> topK） */
    @Value("${stellar.rag.pool-size:12}")
    int poolSize;

    /** 融合后全局分数阈值：低于该 RRF 分的命中丢弃（默认 0=不过滤） */
    @Value("${stellar.rag.score-threshold:0.0}")
    double scoreThreshold;

    /** 是否启用 LLM 查询改写（关掉则拿原查询检索；loop 依赖改写，关掉自动退化为单轮） */
    @Value("${stellar.rag.rewrite-enabled:true}")
    boolean rewriteEnabled;

    /** 是否启用 LLM 重排（关掉则按融合分数顺序取 top-k） */
    @Value("${stellar.rag.rerank-enabled:true}")
    boolean rerankEnabled;

    /** 备忘笔记源总开关 */
    @Value("${stellar.rag.memos-enabled:true}")
    boolean memosEnabled;

    /** 迭代检索开关（期3 loop；判定器每轮一次 LLM） */
    @Value("${stellar.rag.loop-enabled:true}")
    boolean loopEnabled;

    /** loop 轮数上限 */
    @Value("${stellar.rag.loop-max-rounds:3}")
    int loopMaxRounds;

    /** 路由器开关（启发式：短查询判简单问题，不进 loop，省判定 LLM） */
    @Value("${stellar.rag.router-enabled:true}")
    boolean routerEnabled;

    /** 混合检索开关：BM25 关键词通道与稠密向量经 RRF 融合（P4，默认开，可用配置关闭） */
    @Value("${stellar.rag.hybrid-enabled:true}")
    boolean hybridEnabled;

    /** 相关性闸门开关：短查询无触发词判闲聊，跳过整个管线（P1 成本优化） */
    @Value("${stellar.rag.retrieval-gate-enabled:true}")
    boolean retrievalGateEnabled;

    /** RRF 常数（1/(k+rank)：k 越大，排位之间的分数差越小） */
    private static final int RRF_K = 60;

    /**
     * 跑一遍管线（单轮或迭代 loop，按 Router 判定 + loop 开关）。
     *
     * @param query    原始用户问题
     * @param kbId     会话关联知识库 id（可空，不关联则该源跳过）
     * @param useMemos 是否启用备忘笔记源（仅登录用户 true）
     * @param modelId  改写/重排/判定用的 TEXT 模型 id（可空用 TEXT 默认）
     */
    public RetrievalResult search(String query, Long kbId, boolean useMemos, Long modelId) {
        return search(query, kbId, useMemos, modelId, null);
    }

    /**
     * 完整管线但跳过相关性闸门（评估跑分用）：golden set 的用例都是显式检索意图，
     * 短 query（如"价格？"）不该被闲聊闸门跳过，否则 full 跑分结果失真。
     */
    public RetrievalResult searchFull(String query, Long kbId, boolean useMemos, Long modelId) {
        return search(query, kbId, useMemos, modelId, null, false);
    }

    /**
     * 跑一遍管线（单轮或迭代 loop，按 Router 判定 + loop 开关）。
     *
     * @param query    原始用户问题
     * @param kbId     会话关联知识库 id（可空，不关联则该源跳过）
     * @param useMemos 是否启用备忘笔记源（仅登录用户 true）
     * @param modelId  改写/重排/判定用的 TEXT 模型 id（可空用 TEXT 默认）
     * @param history  会话历史（可空；非空时改写器用它补全指代，多轮追问更准）
     */
    public RetrievalResult search(String query, Long kbId, boolean useMemos, Long modelId,
                                  List<Map<String, String>> history) {
        return search(query, kbId, useMemos, modelId, history, true);
    }

    private RetrievalResult search(String query, Long kbId, boolean useMemos, Long modelId,
                                   List<Map<String, String>> history, boolean applyGate) {
        boolean memosOn = useMemos && memosEnabled;
        // 相关性闸门：闲聊类短查询（无触发词）直接跳过整个管线，省 embedding + LLM 调用
        if (applyGate && retrievalGateEnabled && routerEnabled && !router.needsRetrieval(query)) {
            log.info("[RAG管线] 相关性闸门：闲聊类查询跳过检索 query={}", truncateLog(query));
            return new RetrievalResult(query, List.of(), Map.of("kb", 0, "memos", 0), 0);
        }
        // KB 管线级解析一次（存在性 + 绑定 embedding 模型）：后续 loop 各轮不再重复查询
        AiKnowledgeBase kb = resolveKb(kbId);
        // 无任何可用源：跳过整个管线（含改写/重排/判定），避免白烧 LLM
        if (kb == null && !memosOn) {
            return new RetrievalResult(query, List.of(), Map.of("kb", 0, "memos", 0), 1);
        }
        // loop 可用性：需开着 loop、路由器放行、且改写开启（无改写每轮查同一词没有意义）
        boolean loopUsable = loopEnabled && rewriteEnabled && (!routerEnabled || router.needsLoop(query));
        int maxRounds = loopUsable ? Math.max(1, loopMaxRounds) : 1;

        long start = System.currentTimeMillis();
        List<RagHit> accumulated = new ArrayList<>();
        String currentQuery = query;
        String gap = null;
        int kbCount = 0, memosCount = 0;
        for (int round = 1; round <= maxRounds; round++) {
            long roundStart = System.currentTimeMillis();
            // 1. 改写（loop 时带上一轮缺口；多轮对话带历史补全指代）
            currentQuery = safeRewrite(query, gap, modelId, history);
            // 2. 检索 + RRF 融合 + 阈值
            FusedResult fused = retrieveAndFuse(currentQuery, kb, memosOn);
            kbCount = fused.kbCount();
            memosCount = fused.memosCount();
            // 3. 跨轮累计去重（不同轮可能召回同一资料，只保留首个）
            List<RagHit> previous = accumulated;
            accumulated = mergeAccumulated(accumulated, fused.hits());
            // 3.5 跨轮重融合 + 截断重排池：把上一轮累计(不含本轮)与本轮结果再 RRF 一次，
            //     让后几轮新召回的命中凭融合分与旧轮竞争 poolSize 名额，而不是被 append 到队尾
            //     永远挤不进 LlmReranker 的候选上限。（注意用 previous 而非累计：累计已含本轮，
            //     与 fresh 重融合会双重计分，把新轮命中错误顶到最前）
            List<RagHit> pool = truncateTo(refuseAccumulated(previous, fused.hits()), poolSize);
            // 4. 重排（对重融合后的重排池）
            List<RagHit> reranked = rerankEnabled
                    ? reranker.rerank(currentQuery, pool, Math.max(topK, pool.size()), modelId)
                    : pool;
            boolean lastRound = round >= maxRounds;
            // 5. 判定"资料够不够"（仅非末轮；末轮直接生成）
            if (!lastRound) {
                List<RagHit> evidence = truncateTo(reranked, topK);
                Judgement judgement = judgeWithFallback(query, evidence, modelId);
                if (judgement.sufficient()) {
                    log.info("[RAG管线] loop 第{}轮判定足够，结束迭代 耗时={}ms", round,
                            System.currentTimeMillis() - roundStart);
                    return new RetrievalResult(currentQuery, evidence,
                            Map.of("kb", kbCount, "memos", memosCount), round);
                }
                gap = judgement.gap();
                log.info("[RAG管线] loop 第{}轮判定不足 gap={}，进入下一轮", round, truncateLog(gap));
                continue;
            }
            // 6. 末轮：取 top-k 注入
            List<RagHit> top = truncateTo(reranked, topK);
            log.info("[RAG管线] 检索完成 rounds={} top={} 总耗时={}ms 末轮耗时={}ms",
                    round, top.size(), System.currentTimeMillis() - start,
                    System.currentTimeMillis() - roundStart);
            return new RetrievalResult(currentQuery, top,
                    Map.of("kb", kbCount, "memos", memosCount), round);
        }
        // 理论不可达（末轮必 return）；防御兜底取累计 top-k
        List<RagHit> top = truncateTo(accumulated, topK);
        return new RetrievalResult(currentQuery, top, Map.of("kb", kbCount, "memos", memosCount), maxRounds);
    }

    /**
     * 纯检索路径（评估跑分开 LLM 步骤用）：原查询直检 → RRF 融合 → 阈值 → top-k。
     * 与线上检索路径共用 retrieveAndFuse，保证跑分与线上召回一致性。
     */
    public RetrievalResult searchTopK(String query, Long kbId, boolean useMemos, int k) {
        boolean memosOn = useMemos && memosEnabled;
        AiKnowledgeBase kb = resolveKb(kbId);
        if (kb == null && !memosOn) {
            return new RetrievalResult(query, List.of(), Map.of("kb", 0, "memos", 0), 1);
        }
        FusedResult fused = retrieveAndFuse(query, kb, memosOn);
        List<RagHit> top = truncateTo(fused.hits(), k > 0 ? k : topK);
        return new RetrievalResult(query, top, Map.of("kb", fused.kbCount(), "memos", fused.memosCount()), 1);
    }

    // ===== 内部 =====

    private String safeRewrite(String query, String gap, Long modelId, List<Map<String, String>> history) {
        if (!rewriteEnabled) {
            return query;
        }
        try {
            String rewritten = queryRewriter.rewrite(query, gap, modelId, history);
            return StringUtils.hasText(rewritten) ? rewritten : query;
        } catch (Exception e) {
            log.warn("[RAG管线] 查询改写异常，回退原查询: {}", e.getMessage());
            return query;
        }
    }

    /** 判定兜底：实现者按契约不抛异常，这里再兜一层防泄漏；null/异常一律保守放行。 */
    private Judgement judgeWithFallback(String query, List<RagHit> evidence, Long modelId) {
        try {
            Judgement j = judger.judge(query, evidence, modelId);
            return j == null ? Judgement.ok() : j;
        } catch (Exception e) {
            log.warn("[RAG管线] 充分性判定异常，保守放行: {}", e.getMessage());
            return Judgement.ok();
        }
    }

    /**
     * 双源检索 + RRF 融合 + 全局阈值（管线的检索核心，loop 与 searchTopK 共用）。
     * <p>query 向量共享：memos 恒用默认 EMBEDDING 模型；KB 未绑定专属模型（用默认）时与 memos
     * 共用同一个向量（每轮只向量化一次）；KB 绑定专属模型则单独向量化。
     * <p>hybrid 开启时每源两条通道（稠密向量 + BM25 关键词）共 4 张列表一起 RRF：
     * 专有名词/编号/标签由关键词通道精确召回，语义相近表达由向量通道召回，互不干扰。
     */
    private FusedResult retrieveAndFuse(String query, AiKnowledgeBase kb, boolean memosOn) {
        boolean kbNeedsDefaultVec = kb != null && kb.getEmbeddingModelId() == null;
        // 默认模型 query 向量：memos 需要，或 KB 用默认模型时需要（两者共享一次调用）
        float[] sharedVec = (memosOn || kbNeedsDefaultVec) ? safeEmbed(query, null) : null;
        // KB 向量：用默认模型则复用 sharedVec；绑定专属模型则独立向量化
        float[] kbVec = kb == null ? null
                : (kbNeedsDefaultVec ? sharedVec : safeEmbed(query, kb.getEmbeddingModelId()));

        List<RagHit> kbHits = kbRetriever.retrieve(kb, kbVec, poolSize);
        List<RagHit> memosHits = memosOn ? memosRetriever.retrieve(sharedVec, poolSize) : List.of();
        List<List<RagHit>> lists = new ArrayList<>();
        lists.add(kbHits);
        lists.add(memosHits);
        int kbKwCount = 0, memosKwCount = 0;
        if (hybridEnabled) {
            List<RagHit> kbKw = kbRetriever.retrieveKeyword(kb, query, poolSize);
            List<RagHit> memosKw = memosOn ? memosRetriever.retrieveKeyword(query, poolSize) : List.of();
            kbKwCount = kbKw.size();
            memosKwCount = memosKw.size();
            lists.add(kbKw);
            lists.add(memosKw);
        }
        List<RagHit> fused = thresholdFilter(rrfFuse(lists), scoreThreshold);
        return new FusedResult(fused, kbHits.size() + kbKwCount, memosHits.size() + memosKwCount);
    }

    /** 管线级解析 KB（存在性 + 绑定 embedding 模型），失败返回 null（该源降级为不可用）。 */
    private AiKnowledgeBase resolveKb(Long kbId) {
        return kbRetriever.getKbContext(kbId);
    }

    /** 查询向量化（失败返回 null，各通道降级为空，不阻断管线）。 */
    private float[] safeEmbed(String query, Long modelId) {
        try {
            return embeddingService.embed(query, modelId);
        } catch (Exception e) {
            log.warn("[RAG管线] 查询向量化失败 modelId={}: {}", modelId, e.getMessage());
            return null;
        }
    }

    /**
     * 跨轮重融合：把上一轮累计（不含本轮）与本轮结果再做一次 RRF。
     * 两轮都命中的资料融合分叠加（更相关），本轮新召回按本轮 rank 计分、
     * 旧轮独有命中保持上轮 rank——使后几轮新召回能凭融合分挤进 poolSize 重排池
     * （修复 append 队尾导致的 loop 失效）。首轮无历史直接返回本轮结果。
     */
    private List<RagHit> refuseAccumulated(List<RagHit> previous, List<RagHit> fresh) {
        if (previous.isEmpty()) {
            return fresh;
        }
        return rrfFuse(List.of(previous, fresh));
    }

    /** 跨轮累计去重：不同轮可能召回同一资料，保留先到者（source:sourceKey 唯一）。 */
    private List<RagHit> mergeAccumulated(List<RagHit> accumulated, List<RagHit> fresh) {
        if (accumulated.isEmpty()) {
            return fresh;
        }
        Map<String, RagHit> merged = new LinkedHashMap<>();
        for (RagHit h : accumulated) {
            merged.putIfAbsent(h.source() + ":" + h.sourceKey(), h);
        }
        for (RagHit h : fresh) {
            merged.putIfAbsent(h.source() + ":" + h.sourceKey(), h);
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * RRF（Reciprocal Rank Fusion）跨源融合：每个命中在各源列表的 rank（1 起）
     * → 融合分 Σ 1/(k+rank)。跨源排序归一化，多源召回互不干扰。
     */
    private List<RagHit> rrfFuse(List<List<RagHit>> lists) {
        Map<String, double[]> agg = new LinkedHashMap<>();
        for (List<RagHit> list : lists) {
            int rank = 1;
            for (RagHit h : list) {
                String key = h.source() + ":" + h.sourceKey();
                double[] entry = agg.computeIfAbsent(key, k -> new double[2]);
                entry[0] += 1.0 / (RRF_K + rank);
                entry[1] = h.score();
                rank++;
            }
        }
        List<Map.Entry<String, double[]>> entries = new ArrayList<>(agg.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0]));
        List<RagHit> fused = new ArrayList<>(entries.size());
        for (Map.Entry<String, double[]> e : entries) {
            RagHit h = findHit(lists, e.getKey());
            if (h != null) {
                fused.add(new RagHit(h.source(), h.sourceKey(), h.text(), h.title(), h.url(), e.getValue()[0]));
            }
        }
        return fused;
    }

    private RagHit findHit(List<List<RagHit>> lists, String key) {
        for (List<RagHit> list : lists) {
            for (RagHit h : list) {
                if ((h.source() + ":" + h.sourceKey()).equals(key)) {
                    return h;
                }
            }
        }
        return null;
    }

    private List<RagHit> thresholdFilter(List<RagHit> hits, double threshold) {
        if (threshold <= 0) {
            return hits;
        }
        return hits.stream().filter(h -> h.score() >= threshold).toList();
    }

    private List<RagHit> truncateTo(List<RagHit> hits, int k) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        return hits.size() > k ? hits.subList(0, k) : hits;
    }

    private String truncateLog(String s) {
        if (s == null) return null;
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    private record FusedResult(List<RagHit> hits, int kbCount, int memosCount) {
    }
}