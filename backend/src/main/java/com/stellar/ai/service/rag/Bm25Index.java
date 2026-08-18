package com.stellar.ai.service.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 BM25 倒排索引（零外部依赖）：关键词/精确词召回，与稠密向量经 RRF 融合组成混合检索。
 * <p>分词策略：CJK 汉字按单字切分 + ASCII 词（字母/数字/下划线）整体切分（小写），
 * 无需 jieba 等分词依赖即可覆盖中文笔记里专有名词/编号/标签/URL 片段的精确匹配。
 * 中文单字 BM25 会牺牲部分语义（单字 idf 低），但作为与向量互补的精确召回通道足够。
 * <p>用法：{@link #build(List)} 由文档文本列表构建，{@link #search} 返回按 BM25 分降序的文档下标
 * （下标与构建时传入的文本列表一一对应）。
 */
public final class Bm25Index {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /** CJK 单字 + ASCII 词（含下划线，便于匹配标签/文件名）。 */
    private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}]|[a-z0-9_]+");

    private final int docCount;
    private final double avgDocLen;
    private final int[] docLen;
    /** token -> 包含它的文档数（IDF 用） */
    private final Map<String, Integer> docFreq;
    /** token -> [(docIndex, tf), ...] 倒排表 */
    private final Map<String, List<int[]>> postings;

    private Bm25Index(int docCount, double avgDocLen, int[] docLen,
                      Map<String, Integer> docFreq, Map<String, List<int[]>> postings) {
        this.docCount = docCount;
        this.avgDocLen = avgDocLen;
        this.docLen = docLen;
        this.docFreq = docFreq;
        this.postings = postings;
    }

    /** 由文档文本列表构建倒排索引。docIndex 与列表下标一致。 */
    public static Bm25Index build(List<String> texts) {
        int n = texts.size();
        int[] docLen = new int[n];
        Map<String, Integer> df = new HashMap<>();
        Map<String, List<int[]>> postings = new HashMap<>();
        long totalLen = 0;
        for (int i = 0; i < n; i++) {
            Map<String, Integer> tf = tokenize(texts.get(i));
            int len = 0;
            for (int c : tf.values()) {
                len += c;
            }
            docLen[i] = len;
            totalLen += len;
            for (Map.Entry<String, Integer> e : tf.entrySet()) {
                postings.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new int[]{i, e.getValue()});
                df.merge(e.getKey(), 1, Integer::sum);
            }
        }
        double avg = n == 0 ? 0 : (double) totalLen / n;
        return new Bm25Index(n, avg, docLen, df, postings);
    }

    /**
     * 对查询返回 top-k 文档（BM25 分降序）。查询词去重后逐词累计各文档得分。
     * 全部文档为空文本（avgDocLen=0）时无法计算归一化长度，返回空，避免除零 NaN。
     * 无命中或不可检索时返回空列表。
     */
    public List<Score> search(String query, int k) {
        List<Score> result = new ArrayList<>();
        if (k <= 0 || docCount == 0 || avgDocLen <= 0 || query == null || query.isBlank()) {
            return result;
        }
        Map<String, Integer> qt = tokenize(query);
        if (qt.isEmpty()) {
            return result;
        }
        Map<Integer, Double> acc = new HashMap<>();
        for (String term : qt.keySet()) {
            Integer df = docFreq.get(term);
            if (df == null || df == 0) {
                continue;
            }
            double idf = Math.log(1 + (docCount - df + 0.5) / (df + 0.5));
            for (int[] post : postings.get(term)) {
                int doc = post[0];
                double tf = post[1];
                double denom = tf + K1 * (1 - B + B * docLen[doc] / avgDocLen);
                acc.merge(doc, idf * tf * (K1 + 1) / denom, Double::sum);
            }
        }
        if (acc.isEmpty()) {
            return result;
        }
        List<Map.Entry<Integer, Double>> entries = new ArrayList<>(acc.entrySet());
        entries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        int limit = Math.min(k, entries.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<Integer, Double> e = entries.get(i);
            result.add(new Score(e.getKey(), e.getValue()));
        }
        return result;
    }

    /** 分词：CJK 单字 + ASCII 词（小写）。返回词→计数。 */
    static Map<String, Integer> tokenize(String text) {
        Map<String, Integer> tokens = new HashMap<>();
        if (text == null) {
            return tokens;
        }
        Matcher m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            tokens.merge(m.group(), 1, Integer::sum);
        }
        return tokens;
    }

    /** 命中条目：文档下标 + BM25 分。 */
    public record Score(int docIndex, double score) {
    }
}
