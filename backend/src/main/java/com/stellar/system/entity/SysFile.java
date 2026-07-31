package com.stellar.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件（图片等，二进制存数据库 sys_file 表，无磁盘依赖）
 */
@Data
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名 */
    private String originalName;

    /** 扩展名（小写） */
    private String ext;

    /** MIME 类型 */
    private String contentType;

    /** 文件大小（字节） */
    private Long size;

    /** 文件二进制数据（列表查询时不加载，按需 selectFullById 查询） */
    @TableField(select = false)
    private byte[] data;

    /** 上传者用户ID（可空，历史数据/系统生成为 NULL） */
    private Long userId;

    /** 上传时间 */
    private LocalDateTime createTime;
}
