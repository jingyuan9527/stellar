package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stellar.system.dto.MenuVisibilityItemDTO;
import com.stellar.system.entity.SysMenuVisibility;
import com.stellar.system.mapper.SysMenuVisibilityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单可见性配置服务。
 * <p>表内只存"被配置过"的路由记录；前端路由全集由前端 routes 数组提供，
 * 后端只负责维护 public_visible 状态并对外暴露游客可见集合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuVisibilityService {

    private final SysMenuVisibilityMapper mapper;

    /**
     * 游客可见的 route key 列表（public_visible=1）。
     * <p>游客首屏高频读，写仅后台批量保存，走 Spring Cache。
     */
    @Cacheable(cacheNames = "menu-visibility", key = "'public-keys'")
    public List<String> listPublicRouteKeys() {
        List<SysMenuVisibility> list = mapper.selectList(new LambdaQueryWrapper<SysMenuVisibility>()
                .eq(SysMenuVisibility::getPublicVisible, 1)
                .orderByAsc(SysMenuVisibility::getSortOrder));
        return list.stream()
                .map(SysMenuVisibility::getRouteKey)
                .collect(Collectors.toList());
    }

    /**
     * 全量配置列表（管理后台展示用）。
     */
    @Cacheable(cacheNames = "menu-visibility", key = "'all'")
    public List<SysMenuVisibility> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<SysMenuVisibility>()
                .orderByAsc(SysMenuVisibility::getSortOrder));
    }

    /**
     * 批量 upsert：存在则更新状态，不存在则插入。整事务。
     * <p>写操作清空菜单可见性全部缓存（public-keys + all）。
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "menu-visibility", allEntries = true)
    public void batchUpsert(List<MenuVisibilityItemDTO> items) {
        if (items == null) {
            return;
        }
        for (MenuVisibilityItemDTO item : items) {
            if (item.getRouteKey() == null || item.getRouteKey().isBlank()) {
                continue;
            }
            SysMenuVisibility exist = mapper.selectOne(new LambdaQueryWrapper<SysMenuVisibility>()
                    .eq(SysMenuVisibility::getRouteKey, item.getRouteKey()));
            if (exist != null) {
                exist.setRouteName(item.getRouteName());
                exist.setParentKey(item.getParentKey());
                exist.setPublicVisible(item.getPublicVisible());
                exist.setSortOrder(item.getSortOrder());
                exist.setUpdateTime(LocalDateTime.now());
                mapper.updateById(exist);
            } else {
                SysMenuVisibility entity = new SysMenuVisibility();
                entity.setRouteKey(item.getRouteKey());
                entity.setRouteName(item.getRouteName());
                entity.setParentKey(item.getParentKey());
                entity.setPublicVisible(item.getPublicVisible() == null ? 0 : item.getPublicVisible());
                entity.setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder());
                entity.setCreateTime(LocalDateTime.now());
                entity.setUpdateTime(LocalDateTime.now());
                mapper.insert(entity);
            }
        }
        log.info("[菜单可见性] 批量保存完成，共 {} 项", items.size());
    }
}
