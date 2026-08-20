package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stellar.system.dto.SysLogQueryDTO;
import com.stellar.system.entity.SysLog;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysLogMapper;
import com.stellar.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysLogService {

    private final SysLogMapper sysLogMapper;

    /** 操作人用户名短缓存（userId→username，5min TTL）：异步线程解析高频命中，避免每条日志重复查库。 */
    private static final Cache<Long, String> OPERATOR_CACHE = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(2000)
            .build();

    /** 供 saveLog 异步线程按 userId 解析用户名；单测直接 new 时注入 null 则跳过解析。 */
    @Autowired(required = false)
    private SysUserMapper sysUserMapper;

    /**
     * 异步保存操作日志，不阻塞业务请求。
     * 切面/外部调用日志在请求线程只填 operatorUserId（不查库），由本方法异步线程内解析用户名填充 operator
     * （消除请求线程上的同步 SELECT）。
     */
    @Async("logTaskExecutor")
    public void saveLog(SysLog sysLog) {
        try {
            resolveOperator(sysLog);
            sysLogMapper.insert(sysLog);
        } catch (Exception e) {
            log.error("异步保存操作日志失败: {}", e.getMessage(), e);
        }
    }

    private void resolveOperator(SysLog sysLog) {
        if (sysLog.getOperator() != null) {
            return; // LOGIN 等已直接填好 username，无需解析
        }
        Long userId = sysLog.getOperatorUserId();
        if (userId == null || sysUserMapper == null) {
            return;
        }
        try {
            String username = OPERATOR_CACHE.get(userId, id -> {
                SysUser u = sysUserMapper.selectById(id);
                return u != null ? u.getUsername() : null;
            });
            sysLog.setOperator(username != null ? username : "user:" + userId);
        } catch (Exception e) {
            log.warn("解析操作人用户名失败 userId={}: {}", userId, e.getMessage());
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
