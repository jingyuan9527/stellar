package com.stellar.ai.service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 启发式路由器（默认实现）：短查询（<= simpleLength 字）视为简单问题，单轮直接生成。
 * <p>启发式只是粗略代理（企业用 LLM 路由更准但多一次调用），此实现保证零成本；
 * 长问题/含具体实体名的查询才进 loop。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeuristicRouter implements QueryRouter {

    /** 查询长度阈值：<= 该长度的查询视为简单问题（不进 loop） */
    @Value("${stellar.rag.router-simple-length:12}")
    private int simpleLength;

    @Override
    public boolean needsLoop(String query) {
        if (!StringUtils.hasText(query)) {
            return false;
        }
        boolean loop = query.trim().length() > simpleLength;
        log.debug("[RAG管线] 路由判定 len={} simpleLength={} -> needsLoop={}", query.trim().length(), simpleLength, loop);
        return loop;
    }
}