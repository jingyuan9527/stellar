package com.stellar.memos.dto;

import lombok.Data;

/**
 * 备忘同步笔记分页查询参数。需登录。
 */
@Data
public class MemosQueryDTO {

    /** 页码（从 1 起） */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 关键字（匹配 uid / 内容 / 标签） */
    private String keyword;

    /** 远端删除状态过滤：null 全部，0 存活，1 已删 */
    private Integer remoteDeleted;
}
