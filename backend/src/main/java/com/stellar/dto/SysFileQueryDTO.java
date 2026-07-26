package com.stellar.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分页查询参数。
 */
@Data
public class SysFileQueryDTO {

    /** 原始文件名（模糊） */
    private String originalName;

    /** 文件类型分组：image / audio，为空则全部 */
    private String fileType;

    /** 上传者用户ID（可选，预留按人过滤） */
    private Long userId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
