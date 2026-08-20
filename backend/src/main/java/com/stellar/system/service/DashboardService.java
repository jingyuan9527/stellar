package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.ai.entity.AiTask;
import com.stellar.ai.mapper.AiTaskMapper;
import com.stellar.ai.service.SysAiUsageService;
import com.stellar.common.FileConstants;
import com.stellar.system.entity.SysFile;
import com.stellar.system.mapper.SysFileMapper;
import com.stellar.system.vo.DashboardStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SysAiUsageService aiUsageService;
    private final AiTaskMapper aiTaskMapper;
    private final SysFileMapper fileMapper;

    public DashboardStatsVO stats() {
        DashboardStatsVO vo = new DashboardStatsVO();
        try {
            vo.setAiUsage(aiUsageService.stats());
        } catch (Exception e) {
            log.warn("[仪表盘] AI 调用统计失败: {}", e.getMessage(), e);
        }
        try {
            vo.setTextGen(buildTaskStatByType("text", "success"));
        } catch (Exception e) {
            log.warn("[仪表盘] 文案记录统计失败: {}", e.getMessage(), e);
        }
        try {
            vo.setImageTask(buildTaskStatByType("image", "completed"));
        } catch (Exception e) {
            log.warn("[仪表盘] 图片任务统计失败: {}", e.getMessage(), e);
        }
        try {
            vo.setVideoTask(buildTaskStatByType("video", "completed"));
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

    private DashboardStatsVO.TaskStat buildTaskStatByType(String taskType, String successStatus) {
        List<AiTask> tasks = aiTaskMapper.selectList(
                new LambdaQueryWrapper<AiTask>()
                        .select(AiTask::getStatus, AiTask::getDurationMs, AiTask::getCreateTime)
                        .eq(AiTask::getTaskType, taskType));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();
        LocalDateTime prevWeekStart = LocalDate.now().minusDays(13).atStartOfDay();
        long total = 0, today = 0, success = 0;
        long durationSum = 0, durationCount = 0;
        long weekTotal = 0, prevWeekTotal = 0;
        for (AiTask t : tasks) {
            if (t.getStatus() == null || t.getStatus().equals("generating")) continue;
            total++;
            if (t.getCreateTime() != null) {
                if (!t.getCreateTime().isBefore(todayStart)) today++;
                if (!t.getCreateTime().isBefore(weekStart)) weekTotal++;
                else if (!t.getCreateTime().isBefore(prevWeekStart)) prevWeekTotal++;
            }
            if (successStatus.equals(t.getStatus())) {
                success++;
                if (t.getDurationMs() != null && t.getDurationMs() > 0) {
                    durationSum += t.getDurationMs();
                    durationCount++;
                }
            }
        }
        return buildTaskStat(total, today, success, durationSum, durationCount, weekTotal, prevWeekTotal);
    }

    private DashboardStatsVO.TaskStat buildTaskStat(long total, long today, long success,
                                                    long durationSum, long durationCount,
                                                    long weekTotal, long prevWeekTotal) {
        DashboardStatsVO.TaskStat s = new DashboardStatsVO.TaskStat();
        s.setTotal(total);
        s.setToday(today);
        s.setSuccessCount(success);
        s.setSuccessRate(total > 0 ? Math.round(((double) success / total) * 1000) / 10.0 : 0);
        s.setAvgDuration(durationCount > 0 ? durationSum / durationCount : 0);
        s.setWeekTotal(weekTotal);
        s.setPrevWeekTotal(prevWeekTotal);
        return s;
    }

    private DashboardStatsVO.FileStat buildFileStat() {
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

    private DashboardStatsVO.TtsStat buildTtsStat() {
        List<AiTask> records = aiTaskMapper.selectList(
                new LambdaQueryWrapper<AiTask>()
                        .select(AiTask::getFileSize, AiTask::getCreateTime)
                        .eq(AiTask::getTaskType, "tts"));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long total = records.size();
        long today = 0;
        long totalSize = 0;
        for (AiTask r : records) {
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
