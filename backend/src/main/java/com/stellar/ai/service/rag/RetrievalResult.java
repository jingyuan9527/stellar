package com.stellar.ai.service.rag;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索管线结果：改写后的查询 + 最终注入的命中 + 各源召回数 + 迭代轮数（期3 loop 用）。
 */
public record RetrievalResult(
        String rewrittenQuery,
        List<RagHit> hits,
        Map<String, Integer> sourceCounts,
        int rounds
) {
}
