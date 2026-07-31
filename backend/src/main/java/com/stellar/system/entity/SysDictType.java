package com.stellar.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型：管理一组枚举值集合（如 model_type）。
 */
@Data
@TableName("sys_dict_type")
public class SysDictType {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典编码，唯一，如 model_type */
    private String dictCode;

    private String dictName;

    /** 0禁用 1启用 */
    private Integer enabled;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
