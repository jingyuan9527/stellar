package com.stellar.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 前端错误上报请求 */
@Data
public class ClientErrorReportDTO {

    /** 错误消息 */
    @NotBlank(message = "错误消息不能为空")
    @Size(max = 500, message = "错误消息过长")
    private String message;

    /** 堆栈（前端已截断） */
    @Size(max = 4000, message = "堆栈过长")
    private String stack;

    /** 来源：vue（渲染）/ window（全局 error）/ promise（未捕获 rejection）/ boundary（组件边界） */
    @Size(max = 50)
    private String source;

    /** 出错页面 URL */
    @Size(max = 500)
    private String url;
}