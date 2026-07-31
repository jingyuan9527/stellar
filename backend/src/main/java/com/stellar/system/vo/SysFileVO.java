package com.stellar.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件展示对象（不含二进制 data，列表/详情用）。
 */
@Data
public class SysFileVO {

    private Long id;

    private String originalName;

    private String ext;

    private String contentType;

    private Long size;

    private Long userId;

    /** 上传者用户名（sys_user.username，历史/系统文件为 null） */
    private String uploaderName;

    private LocalDateTime createTime;
}
