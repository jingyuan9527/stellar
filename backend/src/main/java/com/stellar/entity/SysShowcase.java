package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作品橱窗：游客可见的折腾成果，按类型渲染不同卡片。
 */
@Data
@TableName("sys_showcase")
public class SysShowcase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 类型: cover|text|audio|demo|project|link */
    private String type;

    private String title;

    private String summary;

    /** 封面图 URL（/uploads/xxx） */
    private String coverUrl;

    /** 富文本或 JSON 正文 */
    private String content;

    /** 音视频/媒体 URL */
    private String mediaUrl;

    /** 跳转链接 */
    private String link;

    /** 标签，逗号分隔 */
    private String tags;

    private Integer sortOrder;

    /** 是否公开展示: 0否 1是 */
    private Integer visible;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
