package com.stellar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典数据：某字典类型下的具体可选值。
 */
@Data
@TableName("sys_dict_data")
public class SysDictData {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属字典编码，引用 sys_dict_type.dict_code */
    private String dictCode;

    /** 字典值，如 TEXT/IMAGE */
    private String value;

    /** 显示标签，如 文本对话/图片生成 */
    private String label;

    private Integer sortOrder;

    /** 0禁用 1启用 */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
