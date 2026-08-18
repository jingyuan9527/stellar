package com.stellar.ai.service.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Bm25Index} 纯逻辑单测：分词（CJK 单字 + ASCII 词）、BM25 命中与排序、边界（空库/无命中）。
 * 零依赖纯算法，不启动 Spring。
 */
class Bm25IndexTest {

    @Test
    void tokenize_汉字按单字切分() {
        Map<String, Integer> t = Bm25Index.tokenize("你好");
        assertEquals(1, t.get("你"));
        assertEquals(1, t.get("好"));
        assertEquals(2, t.size());
    }

    @Test
    void tokenize_ASCII词小写整体切分() {
        Map<String, Integer> t = Bm25Index.tokenize("Hello World_2 rclone");
        assertEquals(1, t.get("hello"));
        assertEquals(1, t.get("world_2"));
        assertEquals(1, t.get("rclone"));
    }

    @Test
    void tokenize_重复汉字计数() {
        Map<String, Integer> t = Bm25Index.tokenize("图床图床");
        assertEquals(2, t.get("图"));
        assertEquals(2, t.get("床"));
    }

    @Test
    void search_命中含关键词文档_按BM25分排序() {
        Bm25Index idx = Bm25Index.build(List.of(
                "部署图床方案 rclone",
                "购物清单",
                "图床备份图床工具 rclone"));
        List<Bm25Index.Score> hits = idx.search("图床", 5);
        // 文档 3 含"图"×2"床"×2（tf 更高），应排文档 1 之前；文档 2 无命中
        assertEquals(2, hits.size());
        assertEquals(2, hits.get(0).docIndex());
        assertEquals(0, hits.get(1).docIndex());
        assertTrue(hits.get(0).score() > hits.get(1).score());
    }

    @Test
    void search_ASCII词精确匹配() {
        Bm25Index idx = Bm25Index.build(List.of(
                "rclone 配置说明",
                "redis 缓存配置"));
        List<Bm25Index.Score> hits = idx.search("rclone", 5);
        assertEquals(1, hits.size());
        assertEquals(0, hits.get(0).docIndex());
    }

    @Test
    void search_topK截断() {
        Bm25Index idx = Bm25Index.build(List.of("甲图床", "乙图床", "丙图床"));
        assertEquals(2, idx.search("图床", 2).size());
    }

    @Test
    void search_空文档库_返回空() {
        assertTrue(Bm25Index.build(List.of()).search("图床", 5).isEmpty());
    }

    @Test
    void search_无命中_返回空() {
        Bm25Index idx = Bm25Index.build(List.of("购物清单", "买菜"));
        assertTrue(idx.search("数据库", 5).isEmpty());
    }

    @Test
    void search_全部文档为空文本_返回空不除零() {
        // avgDocLen=0 时长度归一化除零会产生 NaN 排序失真，应直接返回空
        Bm25Index idx = Bm25Index.build(List.of("", "", " "));
        assertTrue(idx.search("图床", 5).isEmpty());
    }
}
