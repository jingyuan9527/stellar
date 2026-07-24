package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 神奇海螺预设回答
 */
@Data
@TableName("conch_answer")
public class ConchAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 回答文本（如"确实如此"） */
    private String answerText;

    /** 匹配描述（辅助 LLM 语义匹配） */
    private String matchDescription;

    /** 音频文件 ID（引用 sys_file.id） */
    private Long fileId;

    /** 是否启用: 0禁用 1启用 */
    private Integer enabled;

    /** 排序 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 逻辑删除 */
    @TableLogic
    @JsonIgnore
    private Integer deleted;
}
