package com.stellar.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.entity.SysDictData;
import com.stellar.mapper.SysDictDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 字典服务：按 dictCode 查启用数据，供前端下拉/枚举渲染。
 * <p>字典数据变更低频，种子数据由 schema.sql 幂等播种。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictService {

    private final SysDictDataMapper dictDataMapper;

    /**
     * 按字典编码查启用的数据列表（按 sort_order 升序）。
     */
    public List<SysDictData> listDataByCode(String dictCode) {
        if (dictCode == null || dictCode.isBlank()) {
            return Collections.emptyList();
        }
        return dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictCode, dictCode)
                .eq(SysDictData::getEnabled, 1)
                .orderByAsc(SysDictData::getSortOrder));
    }
}
