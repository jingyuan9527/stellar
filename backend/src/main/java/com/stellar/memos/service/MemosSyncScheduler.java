package com.stellar.memos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 备忘同步定时任务：每 4 小时整点拉取一次远端笔记（cron = 0 0 0/4 * * ?）。
 * <p>与手动「立即同步」共用 Redis 互斥锁防并发；未配置/异常均由 {@link MemosService#scheduledSyncPull()}
 * 落状态记录后吞掉，此处外层 try 仅为最后兜底防调度线程被打断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemosSyncScheduler {

    private final MemosService memosService;

    @Scheduled(cron = "0 0 0/4 * * ?")
    public void scheduledSync() {
        try {
            memosService.scheduledSyncPull();
        } catch (Exception e) {
            log.error("[备忘同步] 定时同步未捕获异常: {}", e.getMessage(), e);
        }
    }
}
