package com.stellar.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String module;

    private String operationType;

    private String operator;

    /**
     * 操作人 userId（不落库）：由切面/外部调用日志在请求线程只取 userId，用户名在 saveLog 异步线程查库填充 operator。
     */
    @TableField(exist = false)
    private Long operatorUserId;

    private String requestMethod;

    private String requestUrl;

    private String javaMethod;

    private String params;

    private Integer status;

    private String errorMsg;

    private String ip;

    private Long duration;

    private LocalDateTime createTime;
}
