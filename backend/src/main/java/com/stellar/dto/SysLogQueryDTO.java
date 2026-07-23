package com.stellar.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysLogQueryDTO {

    private String module;

    private String operator;

    private Integer status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
