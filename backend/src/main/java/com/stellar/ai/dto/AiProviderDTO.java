package com.stellar.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiProviderDTO {

    /** 更新时必填 */
    private Long id;

    @NotBlank(message = "供应商名称不能为空")
    private String name;

    @NotBlank(message = "接口地址不能为空")
    private String endpoint;

    /** 为空时保留原值，避免脱敏回写覆盖 */
    private String apiKey;

    private Integer enabled;

    private Integer sortOrder;
}
