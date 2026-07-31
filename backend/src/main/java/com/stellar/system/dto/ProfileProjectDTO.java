package com.stellar.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人项目展示 新增/编辑 DTO。
 */
@Data
public class ProfileProjectDTO {

    /** 编辑时传，新增时不传 */
    private Long id;

    @NotBlank(message = "项目名不能为空")
    @Size(max = 100, message = "项目名不能超过100字")
    private String name;

    @Size(max = 500, message = "线上地址不能超过500字")
    private String siteUrl;

    @Size(max = 500, message = "源码地址不能超过500字")
    private String sourceUrl;

    @Size(max = 500, message = "简介不能超过500字")
    private String description;
}
