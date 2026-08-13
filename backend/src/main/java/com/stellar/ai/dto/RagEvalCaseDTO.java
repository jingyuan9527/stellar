package com.stellar.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * RAG 评估用例（golden set）请求：问题 + 期望命中的来源 key 列表 + 可选知识库。
 */
@Data
public class RagEvalCaseDTO {

    /** 编辑时必填 */
    private Long id;

    /** 用户问题（golden，与线上同样的提问方式） */
    @NotBlank(message = "query 不能为空")
    private String query;

    /** 关联知识库ID(可空，空=仅备忘笔记源) */
    private Long kbId;

    /** 期望命中的来源key列表, 如 ["memos:12","kb:3"]（memos:{noteId} / kb:{chunkId}） */
    @NotEmpty(message = "expectedSources 至少一个")
    private List<String> expectedSources;

    /** 备注 */
    private String note;
}