package com.stellar.memos.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.memos.dto.MemosQueryDTO;
import com.stellar.memos.entity.MemosSyncLog;
import com.stellar.memos.mapper.MemosSyncLogMapper;
import com.stellar.memos.vo.MemosSyncLogVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Memos 同步状态记录存储：落库（顺带清理旧记录）、分页与最近一条查询。
 * <p>从 MemosService 抽出，同步编排只管流程，状态记录是独立关注点。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemosSyncLogStore {

    /** 同步状态记录保留天数：查询窗口与清理阈值，3 天前的记录由同步任务顺带删除 */
    static final int KEEP_DAYS = 3;

    private final MemosSyncLogMapper memosSyncLogMapper;

    /** 落一条同步状态记录，并顺带清理 {@link #KEEP_DAYS} 天前的旧记录（异步失败不阻断主流程）。 */
    public void persist(MemosSyncLog record, long startMillis) {
        try {
            record.setDurationMs(System.currentTimeMillis() - startMillis);
            record.setCreateTime(LocalDateTime.now());
            memosSyncLogMapper.insert(record);
            trimOld();
        } catch (Exception e) {
            log.error("[备忘同步] 同步状态落库失败 triggerType={}: {}", record.getTriggerType(), e.getMessage(), e);
        }
    }

    /** 清理保留期外的同步状态记录（随每次同步执行，异常仅告警）。 */
    private void trimOld() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(KEEP_DAYS);
            int removed = memosSyncLogMapper.delete(new LambdaQueryWrapper<MemosSyncLog>()
                    .lt(MemosSyncLog::getCreateTime, cutoff));
            if (removed > 0) {
                log.info("[备忘同步] 清理 {} 天前旧同步状态记录 {} 条", KEEP_DAYS, removed);
            }
        } catch (Exception e) {
            log.warn("[备忘同步] 清理旧同步状态记录失败: {}", e.getMessage());
        }
    }

    /** 同步状态记录分页（仅按时间倒序，查询窗口限最近保留期）。 */
    public Page<MemosSyncLogVO> page(MemosQueryDTO query) {
        Page<MemosSyncLog> page = memosSyncLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<MemosSyncLog>()
                        .ge(MemosSyncLog::getCreateTime, LocalDateTime.now().minusDays(KEEP_DAYS))
                        .orderByDesc(MemosSyncLog::getCreateTime)
                        .orderByDesc(MemosSyncLog::getId));
        Page<MemosSyncLogVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /** 最近一次同步状态（空记录返回 null）。 */
    public MemosSyncLogVO latest() {
        Page<MemosSyncLog> page = memosSyncLogMapper.selectPage(
                new Page<>(1, 1),
                new LambdaQueryWrapper<MemosSyncLog>()
                        .ge(MemosSyncLog::getCreateTime, LocalDateTime.now().minusDays(KEEP_DAYS))
                        .orderByDesc(MemosSyncLog::getCreateTime)
                        .orderByDesc(MemosSyncLog::getId));
        return page.getRecords().isEmpty() ? null : toVO(page.getRecords().get(0));
    }

    private MemosSyncLogVO toVO(MemosSyncLog l) {
        MemosSyncLogVO vo = new MemosSyncLogVO();
        vo.setId(l.getId());
        vo.setTriggerType(l.getTriggerType());
        vo.setStatus(l.getStatus());
        vo.setFetched(l.getFetched());
        vo.setCreated(l.getCreated());
        vo.setUpdated(l.getUpdated());
        vo.setMarkedDeleted(l.getMarkedDeleted());
        vo.setErrors(l.getErrors());
        vo.setConflicts(l.getConflicts());
        vo.setDurationMs(l.getDurationMs());
        vo.setErrorMessage(l.getErrorMessage());
        vo.setCreateTime(l.getCreateTime());
        return vo;
    }
}