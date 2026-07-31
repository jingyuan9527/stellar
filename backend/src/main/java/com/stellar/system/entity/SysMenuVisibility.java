package com.stellar.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单可见性配置：控制哪些前端路由对游客（未登录）公开。
 */
@Data
@TableName("sys_menu_visibility")
public class SysMenuVisibility {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 前端路由 key，与 useMenu 生成的 key 一致（如 /tts/edge） */
    private String routeKey;

    /** 路由名称，展示用 */
    private String routeName;

    /** 父菜单 key（便于分组展示，如 /tts） */
    private String parentKey;

    /** 是否对游客公开: 0否 1是 */
    private Integer publicVisible;

    /** 排序 */
    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
