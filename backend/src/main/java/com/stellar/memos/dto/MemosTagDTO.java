package com.stellar.memos.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI 打标签请求：勾选的本地笔记 id 列表 + 可选 AI 模型 id（空则用 TEXT 默认模型）。
 */
@Data
public class MemosTagDTO {

    /** 笔记 id 列表（1-200 条） */
    @NotEmpty(message = "请先勾选要打标签的笔记")
    @Size(max = 200, message = "单次打标签最多 200 条")
    private List<Long> ids;

    /** AI 模型 id（TEXT 类型，空则用后端 TEXT 默认模型） */
    private Long modelId;
}
