package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.common.FileConstants;
import com.stellar.entity.SysAiChatRecord;
import com.stellar.entity.SysAiImageTask;
import com.stellar.entity.SysAiVideoTask;
import com.stellar.entity.SysFile;
import com.stellar.entity.TtsRecord;
import com.stellar.mapper.SysAiChatRecordMapper;
import com.stellar.mapper.SysAiImageTaskMapper;
import com.stellar.mapper.SysAiVideoTaskMapper;
import com.stellar.mapper.SysFileMapper;
import com.stellar.mapper.TtsRecordMapper;
import com.stellar.vo.DashboardStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘聚合统计服务：调用各模块 Mapper 汇总零成本可统计指标。
 * <p>所有查询只取统计所需字段（避免拉取 prompt/result/data 等大字段），单服务内 stream 聚合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SysAiUsageService aiUsageService;
    private final SysAiChatRecordMapper chatRecordMapper;
    private final SysAiImageTaskMapper imageTaskMapper;
    private final SysAiVideoTaskMapper videoTaskMapper;
    private final SysFileMapper fileMapper;
    private final TtsRecordMapper ttsRecordMapper;

    /**
     * 聚合仪表盘全部统计：AI token 调用 + 文案/图片/视频任务质量 + 文件 + TTS。
     * <p>每个子项独立 try/catch，单点失败不阻塞其他维度展示。
     */
    public DashboardStatsVO stats() {
        DashboardStatsVO vo = new DashboardStatsVO();
        try {
            vo.setAiUsage(aiUsageService.stats());
        } catch (Exception e) {
            log.warn("[仪表盘] AI 调用统计失败: {}", e.getMessage(), e);
        }
        try {
            vo.setTextGen(buildTextGenStat());
        } catch (Exception e) {
            log.warn("[仪表盘] 文案记录统计失败: {}", e.getMessage(), e);
        }
        try {
            vo.setImageTask(buildImageTaskStat());
        } catch (Exception e) {
            log.warn("[仪表盘] 图片任务统计失败: {}", e.getMessage(), e);
        }
        try {
            vo.setVideoTask(buildVideoTaskStat());
        } catch (Exception e) {
            log.warn("[仪表盘] 视频任务统计失败: {}", e.getMessage(), e);
        }
        try {
            vo.setFile(buildFileStat());
        } catch (Exception e) {
            log.warn("[仪表盘] 文件统计失败: {}", e.getMessage(), e);
        }
        try {
            vo.setTts(buildTtsStat());
        } catch (Exception e) {
            log.warn("[仪表盘] TTS 统计失败: {}", e.getMessage(), e);
        }
        return vo;
    }

    /**
     * 文案记录统计：成功率(success/total) + 平均耗时(duration_ms, 仅 success)。
     * <p>耗时单位毫秒（SysAiChatRecord.duration_ms 字段精确记录）。
     */
    private DashboardStatsVO.TaskStat buildTextGenStat() {
        List<SysAiChatRecord> records = chatRecordMapper.selectList(
                new LambdaQueryWrapper<SysAiChatRecord>()
                        .select(SysAiChatRecord::getStatus, SysAiChatRecord::getDurationMs,
                                SysAiChatRecord::getCreateTime));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long total = 0, today = 0, success = 0;
        long durationSum = 0, durationCount = 0;
        for (SysAiChatRecord r : records) {
            // success/failed 为终态，generating 不计入分母
            if (r.getStatus() == null || r.getStatus().equals("generating")) continue;
            total++;
            if (r.getCreateTime() != null && !r.getCreateTime().isBefore(todayStart)) today++;
            if ("success".equals(r.getStatus())) {
                success++;
                if (r.getDurationMs() != null && r.getDurationMs() > 0) {
                    durationSum += r.getDurationMs();
                    durationCount++;
                }
            }
        }
        return buildTaskStat(total, today, success, durationSum, durationCount);
    }

    /**
     * 图片任务统计：成功率(completed/终态) + 平均耗时(updateTime-createTime 秒级估算，仅 completed)。
     */
    private DashboardStatsVO.TaskStat buildImageTaskStat() {
        List<SysAiImageTask> tasks = imageTaskMapper.selectList(
                new LambdaQueryWrapper<SysAiImageTask>()
                        .select(SysAiImageTask::getStatus, SysAiImageTask::getCreateTime,
                                SysAiImageTask::getUpdateTime));
        return aggregateTaskStat(tasks, SysAiImageTask::getStatus,
                SysAiImageTask::getCreateTime, SysAiImageTask::getUpdateTime);
    }

    /**
     * 视频任务统计：同图片，仅 completed 计入耗时，秒级估算。
     */
    private DashboardStatsVO.TaskStat buildVideoTaskStat() {
        List<SysAiVideoTask> tasks = videoTaskMapper.selectList(
                new LambdaQueryWrapper<SysAiVideoTask>()
                        .select(SysAiVideoTask::getStatus, SysAiVideoTask::getCreateTime,
                                SysAiVideoTask::getUpdateTime));
        return aggregateTaskStat(tasks, SysAiVideoTask::getStatus,
                SysAiVideoTask::getCreateTime, SysAiVideoTask::getUpdateTime);
    }

    /**
     * 图片/视频任务通用聚合：终态任务计入分母，completed 计入耗时（秒级）。
     * <p>耗时采用 updateTime-createTime 估算，秒级精度（表内无毫秒字段，足够看趋势）。
     */
    private <T> DashboardStatsVO.TaskStat aggregateTaskStat(
            List<T> tasks,
            java.util.function.Function<T, String> statusGetter,
            java.util.function.Function<T, LocalDateTime> createTimeGetter,
            java.util.function.Function<T, LocalDateTime> updateTimeGetter) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long total = 0, today = 0, success = 0;
        long durationSum = 0, durationCount = 0;
        for (T t : tasks) {
            String status = statusGetter.apply(t);
            if (status == null || status.equals("generating")) continue;
            total++;
            LocalDateTime ct = createTimeGetter.apply(t);
            if (ct != null && !ct.isBefore(todayStart)) today++;
            if ("completed".equals(status)) {
                success++;
                LocalDateTime ut = updateTimeGetter.apply(t);
                if (ct != null && ut != null && ut.isAfter(ct)) {
                    durationSum += Duration.between(ct, ut).getSeconds();
                    durationCount++;
                }
            }
        }
        return buildTaskStat(total, today, success, durationSum, durationCount);
    }

    private DashboardStatsVO.TaskStat buildTaskStat(long total, long today, long success,
                                                    long durationSum, long durationCount) {
        DashboardStatsVO.TaskStat s = new DashboardStatsVO.TaskStat();
        s.setTotal(total);
        s.setToday(today);
        s.setSuccessCount(success);
        // 成功率保留 1 位小数（0-100）
        s.setSuccessRate(total > 0 ? Math.round(((double) success / total) * 1000) / 10.0 : 0);
        s.setAvgDuration(durationCount > 0 ? durationSum / durationCount : 0);
        return s;
    }

    /**
     * 文件统计：总数、今日上传、总占用字节、按类型（image/audio/other）分组。
     */
    private DashboardStatsVO.FileStat buildFileStat() {
        // data 字段 @TableField(select=false) 默认不加载；显式 select 进一步裁剪
        List<SysFile> files = fileMapper.selectList(
                new LambdaQueryWrapper<SysFile>()
                        .select(SysFile::getExt, SysFile::getSize, SysFile::getCreateTime));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long total = files.size();
        long todayUpload = 0;
        long totalSize = 0;
        Map<String, long[]> typeMap = new LinkedHashMap<>();
        long[] imageArr = typeMap.computeIfAbsent("image", k -> new long[]{0, 0});
        long[] audioArr = typeMap.computeIfAbsent("audio", k -> new long[]{0, 0});
        long[] otherArr = typeMap.computeIfAbsent("other", k -> new long[]{0, 0});
        for (SysFile f : files) {
            long size = f.getSize() == null ? 0 : f.getSize();
            totalSize += size;
            if (f.getCreateTime() != null && !f.getCreateTime().isBefore(todayStart)) todayUpload++;
            String ext = f.getExt() == null ? "" : f.getExt().toLowerCase();
            long[] arr;
            if (FileConstants.IMAGE_EXT.contains(ext)) {
                arr = imageArr;
            } else if (FileConstants.AUDIO_EXT.contains(ext)) {
                arr = audioArr;
            } else {
                arr = otherArr;
            }
            arr[0]++;
            arr[1] += size;
        }
        DashboardStatsVO.FileStat stat = new DashboardStatsVO.FileStat();
        stat.setTotal(total);
        stat.setTodayUpload(todayUpload);
        stat.setTotalSize(totalSize);
        List<DashboardStatsVO.FileTypeStat> byType = new ArrayList<>();
        typeMap.forEach((type, arr) -> {
            if (arr[0] == 0) return;
            DashboardStatsVO.FileTypeStat t = new DashboardStatsVO.FileTypeStat();
            t.setType(type);
            t.setCount(arr[0]);
            t.setSize(arr[1]);
            byType.add(t);
        });
        stat.setByType(byType);
        return stat;
    }

    /**
     * TTS 合成统计：总数、今日合成、总音频字节。
     */
    private DashboardStatsVO.TtsStat buildTtsStat() {
        // 逻辑删除字段由 MP 自动过滤；只取统计字段，避免拉 audio_data 大字段
        List<TtsRecord> records = ttsRecordMapper.selectList(
                new LambdaQueryWrapper<TtsRecord>()
                        .select(TtsRecord::getFileSize, TtsRecord::getCreateTime));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long total = records.size();
        long today = 0;
        long totalSize = 0;
        for (TtsRecord r : records) {
            long size = r.getFileSize() == null ? 0 : r.getFileSize();
            totalSize += size;
            if (r.getCreateTime() != null && !r.getCreateTime().isBefore(todayStart)) today++;
        }
        DashboardStatsVO.TtsStat stat = new DashboardStatsVO.TtsStat();
        stat.setTotal(total);
        stat.setToday(today);
        stat.setTotalSize(totalSize);
        return stat;
    }
}
