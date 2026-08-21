package com.stellar.system.service;

import com.stellar.infra.CallLogSink;
import com.stellar.infra.ExternalCallLogEntry;
import com.stellar.system.entity.SysLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * {@link CallLogSink} 的 system 实现：把外部调用日志映射进 sys_log
 * （module=外部调用，operationType=OTHER），复用 {@link SysLogService#saveLog} 异步落库；
 * 登录场景只带 userId，用户名由 saveLog 异步线程解析。
 */
@Component
@RequiredArgsConstructor
public class SysLogCallLogSink implements CallLogSink {

    private final SysLogService sysLogService;

    @Override
    public void write(ExternalCallLogEntry entry) {
        SysLog sysLog = new SysLog();
        sysLog.setModule("外部调用");
        sysLog.setOperationType("OTHER");
        sysLog.setOperator(entry.getOperator());
        sysLog.setOperatorUserId(entry.getOperatorUserId());
        sysLog.setRequestMethod("POST");
        sysLog.setRequestUrl(entry.getProvider() + " / " + entry.getAction());
        sysLog.setJavaMethod(entry.getProvider());
        sysLog.setParams(entry.getParams());
        sysLog.setStatus(entry.isSuccess() ? 1 : 0);
        sysLog.setErrorMsg(entry.getErrorMsg());
        sysLog.setIp(entry.getIp());
        sysLog.setDuration(entry.getDurationMs());
        sysLog.setCreateTime(LocalDateTime.now());
        sysLogService.saveLog(sysLog);
    }
}
