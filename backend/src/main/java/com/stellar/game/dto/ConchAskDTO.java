package com.stellar.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 神奇海螺提问请求
 */
@Data
public class ConchAskDTO {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过500字")
    private String question;
}
