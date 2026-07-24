package com.stellar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 神奇海螺预设回答新增/编辑请求
 */
@Data
public class ConchAnswerDTO {

    /** 编辑时传，新增时不传 */
    private Long id;

    @NotBlank(message = "回答文本不能为空")
    @Size(max = 200, message = "回答文本不能超过200字")
    private String answerText;

    @Size(max = 500, message = "匹配描述不能超过500字")
    private String matchDescription;

    @NotNull(message = "音频文件不能为空")
    private Long fileId;
}
