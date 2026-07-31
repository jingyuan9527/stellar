package com.stellar.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 游戏成绩提交参数。
 */
@Data
public class GameScoreSubmitDTO {

    @NotBlank(message = "玩家名称不能为空")
    @Size(max = 64, message = "玩家名称最长 64 字符")
    private String playerName;

    @NotNull(message = "得分不能为空")
    @Min(value = 0, message = "得分非法")
    @Max(value = 30, message = "得分非法")
    private Integer score;

    @NotNull(message = "用时不能为空")
    @Min(value = 0, message = "用时非法")
    private Integer totalTime;

    @NotNull(message = "正确率不能为空")
    @Min(value = 0, message = "正确率非法")
    @Max(value = 100, message = "正确率非法")
    private Double accuracy;
}
