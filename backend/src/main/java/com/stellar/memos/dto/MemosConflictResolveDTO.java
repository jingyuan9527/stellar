package com.stellar.memos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 冲突裁决请求：逐条指定方向（local=以本地为准写回远端 / remote=以远端为准覆盖本地），
 * 支持一次提交多条（前端批量选择后统一提交）。
 */
@Data
public class MemosConflictResolveDTO {

    @NotEmpty(message = "请至少选择一条冲突笔记")
    @Valid
    @Size(max = 200, message = "单次最多裁决 200 条")
    private List<Item> items;

    @Data
    public static class Item {

        /** 冲突笔记 id */
        @NotNull(message = "笔记 id 不能为空")
        private Long id;

        /** 裁决方向：local 以本地为准（写回覆盖远端）/ remote 以远端为准（远端覆盖本地） */
        @NotBlank(message = "请选择裁决方向")
        @Pattern(regexp = "local|remote", message = "裁决方向仅支持 local/remote")
        private String direction;
    }
}
