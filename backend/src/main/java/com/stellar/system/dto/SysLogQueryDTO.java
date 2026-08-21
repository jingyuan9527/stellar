package com.stellar.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysLogQueryDTO {

    private String module;

    private String operator;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 1000, message = "导出单次最多 1000 条")
    private Integer pageSize = 10;
}
