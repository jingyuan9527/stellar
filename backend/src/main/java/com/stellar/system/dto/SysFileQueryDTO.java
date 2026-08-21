package com.stellar.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件分页查询参数
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

    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "单页最多 100 条")
    private Integer pageSize = 10;
}
