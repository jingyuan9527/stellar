package com.stellar.ai.service.rag;

import com.stellar.ai.entity.AiKnowledgeBase;
import com.stellar.ai.service.AiEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RagSearchService} 管线单测：RRF 跨源融合排序、阈值过滤、无源短路（省 LLM 调用）、
 * 重排关停时按融合分截断；loop 迭代（Router 放行→判定不足续查→轮数上限兜底）、
 * 跨轮重融合（后几轮新召回进入重排池）、相关性闸门（闲聊跳过）、混合检索（关键词通道）、
 * 共享 query 向量（KB 用默认模型与 memos 共享一次向量化）、searchTopK 纯检索路径。
 * 纯 Mockito 隔离各组件，验证编排逻辑本身。
 */
class RagSearchServiceTest {

    private KbRetriever kbRetriever;
    private MemosRetriever memosRetriever;
    private QueryRewriter queryRewriter;
    private Reranker reranker;
    private Judger judger;
    private QueryRouter router;
    private AiEmbeddingService embeddingService;
    private RagSearchService service;

    @BeforeEach
    void setUp() {
        kbRetriever = mock(KbRetriever.class);
        memosRetriever = mock(MemosRetriever.class);
        queryRewriter = mock(QueryRewriter.class);
        reranker = mock(Reranker.class);
        judger = mock(Judger.class);
        router = mock(QueryRouter.class);
        embeddingService = mock(AiEmbeddingService.class);
        service = new RagSearchService(kbRetriever, memosRetriever, queryRewriter, reranker, judger, router,
                embeddingService);
        service.topK = 3;
        service.poolSize = 5;
        service.scoreThreshold = 0.0;
        service.rewriteEnabled = true;
        service.rerankEnabled = false;
        service.memosEnabled = true;
        service.loopEnabled = true;
        service.loopMaxRounds = 3;
        service.routerEnabled = true;
        // 默认：闸门/混合检索关闭（默认行为不变），loop 放行
        service.retrievalGateEnabled = false;
        service.hybridEnabled = false;
        when(router.needsLoop(any())).thenReturn(true);
        when(embeddingService.embed(any(), any())).thenReturn(new float[]{1f, 0f, 0f});
    }

    private RagHit hit(String source, String key, String text, double score) {
        return new RagHit(source, key, text, null, null, score);
    }

    private AiKnowledgeBase kb(Long id, Long embeddingModelId) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setId(id);
        kb.setEmbeddingModelId(embeddingModelId);
        return kb;
    }

    @Test
    void search_仅备忘源_不改写不重排_RRF截断topK() {
        service.loopEnabled = false;
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("改:q");
        when(memosRetriever.retrieve(any(), eqK(5)))
                .thenReturn(List.of(hit("memos", "1", "a", 0.9), hit("memos", "2", "b", 0.8),
                        hit("memos", "3", "c", 0.7), hit("memos", "4", "d", 0.6)));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals("改:q", r.rewrittenQuery());
        assertEquals(3, r.hits().size());
        assertEquals(List.of("1", "2", "3"), r.hits().stream().map(RagHit::sourceKey).toList());
        verify(reranker, never()).rerank(any(), any(), anyInt(), any());
        assertEquals(Map.of("kb", 0, "memos", 4), r.sourceCounts());
    }

    @Test
    void search_RRF跨源融合_按rank排序不依赖单源cos分() {
        service.loopEnabled = false;
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(kbRetriever.getKbContext(7L)).thenReturn(kb(7L, null));
        when(memosRetriever.retrieve(any(), eqK(5)))
                .thenReturn(List.of(hit("memos", "mC", "nc", 0.5), hit("memos", "mD", "nd", 0.99)));
        when(kbRetriever.retrieve(any(), any(), eqK(5)))
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
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(kbRetriever.getKbContext(7L)).thenReturn(kb(7L, null));
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(kbRetriever.retrieve(any(), any(), eqK(5))).thenReturn(List.of(hit("kb", "B", "nb", 0.8)));

        RetrievalResult r = service.search("q", 7L, true, null);

        assertTrue(r.hits().isEmpty(), "全部低于全局阈值应过滤为空");
    }

    @Test
    void search_无任何源_短路跳过改写检索() {
        service.memosEnabled = false;
        RetrievalResult r = service.search("q", null, true, null);
        assertEquals(0, r.hits().size());
        verify(queryRewriter, never()).rewrite(any(), any(), any(), any());
        verify(memosRetriever, never()).retrieve(any(), anyInt());
        verify(kbRetriever, never()).retrieve(any(), any(), anyInt());
    }

    @Test
    void search_改写抛异常_降级原查询继续() {
        service.loopEnabled = false;
        when(queryRewriter.rewrite(any(), any(), any(), any())).thenThrow(new RuntimeException("boom"));
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals("q", r.rewrittenQuery());
        assertEquals(1, r.hits().size());
    }

    // ===== 共享 query 向量（任务1）=====

    @Test
    void search_kb用默认模型_与memos共享一次向量化() {
        service.loopEnabled = false;
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(kbRetriever.getKbContext(7L)).thenReturn(kb(7L, null));
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(kbRetriever.retrieve(any(), any(), eqK(5))).thenReturn(List.of(hit("kb", "b", "nb", 0.8)));

        service.search("q", 7L, true, null);

        // 默认模型 query 向量只 embed 一次（KB 与 memos 共享）
        verify(embeddingService, times(1)).embed(any(), isNull());
        verify(memosRetriever).retrieve(any(), eqK(5));
    }

    @Test
    void search_kb绑定专属模型_各自向量化() {
        service.loopEnabled = false;
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(kbRetriever.getKbContext(7L)).thenReturn(kb(7L, 99L));
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(kbRetriever.retrieve(any(), any(), eqK(5))).thenReturn(List.of(hit("kb", "b", "nb", 0.8)));

        service.search("q", 7L, true, null);

        // KB 绑定模型(99)与 memos 默认(null)各 embed 一次，共 2 次
        verify(embeddingService).embed("q", 99L);
        verify(embeddingService).embed("q", null);
        verify(embeddingService, times(2)).embed(any(), any());
    }

    @Test
    void search_仅KB无memos_只向量化KB所需() {
        service.loopEnabled = false;
        service.memosEnabled = false;
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(kbRetriever.getKbContext(7L)).thenReturn(kb(7L, null));
        when(kbRetriever.retrieve(any(), any(), eqK(5))).thenReturn(List.of(hit("kb", "b", "nb", 0.8)));

        service.search("q", 7L, false, null);

        verify(embeddingService, times(1)).embed(any(), isNull());
        verify(memosRetriever, never()).retrieve(any(), anyInt());
    }

    // ===== 相关性闸门（P1）=====

    @Test
    void search_闸门判闲聊_跳过整个管线() {
        service.retrievalGateEnabled = true;
        when(router.needsRetrieval("hi")).thenReturn(false);

        RetrievalResult r = service.search("hi", 7L, true, null);

        assertEquals(0, r.hits().size());
        assertEquals(0, r.rounds());
        verify(queryRewriter, never()).rewrite(any(), any(), any(), any());
        verify(memosRetriever, never()).retrieve(any(), anyInt());
        verify(kbRetriever, never()).getKbContext(any());
    }

    @Test
    void search_闸门放行_正常检索() {
        service.retrievalGateEnabled = true;
        service.loopEnabled = false;
        when(router.needsRetrieval("q")).thenReturn(true);
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(1, r.hits().size());
    }

    @Test
    void searchFull_绕过闸门_评估短用例不被闲聊跳过() {
        service.retrievalGateEnabled = true;
        service.loopEnabled = false;
        when(router.needsRetrieval("q")).thenReturn(false);
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        RetrievalResult r = service.searchFull("q", null, true, null);

        assertEquals(1, r.hits().size());
        assertEquals(1, r.rounds());
        verify(memosRetriever).retrieve(any(), eqK(5));
    }

    // ===== 混合检索（P4：BM25 关键词通道）=====

    @Test
    void search_hybrid开启_关键词通道参与RRF融合() {
        service.hybridEnabled = true;
        service.loopEnabled = false;
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(kbRetriever.getKbContext(7L)).thenReturn(kb(7L, null));
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        // 关键词通道命中向量通道没召回的精确词
        when(memosRetriever.retrieveKeyword("q", 5)).thenReturn(List.of(hit("memos", "2", "b", 12.0)));
        when(kbRetriever.retrieveKeyword(any(), eq("q"), eqK(5))).thenReturn(List.of(hit("kb", "9", "c", 8.0)));

        RetrievalResult r = service.search("q", 7L, true, null);

        // 三路命中都进融合（rank1 各 1/61）
        assertEquals(3, r.hits().size());
        assertTrue(r.hits().stream().anyMatch(h -> "2".equals(h.sourceKey())), "关键词通道命中应进入融合结果");
        assertEquals(Map.of("kb", 1, "memos", 2), r.sourceCounts());
        verify(memosRetriever).retrieveKeyword("q", 5);
        verify(kbRetriever).retrieveKeyword(any(), eq("q"), eqK(5));
    }

    @Test
    void search_hybrid关闭_不调关键词通道() {
        service.hybridEnabled = false;
        service.loopEnabled = false;
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(kbRetriever.getKbContext(7L)).thenReturn(kb(7L, null));
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        service.search("q", 7L, true, null);

        verify(memosRetriever, never()).retrieveKeyword(any(), anyInt());
        verify(kbRetriever, never()).retrieveKeyword(any(), any(), anyInt());
    }

    // ===== loop 迭代 =====

    @Test
    void loop_第一轮判定足够_结束迭代() {
        when(queryRewriter.rewrite(any(), any(), any(), any())).thenReturn("q");
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(judger.judge(any(), any(), any())).thenReturn(Judgement.ok());

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(1, r.rounds());
        assertEquals(1, r.hits().size());
        verify(judger).judge(any(), any(), any());
        verify(memosRetriever).retrieve(any(), anyInt());
    }

    @Test
    void loop_判定不足_带gap补查_第二轮足够() {
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q1");
        when(queryRewriter.rewrite("q", "缺A", null, null)).thenReturn("q2");
        when(memosRetriever.retrieve(any(), eqK(5)))
                .thenReturn(List.of(hit("memos", "1", "a", 0.9)))
                .thenReturn(List.of(hit("memos", "2", "b", 0.8)));
        when(judger.judge(any(), any(), any()))
                .thenReturn(new Judgement(false, "缺A"), Judgement.ok());

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(2, r.rounds());
        assertEquals(2, r.hits().size());
        verify(queryRewriter).rewrite("q", "缺A", null, null);
    }

    @Test
    void loop_第二轮新召回_经跨轮重融合进入判定与最终结果() {
        service.poolSize = 5;
        service.topK = 3;
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q1");
        when(queryRewriter.rewrite("q", "缺B", null, null)).thenReturn("q2");
        when(memosRetriever.retrieve(any(), eqK(5)))
                .thenReturn(List.of(
                        hit("memos", "r1a", "a", 0.9), hit("memos", "r1b", "b", 0.8),
                        hit("memos", "r1c", "c", 0.7), hit("memos", "r1d", "d", 0.6)))
                .thenReturn(List.of(
                        hit("memos", "r2a", "e", 0.9), hit("memos", "r2b", "f", 0.8),
                        hit("memos", "r2c", "g", 0.7), hit("memos", "r2d", "h", 0.6)));
        when(judger.judge(any(), any(), any()))
                .thenReturn(new Judgement(false, "缺B"), Judgement.ok());

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(2, r.rounds());
        assertEquals(List.of("r1a", "r2a", "r1b"), r.hits().stream().map(RagHit::sourceKey).toList());
    }

    @Test
    void loop_始终不足_到轮数上限兜底返回() {
        service.loopMaxRounds = 2;
        when(queryRewriter.rewrite(any(), any(), any(), any())).thenReturn("q");
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(judger.judge(any(), any(), any())).thenReturn(new Judgement(false, "一直缺"));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(2, r.rounds());
        assertEquals(1, r.hits().size());
        verify(judger, times(1)).judge(any(), any(), any());
    }

    @Test
    void loop_路由器判简单_单轮无判定() {
        when(router.needsLoop(any())).thenReturn(false);
        when(queryRewriter.rewrite("q", null, null, null)).thenReturn("q");
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(1, r.rounds());
        verify(judger, never()).judge(any(), any(), any());
    }

    @Test
    void loop_判定抛异常_保守放行不卡死() {
        service.loopMaxRounds = 2;
        when(queryRewriter.rewrite(any(), any(), any(), any())).thenReturn("q");
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));
        when(judger.judge(any(), any(), any())).thenThrow(new RuntimeException("judger down"));

        RetrievalResult r = service.search("q", null, true, null);

        assertEquals(1, r.rounds());
        assertEquals(1, r.hits().size());
    }

    @Test
    void loop_改写关停_退化为单轮() {
        service.rewriteEnabled = false;
        service.loopMaxRounds = 3;
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        RetrievalResult r = service.search("qq", null, true, null);

        assertEquals(1, r.rounds());
        verify(judger, never()).judge(any(), any(), any());
    }

    @Test
    void search_历史透传_改写器收到会话上下文() {
        service.loopEnabled = false;
        List<Map<String, String>> history = List.of(Map.of("role", "user", "content", "上次说的图床方案"));
        when(queryRewriter.rewrite("q", null, null, history)).thenReturn("改:q");
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(hit("memos", "1", "a", 0.9)));

        RetrievalResult r = service.search("q", null, true, null, history);

        assertEquals("改:q", r.rewrittenQuery());
        verify(queryRewriter).rewrite("q", null, null, history);
    }

    // ===== 纯检索路径（评估跑分用）=====

    @Test
    void searchTopK_直检不触发改写重排判定() {
        when(memosRetriever.retrieve(any(), eqK(5))).thenReturn(List.of(
                hit("memos", "1", "a", 0.9), hit("memos", "2", "b", 0.7),
                hit("memos", "3", "c", 0.6), hit("memos", "4", "d", 0.5), hit("memos", "5", "e", 0.4)));

        RetrievalResult r = service.searchTopK("qq", null, true, 2);

        assertEquals(2, r.hits().size());
        verify(queryRewriter, never()).rewrite(any(), any(), any(), any());
        verify(reranker, never()).rerank(any(), any(), anyInt(), any());
        verify(judger, never()).judge(any(), any(), any());
    }

    // Mockito 对 varargs int 参数的精确匹配（retrieve(..., int) 的第二参）
    private static int eqK(int k) {
        return org.mockito.ArgumentMatchers.eq(k);
    }
}
