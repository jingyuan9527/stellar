package com.stellar.ai.service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 启发式路由器（默认实现）：
 * <ul>
 *   <li>{@link #needsLoop}：短查询（&lt;= simpleLength 字）视为简单问题，不进 loop；长/含实体查询才迭代补查。</li>
 *   <li>{@link #needsRetrieval}（相关性闸门）：短查询且不含检索触发词（笔记/资料/查/多少…）判为闲聊，
 *       跳过整个 RAG 管线，省掉每条消息白烧的 embedding + LLM 调用。</li>
 * </ul>
 * 启发式只是粗略代理（企业用 LLM 路由更准但多一次调用），此实现保证零成本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeuristicRouter implements QueryRouter {

    /** 查询长度阈值：<= 该长度的查询视为简单问题（不进 loop）；也是闸门的"短查询"判定线 */
    @Value("${stellar.rag.router-simple-length:12}")
    private int simpleLength;

    /** 检索触发词：短查询命中任一触发词仍判定"需要检索"（如"多少钱""找一下笔记"） */
    @Value("${stellar.rag.retrieval-trigger-words:}")
    private List<String> triggerWords = List.of();

    @Override
    public boolean needsLoop(String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        boolean loop = query.trim().length() > simpleLength;
        log.debug("[RAG管线] 路由判定 len={} simpleLength={} -> needsLoop={}", query.trim().length(), simpleLength, loop);
        return loop;
    }

    @Override
    public boolean needsRetrieval(String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        String q = query.trim();
        if (q.length() > simpleLength) {
            return true;
        }
        for (String w : triggerWords) {
            if (q.contains(w)) {
                return true;
            }
        }
        log.info("[RAG管线] 相关性闸门：短查询且无触发词，判定闲聊跳过检索 query={}", q);
        return false;
    }
}