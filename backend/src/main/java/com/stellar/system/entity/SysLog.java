package com.stellar.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.baomidou.mybatisplus.annotation.IdType;
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
