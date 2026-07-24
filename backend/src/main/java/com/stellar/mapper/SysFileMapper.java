package com.stellar.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stellar.entity.SysFile;
import org.apache.ibatis.annotations.Select;

/**
 * 文件 Mapper
 */
public interface SysFileMapper extends BaseMapper<SysFile> {

    /**
     * 按主键查全字段（含 data 二进制，绕过 @TableField(select=false)）。
     * <p>仅文件读取接口调用，避免列表查询误加载大字段。
     */
    @Select("SELECT id, original_name, ext, content_type, size, data, create_time FROM sys_file WHERE id = #{id}")
    SysFile selectFullById(Long id);
}
