package com.stellar.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人项目展示（about 页公开展示，多条）。
 */
@Data
@TableName("sys_profile_project")
public class SysProfileProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 项目名 */
    private String name;

    /** 线上地址（可空） */
    private String siteUrl;

    /** 源码地址（如 GitHub，可空） */
    private String sourceUrl;

    /** 简介（1-2 句） */
    private String description;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
