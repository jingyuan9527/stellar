package com.stellar.memos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 本地编辑笔记正文请求：仅改本地备份，置 local_edited 待写回；
 * 若与远端产生双向变更，下次同步会标记冲突由用户裁决。
 */
@Data
public class MemosContentUpdateDTO {

    /** 编辑后的正文（Markdown） */
    @NotNull(message = "正文不能为空")
    @Size(max = 60000, message = "正文最长 60000 字符")
    private String content;
}
