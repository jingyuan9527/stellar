package com.stellar.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.AiModelDTO;
import com.stellar.ai.entity.SysAiModel;
import com.stellar.ai.entity.SysAiProvider;
import com.stellar.ai.mapper.SysAiModelMapper;
import com.stellar.ai.mapper.SysAiProviderMapper;
import com.stellar.ai.vo.AiModelVO;
import com.stellar.ai.vo.AiResolvedConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 模型服务：模型 CRUD + 按类型设默认（同类型互斥）+ 解析调用配置。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final SysAiModelMapper modelMapper;
    private final SysAiProviderMapper providerMapper;

    /**
     * 查某供应商下全部模型，按 sort_order 升序。
     */
    @Cacheable(cacheNames = "ai-model", key = "'provider:'+#providerId")
    public List<AiModelVO> listByProvider(Long providerId) {
        List<SysAiModel> list = modelMapper.selectList(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getProviderId, providerId)
                .orderByAsc(SysAiModel::getSortOrder)
                .orderByAsc(SysAiModel::getId));
        return list.stream().map(m -> toVO(m, null)).collect(Collectors.toList());
    }

    /**
     * 查全部模型（带供应商名称），按 sort_order 升序。
     */
    @Cacheable(cacheNames = "ai-model", key = "'all'")
    public List<AiModelVO> listAll() {
        List<SysAiModel> list = modelMapper.selectList(new LambdaQueryWrapper<SysAiModel>()
                .orderByAsc(SysAiModel::getSortOrder)
                .orderByAsc(SysAiModel::getId));
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> providerIds = list.stream().map(SysAiModel::getProviderId).collect(Collectors.toSet());
        Map<Long, String> nameMap = new HashMap<>();
        if (!providerIds.isEmpty()) {
            providerMapper.selectBatchIds(providerIds)
                    .forEach(p -> nameMap.put(p.getId(), p.getName()));
        }
        return list.stream().map(m -> toVO(m, nameMap.get(m.getProviderId()))).collect(Collectors.toList());
    }

    /**
     * 按类型查启用的模型（供前端下拉/调用方选模型用）。公开接口高频读，走 Spring Cache。
     * <p>空结果不缓存（unless）：避免空 List 序列化/反序列化 type id 的边界问题，
     * 同时无该类型模型时每次查 DB（空，快），配了模型后自动缓存。
     */
    @Cacheable(cacheNames = "ai-model", key = "#modelType", unless = "#result.isEmpty()")
    public List<AiModelVO> listEnabledByType(String modelType) {
        List<SysAiModel> list = modelMapper.selectList(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getModelType, modelType)
                .eq(SysAiModel::getEnabled, 1)
                .orderByAsc(SysAiModel::getSortOrder)
                .orderByAsc(SysAiModel::getId));
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> providerIds = list.stream().map(SysAiModel::getProviderId).collect(Collectors.toSet());
        Map<Long, String> nameMap = new HashMap<>();
        // 仅启用的供应商下的模型才可用
        providerMapper.selectList(new LambdaQueryWrapper<SysAiProvider>()
                        .in(SysAiProvider::getId, providerIds)
                        .eq(SysAiProvider::getEnabled, 1))
                .forEach(p -> nameMap.put(p.getId(), p.getName()));
        return list.stream()
                .filter(m -> nameMap.containsKey(m.getProviderId()))
                .map(m -> toVO(m, nameMap.get(m.getProviderId())))
                .collect(Collectors.toList());
    }

    @CacheEvict(cacheNames = "ai-model", allEntries = true)
    public void create(AiModelDTO dto) {
        if (dto.getProviderId() == null) {
            throw new BusinessException("供应商不能为空");
        }
        if (providerMapper.selectById(dto.getProviderId()) == null) {
            throw new BusinessException("供应商不存在: id=" + dto.getProviderId());
        }
        SysAiModel m = new SysAiModel();
        m.setProviderId(dto.getProviderId());
        m.setModel(dto.getModel().trim());
        m.setModelType(dto.getModelType().trim());
        m.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
        m.setIsDefault(0);
        m.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        m.setCreateTime(LocalDateTime.now());
        m.setUpdateTime(LocalDateTime.now());
        modelMapper.insert(m);
        // 新建时若标记默认，走互斥逻辑
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            setDefault(m.getId());
        }
        log.info("[AI模型] 新增 id={} providerId={} model={} type={}",
                m.getId(), m.getProviderId(), m.getModel(), m.getModelType());
    }

    @CacheEvict(cacheNames = "ai-model", allEntries = true)
    public void update(AiModelDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("模型 id 不能为空");
        }
        SysAiModel exist = modelMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BusinessException("模型不存在: id=" + dto.getId());
        }
        if (dto.getProviderId() != null) {
            if (providerMapper.selectById(dto.getProviderId()) == null) {
                throw new BusinessException("供应商不存在: id=" + dto.getProviderId());
            }
            exist.setProviderId(dto.getProviderId());
        }
        exist.setModel(dto.getModel().trim());
        exist.setModelType(dto.getModelType().trim());
        if (dto.getEnabled() != null) {
            exist.setEnabled(dto.getEnabled());
        }
        if (dto.getSortOrder() != null) {
            exist.setSortOrder(dto.getSortOrder());
        }
        exist.setUpdateTime(LocalDateTime.now());
        modelMapper.updateById(exist);
        // 切换默认走互斥逻辑
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            setDefault(exist.getId());
        } else if (Integer.valueOf(0).equals(dto.getIsDefault())) {
            exist.setIsDefault(0);
            modelMapper.updateById(exist);
        }
        log.info("[AI模型] 更新 id={} model={} type={}", exist.getId(), exist.getModel(), exist.getModelType());
    }

    @CacheEvict(cacheNames = "ai-model", allEntries = true)
    public void delete(Long id) {
        modelMapper.deleteById(id);
        log.info("[AI模型] 删除 id={}", id);
    }

    @CacheEvict(cacheNames = "ai-model", allEntries = true)
    public void toggleEnabled(Long id, Integer enabled) {
        SysAiModel exist = modelMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("模型不存在: id=" + id);
        }
        exist.setEnabled(enabled);
        exist.setUpdateTime(LocalDateTime.now());
        modelMapper.updateById(exist);
        log.info("[AI模型] 切换启用 id={} enabled={}", id, enabled);
    }

    /**
     * 设为该类型默认：先把同 model_type 的 is_default 清 0，再置当前为 1（同类型互斥）。
     */
    @CacheEvict(cacheNames = "ai-model", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        SysAiModel exist = modelMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("模型不存在: id=" + id);
        }
        // 同类型其他模型清默认
        SysAiModel clear = new SysAiModel();
        clear.setIsDefault(0);
        clear.setUpdateTime(LocalDateTime.now());
        modelMapper.update(clear, new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getModelType, exist.getModelType())
                .eq(SysAiModel::getIsDefault, 1)
                .ne(SysAiModel::getId, id));
        exist.setIsDefault(1);
        exist.setUpdateTime(LocalDateTime.now());
        modelMapper.updateById(exist);
        log.info("[AI模型] 设默认 id={} type={}", id, exist.getModelType());
    }

    /**
     * 按 modelId 解析调用配置（endpoint/apiKey/model/type），供调用方发起 LLM 请求。
     */
    public AiResolvedConfig resolveConfig(Long modelId) {
        SysAiModel m = modelMapper.selectById(modelId);
        if (m == null) {
            throw new BusinessException("AI 模型不存在: id=" + modelId);
        }
        if (m.getEnabled() == null || m.getEnabled() != 1) {
            throw new BusinessException("AI 模型已禁用: id=" + modelId);
        }
        SysAiProvider p = providerMapper.selectById(m.getProviderId());
        if (p == null) {
            throw new BusinessException("AI 供应商不存在: id=" + m.getProviderId());
        }
        if (p.getEnabled() == null || p.getEnabled() != 1) {
            throw new BusinessException("AI 供应商已禁用: " + p.getName());
        }
        if (!StringUtils.hasText(p.getEndpoint())) {
            throw new BusinessException("AI 接口未配置: " + p.getName());
        }
        if (!StringUtils.hasText(p.getApiKey())) {
            throw new BusinessException("AI API Key 未配置: " + p.getName());
        }
        return new AiResolvedConfig(m.getId(), p.getId(), p.getEndpoint(), p.getApiKey(),
                m.getModel(), m.getModelType());
    }

    /**
     * 取某类型的默认模型并解析配置。无默认抛异常（供海螺等不显式选模型的功能用）。
     */
    public AiResolvedConfig resolveDefaultConfig(String modelType) {
        SysAiModel m = modelMapper.selectOne(new LambdaQueryWrapper<SysAiModel>()
                .eq(SysAiModel::getModelType, modelType)
                .eq(SysAiModel::getIsDefault, 1)
                .eq(SysAiModel::getEnabled, 1)
                .last("LIMIT 1"));
        if (m == null) {
            throw new BusinessException("未配置 " + modelType + " 类型的默认模型");
        }
        return resolveConfig(m.getId());
    }

    /**
     * 取某类型的默认模型；无默认则取第一个启用的（供聊天工具调用等不显式选模型的场景用）。
     * <p>resolveDefaultConfig 要求 is_default=1，但管理员可能配了模型却没设默认，
     * 此方法兜底取第一个启用的，避免工具因"未设默认"而不暴露。
     */
    public AiResolvedConfig resolveDefaultOrFirstEnabled(String modelType) {
        try {
            return resolveDefaultConfig(modelType);
        } catch (Exception ignored) {
        }
        List<AiModelVO> models = listEnabledByType(modelType);
        if (models.isEmpty()) {
            throw new BusinessException("未配置 " + modelType + " 类型的可用模型");
        }
        return resolveConfig(models.get(0).getId());
    }

    private AiModelVO toVO(SysAiModel m, String providerName) {
        AiModelVO vo = new AiModelVO();
        vo.setId(m.getId());
        vo.setProviderId(m.getProviderId());
        vo.setProviderName(providerName);
        vo.setModel(m.getModel());
        vo.setModelType(m.getModelType());
        vo.setEnabled(m.getEnabled());
        vo.setIsDefault(m.getIsDefault());
        vo.setSortOrder(m.getSortOrder());
        vo.setCreateTime(m.getCreateTime());
        vo.setUpdateTime(m.getUpdateTime());
        return vo;
    }
}
