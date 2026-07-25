package com.stellar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiModelDTO {

    /** 更新时必填 */
    private Long id;

    /** 新增时必填，编辑时不传则不改供应商 */
    private Long providerId;

    @NotBlank(message = "模型名称不能为空")
    private String model;

    @NotBlank(message = "模型类型不能为空")
    private String modelType;

    private Integer enabled;

    private Integer isDefault;

    private Integer sortOrder;
}
