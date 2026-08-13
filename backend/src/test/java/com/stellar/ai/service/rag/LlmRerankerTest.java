package com.stellar.ai.service.rag;

import com.stellar.ai.service.AiChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LlmReranker} 单测：编号解析兼容（JSON 数组/逗号分隔/越界去重）、LLM 输出重排顺序、
 * 未选中按原序补足、失败/空输出保持原顺序降级。
 */
class LlmRerankerTest {

    private AiChatService aiChatService;
    private LlmReranker reranker;

    @BeforeEach
    void setUp() {
        aiChatService = mock(AiChatService.class);
        reranker = new LlmReranker(aiChatService);
    }

    private List<RagHit> hits(int n) {
        List<RagHit> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new RagHit("memos", String.valueOf(i), "内容" + i, null, null, 0.5));
        }
        return list;
    }

    @Test
    void parseIndices_兼容JSON数组与逗号分隔() {
        assertEquals(List.of(1, 3, 2), reranker.parseIndices("[1,3,2]", 5));
        assertEquals(List.of(3, 1), reranker.parseIndices("3,1", 5));
        assertEquals(List.of(2, 4), reranker.parseIndices("[2, 4]", 5));
        assertEquals(List.of(), reranker.parseIndices("无效", 5));
        // 越界/重复被过滤
        assertEquals(List.of(1, 2), reranker.parseIndices("[1,99,1,2]", 5));
    }

    @Test
    void rerank_按LLM输出顺序重排且未选中补足() {
        when(aiChatService.chatCompletionWithMessages(anyList(), any())).thenReturn("[3,1]");
        List<RagHit> r = reranker.rerank("q", hits(5), 3, null);
        assertEquals(List.of("3", "1", "2"), r.stream().map(RagHit::sourceKey).toList());
    }

    @Test
    void rerank_输出超topK_只取topK() {
        when(aiChatService.chatCompletionWithMessages(anyList(), any())).thenReturn("[1,2,3,4,5]");
        List<RagHit> r = reranker.rerank("q", hits(5), 2, null);
        assertEquals(2, r.size());
        assertEquals(List.of("1", "2"), r.stream().map(RagHit::sourceKey).toList());
    }

    @Test
    void rerank_LLM失败_保持原顺序截断() {
        when(aiChatService.chatCompletionWithMessages(anyList(), any()))
                .thenThrow(new RuntimeException("boom"));
        List<RagHit> r = reranker.rerank("q", hits(5), 3, null);
        assertEquals(List.of("1", "2", "3"), r.stream().map(RagHit::sourceKey).toList());
        assertTrue(r.get(0).score() >= r.get(1).score());
    }

    @Test
    void rerank_空候选_直接返回空() {
        assertTrue(reranker.rerank("q", List.of(), 3, null).isEmpty());
    }
}
