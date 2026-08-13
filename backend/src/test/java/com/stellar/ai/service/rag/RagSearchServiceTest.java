package com.stellar.ai.service.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RagSearchService} 管线单测：RRF 跨源融合排序、阈值过滤、无源短路（省 LLM 调用）、
 * 重排关停时按融合分截断；loop 迭代（Router 放行→判定不足续查→轮数上限兜底）、
 * searchTopK 纯检索路径。纯 Mockito 隔离各组件，验证编排逻辑本身。
 */
class RagSearchServiceTest {

    private KbRetriever kbRetriever;
    private MemosRetriever memosRetriever;
    private QueryRewriter queryRewriter;
    private Reranker reranker;
    private Judger judger;
    private QueryRouter router;
    private RagSearchService service;

    @BeforeEach
    void setUp() {
        kbRetriever = mock(KbRetriever.class);
        memosRetriever = mock(MemosRetriever.class);
        queryRewriter = mock(QueryRewriter.class);
        reranker = mock(Reranker.class);
        judger = mock(Judger.class);
        router = mock(QueryRouter.class);
        service = new RagSearchService(kbRetriever, memosRetriever, queryRewriter, reranker, judger, router);
        service.topK = 3;
        service.poolSize = 5;
        service.scoreThreshold = 0.0;
        service.rewriteEnabled = true;
        service.rerankEnabled = false;
        service.memosEnabled = true;
        service.loopEnabled = true;
        service.loopMaxRounds = 3;
        service.routerEnabled = true;
        // 默认放行进 loop；单测逐个覆盖
        when(router.needsLoop(any())).thenReturn(true);
    }

    private RagHit hit(String source, String key, String text, double score) {
        return new RagHit(source, key, text, null, null, score);
    }

    @Test
    void search_仅备忘源_不改写不重排_RRF截断topK() {
        service.loopEnabled = false;
        when(queryRewriter.rewrite("q", null, null)).thenReturn("改:q");
        when(memosRetriever.retrieve("改:q", 5))
                .thenReturn(List.of(hit("memos", "1", "a", 0.9), hit("memos", "2", "b", 0.8),
                        hit("memos", "3", "c", 0.7), hit("memos", "4", "d", 0.6)));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals("改:q", r.rewrittenQuery());
        assertEquals(3, r.hits().size());
        // 未启用重排时按融合分降序截断 topK
        assertEquals(List.of("1", "2", "3"), r.hits().stream().map(RagHit::sourceKey).toList());
        verify(reranker, never()).rerank(any(), any(), anyInt(), any());
        assertEquals(Map.of("kb", 0, "memos", 4), r.sourceCounts());
    }

    @Test
    void search_RRF跨源融合_按rank排序不依赖单源cos分() {
        service.loopEnabled = false;
        when(queryRewriter.rewrite("q", null, null)).thenReturn("q");
        when(memosRetriever.retrieve("q", 5))
                .thenReturn(List.of(hit("memos", "mC", "nc", 0.5), hit("memos", "mD", "nd", 0.99)));
        when(kbRetriever.retrieve(7L, "q", 5))
                .thenReturn(List.of(hit("kb", "kbA", "na", 0.9), hit("kb", "kbB", "nb", 0.2)));

        RetrievalResult r = service.search("q", 7L, true, null);

        // rank1 的两个融合分相等，且高于任何 rank2；未重排截断 topK=3
        assertEquals(r.hits().get(0).score(), r.hits().get(1).score(), 1e-9);
        assertTrue(r.hits().get(0).score() > r.hits().get(2).score());
        assertEquals(3, r.hits().size());
    }

    @Test
    void search_阈值过滤_低分丢弃() {
        service.loopEnabled = false;
        service.scoreThreshold = 0.03; // RRF: rank1=1/61≈0.016，都低于阈值
        when(queryRewriter.rewrite("q", null, null)).thenReturn("q");
        when(memosRetriever.retrieve("q", 5)).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(kbRetriever.retrieve(7L, "q", 5)).thenReturn(List.of(hit("kb", "B", "nb", 0.8)));

        RetrievalResult r = service.search("q", 7L, true, null);

        assertTrue(r.hits().isEmpty(), "全部低于全局阈值应过滤为空");
    }

    @Test
    void search_无任何源_短路跳过改写检索() {
        service.memosEnabled = false;
        RetrievalResult r = service.search("q", null, true, null);
        assertEquals(0, r.hits().size());
        verify(queryRewriter, never()).rewrite(any(), any(), any());
        verify(memosRetriever, never()).retrieve(any(), anyInt());
        verify(kbRetriever, never()).retrieve(any(), any(), anyInt());
    }

    @Test
    void search_改写抛异常_降级原查询继续() {
        service.loopEnabled = false;
        when(queryRewriter.rewrite(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        when(memosRetriever.retrieve("q", 5)).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals("q", r.rewrittenQuery());
        assertEquals(1, r.hits().size());
    }

    // ===== loop 迭代 =====

    @Test
    void loop_第一轮判定足够_结束迭代() {
        when(queryRewriter.rewrite(any(), any(), any())).thenReturn("q");
        when(memosRetriever.retrieve("q", 5)).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(judger.judge(any(), any(), any())).thenReturn(Judgement.ok());

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(1, r.rounds());
        assertEquals(1, r.hits().size());
        verify(judger).judge(any(), any(), any());
        // 只跑了一轮检索
        verify(memosRetriever).retrieve(any(), anyInt());
    }

    @Test
    void loop_判定不足_带gap补查_第二轮足够() {
        // 第 1 轮判定不足(带缺口)，第 2 轮判定足够 → loop 在第二轮结束
        when(queryRewriter.rewrite("q", null, null)).thenReturn("q1");
        when(queryRewriter.rewrite("q", "缺A", null)).thenReturn("q2");
        when(memosRetriever.retrieve("q1", 5)).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(memosRetriever.retrieve("q2", 5)).thenReturn(List.of(hit("memos", "2", "b", 0.8)));
        when(judger.judge(any(), any(), any()))
                .thenReturn(new Judgement(false, "缺A"), Judgement.ok());

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(2, r.rounds());
        // 跨轮累计：两轮不同来源都进最终 evidence（topK=3 内）
        assertEquals(2, r.hits().size());
        // 缺口喂回了下一轮改写
        verify(queryRewriter).rewrite("q", "缺A", null);
    }

    @Test
    void loop_始终不足_到轮数上限兜底返回() {
        service.loopMaxRounds = 2;
        when(queryRewriter.rewrite(any(), any(), any())).thenReturn("q");
        when(memosRetriever.retrieve("q", 5)).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(judger.judge(any(), any(), any())).thenReturn(new Judgement(false, "一直缺"));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(2, r.rounds());
        assertEquals(1, r.hits().size());
        // 判定只跑了非末轮（1 次），末轮直接生成
        verify(judger, times(1)).judge(any(), any(), any());
    }

    @Test
    void loop_路由器判简单_单轮无判定() {
        when(router.needsLoop(any())).thenReturn(false);
        when(queryRewriter.rewrite("q", null, null)).thenReturn("q");
        when(memosRetriever.retrieve("q", 5)).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(1, r.rounds());
        verify(judger, never()).judge(any(), any(), any());
    }

    @Test
    void loop_判定抛异常_保守放行不卡死() {
        service.loopMaxRounds = 2;
        when(queryRewriter.rewrite(any(), any(), any())).thenReturn("q");
        when(memosRetriever.retrieve("q", 5)).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(judger.judge(any(), any(), any())).thenThrow(new RuntimeException("judger down"));

        RetrievalResult r = service.search("q", null, true, null);

        // 异常→ok()：第 1 轮判定放行直接结束
        assertEquals(1, r.rounds());
        assertEquals(1, r.hits().size());
    }

    @Test
    void loop_改写关停_退化为单轮() {
        service.rewriteEnabled = false;
        service.loopMaxRounds = 3;
        when(memosRetriever.retrieve("qq", 5)).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        RetrievalResult r = service.search("qq", null, true, null);

        assertEquals(1, r.rounds());
        verify(judger, never()).judge(any(), any(), any());
    }

    // ===== 纯检索路径（评估跑分用）=====

    @Test
    void searchTopK_直检不触发改写重排判定() {
        when(memosRetriever.retrieve("qq", 5)).thenReturn(List.of(
                hit("memos", "1", "a", 0.9), hit("memos", "2", "b", 0.7),
                hit("memos", "3", "c", 0.6), hit("memos", "4", "d", 0.5), hit("memos", "5", "e", 0.4)));

        RetrievalResult r = service.searchTopK("qq", null, true, 2);

        assertEquals(2, r.hits().size());
        verify(queryRewriter, never()).rewrite(any(), any(), any());
        verify(reranker, never()).rerank(any(), any(), anyInt(), any());
        verify(judger, never()).judge(any(), any(), any());
    }
}