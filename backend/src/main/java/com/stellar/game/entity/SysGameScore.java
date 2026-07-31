package com.stellar.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 游戏成绩排行榜记录。
 */
@Data
@TableName("sys_game_score")
public class SysGameScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 玩家名称（游客填 / 登录取昵称） */
    private String playerName;

    /** 得分（答对题数） */
    private Integer score;

    /** 用时（秒） */
    private Integer totalTime;

    /** 正确率（%） */
    private Double accuracy;

    /** 登录用户 ID（可空，游客为 null） */
    private Long userId;

    /** 提交 IP（反作弊/统计） */
    private String ip;

    /** 提交时间 */
    private LocalDateTime createTime;
}
