package com.stellar.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏成绩排行榜展示对象（不含 IP / userId 等敏感字段）。
 */
@Data
public class GameScoreVO {

    private Long id;

    private String playerName;

    private Integer score;

    private Integer totalTime;

    private Double accuracy;

    private LocalDateTime createTime;
}
