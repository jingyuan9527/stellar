package com.stellar.memos.vo;

import lombok.Data;

/**
 * AI 打标签结果 / 标签写回结果。
 */
@Data
public class MemosJobResultVO {

    /** 本次处理条数 */
    private int processed;

    /** 成功条数（打标签成功 / 写回成功） */
    private int success;

    /** 跳过条数（写回时无新增标签） */
    private int skipped;

    /** 失败条数 */
    private int failed;

    /** 剩余待处理条数（AI 打标签分批上限，剩余可再点） */
    private int remaining;

    /** 打标签后自动写回成功的条数（仅 AI 打标签接口返回） */
    private int pushSuccess;

    /** 打标签后自动写回失败的条数（置待写回，可手动重试） */
    private int pushFailed;
}
