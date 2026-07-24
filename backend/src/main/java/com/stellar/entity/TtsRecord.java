package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 语音合成记录
 */
@Data
@TableName("tts_record")
public class TtsRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 合成文本 */
    private String text;

    /** 发音人，如 zh-CN-XiaoxiaoNeural */
    private String voice;

    /** 语速 0.5~2.0 */
    private Double rate;

    /** 音调 0~2.0 */
    private Double pitch;

    /** 音量 0~1.0 */
    private Double volume;

    /** MP3 音频数据（列表查询时不加载） */
    @TableField(select = false)
    private byte[] audioData;

    /** 音频文件大小（字节） */
    private Long fileSize;

    /** 操作人用户名 */
    private String operator;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 逻辑删除 */
    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
