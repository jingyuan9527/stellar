package com.stellar.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 神奇海螺提问历史
 */
@Data
@TableName("conch_record")
public class ConchRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户提问文本 */
    private String questionText;

    /** 命中的预设回答 ID */
    private Long answerId;

    /** 登录用户 ID（游客为 null） */
    private Long userId;

    /** 提问时间 */
    private LocalDateTime createTime;

    /** 逻辑删除 */
    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
