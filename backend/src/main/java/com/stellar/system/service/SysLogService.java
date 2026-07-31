package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.system.dto.SysLogQueryDTO;
import com.stellar.system.entity.SysLog;
import com.stellar.system.mapper.SysLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysLogService {

    private final SysLogMapper sysLogMapper;

    /**
     * 异步保存操作日志，不阻塞业务请求。
     */
    @Async("logTaskExecutor")
    public void saveLog(SysLog sysLog) {
        try {
            sysLogMapper.insert(sysLog);
        } catch (Exception e) {
            log.error("异步保存操作日志失败: {}", e.getMessage(), e);
        }
    }

    public Page<SysLog> page(SysLogQueryDTO query) {
        Page<SysLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        return sysLogMapper.selectPage(page, buildWrapper(query));
    }

    public SysLog getById(Long id) {
        return sysLogMapper.selectById(id);
    }

    public List<SysLog> list(SysLogQueryDTO query) {
        return sysLogMapper.selectList(buildWrapper(query));
    }

    private LambdaQueryWrapper<SysLog> buildWrapper(SysLogQueryDTO query) {
        return new LambdaQueryWrapper<SysLog>()
                .like(StringUtils.hasText(query.getModule()), SysLog::getModule, query.getModule())
                .like(StringUtils.hasText(query.getOperator()), SysLog::getOperator, query.getOperator())
                .eq(query.getStatus() != null, SysLog::getStatus, query.getStatus())
                .ge(query.getStartTime() != null, SysLog::getCreateTime, query.getStartTime())
                .le(query.getEndTime() != null, SysLog::getCreateTime, query.getEndTime())
                .orderByDesc(SysLog::getCreateTime);
    }
}
