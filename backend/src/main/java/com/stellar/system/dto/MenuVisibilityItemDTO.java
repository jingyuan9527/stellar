package com.stellar.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 菜单可见性单项配置（管理后台批量保存用）
 */
@Data
public class MenuVisibilityItemDTO {

    @NotBlank(message = "routeKey 不能为空")
    @Size(max = 100, message = "routeKey 最长 100 字")
    private String routeKey;

    @Size(max = 100, message = "routeName 最长 100 字")
    private String routeName;

    @Size(max = 100, message = "parentKey 最长 100 字")
    private String parentKey;

    /** 0 私有 / 1 公开 */
    @Min(value = 0, message = "publicVisible 仅允许 0/1")
    @Max(value = 1, message = "publicVisible 仅允许 0/1")
    private Integer publicVisible;

    @Min(value = 0, message = "sortOrder 不能为负")
    @Max(value = 9999, message = "sortOrder 过大")
    private Integer sortOrder;
}
