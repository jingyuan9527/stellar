package com.stellar.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.stellar.entity.SysLog;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 操作日志导出视图，仅包含导出所需字段。
 */
@Data
public class SysLogExportVO {

    @ExcelProperty("ID")
    @ColumnWidth(10)
    private Long id;

    @ExcelProperty("模块")
    @ColumnWidth(16)
    private String module;

    @ExcelProperty("操作类型")
    @ColumnWidth(12)
    private String operationType;

    @ExcelProperty("操作人")
    @ColumnWidth(14)
    private String operator;

    @ExcelProperty("请求方法")
    @ColumnWidth(10)
    private String requestMethod;

    @ExcelProperty("请求URL")
    @ColumnWidth(28)
    private String requestUrl;

    @ExcelProperty("状态")
    @ColumnWidth(8)
    private String status;

    @ExcelProperty("IP")
    @ColumnWidth(16)
    private String ip;

    @ExcelProperty("耗时(ms)")
    @ColumnWidth(10)
    private Long duration;

    @ExcelProperty("操作时间")
    @ColumnWidth(20)
    private String createTime;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static SysLogExportVO of(SysLog log) {
        SysLogExportVO vo = new SysLogExportVO();
        vo.id = log.getId();
        vo.module = log.getModule();
        vo.operationType = log.getOperationType();
        vo.operator = log.getOperator();
        vo.requestMethod = log.getRequestMethod();
        vo.requestUrl = log.getRequestUrl();
        vo.status = log.getStatus() != null && log.getStatus() == 1 ? "成功" : "失败";
        vo.ip = log.getIp();
        vo.duration = log.getDuration();
        vo.createTime = log.getCreateTime() != null ? log.getCreateTime().format(FMT) : "";
        return vo;
    }
}
