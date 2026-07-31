package com.stellar.system.dto;

import lombok.Data;

/**
 * 菜单可见性单项配置（管理后台批量保存用）。
 */
@Data
public class MenuVisibilityItemDTO {

    private String routeKey;

    private String routeName;

    private String parentKey;

    private Integer publicVisible;

    private Integer sortOrder;
}
