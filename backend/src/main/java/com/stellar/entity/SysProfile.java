package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 个人介绍（单条配置，id 固定为 1）。落地页 /home 展示。
 */
@Data
@TableName("sys_profile")
public class SysProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String nickname;

    private String avatar;

    /** 简介 */
    private String bio;

    /** 技能标签，逗号分隔 */
    private String skills;

    /** 外链 JSON，如 {github, email, site} */
    private String links;

    private LocalDateTime updateTime;
}
