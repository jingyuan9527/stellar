package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.stellar.system.dto.MenuVisibilityItemDTO;
import com.stellar.system.entity.SysMenuVisibility;
import com.stellar.system.mapper.SysMenuVisibilityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link MenuVisibilityService} 单测：公开路由 key 列表、全量列表、
 * batchUpsert 的 null/空 key 跳过、更新与插入两分支。
 */
@ExtendWith(MockitoExtension.class)
class MenuVisibilityServiceTest {

    @Mock
    SysMenuVisibilityMapper mapper;

    private MenuVisibilityItemDTO item(String routeKey, String routeName, Integer visible) {
        MenuVisibilityItemDTO d = new MenuVisibilityItemDTO();
        d.setRouteKey(routeKey);
        d.setRouteName(routeName);
        d.setPublicVisible(visible);
        return d;
    }

    @Test
    void listPublicRouteKeys_只取公开key() {
        MenuVisibilityService service = new MenuVisibilityService(mapper);
        SysMenuVisibility v = new SysMenuVisibility();
        v.setRouteKey("/tools");
        v.setPublicVisible(1);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(v));
        List<String> keys = service.listPublicRouteKeys();
        assertEquals(1, keys.size());
        assertEquals("/tools", keys.get(0));
    }

    @Test
    void listAll_返回全量() {
        MenuVisibilityService service = new MenuVisibilityService(mapper);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(new SysMenuVisibility()));
        assertEquals(1, service.listAll().size());
    }

    @Test
    void batchUpsert_null_直接返回() {
        MenuVisibilityService service = new MenuVisibilityService(mapper);
        service.batchUpsert(null);
        verify(mapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void batchUpsert_空key_跳过() {
        MenuVisibilityService service = new MenuVisibilityService(mapper);
        service.batchUpsert(List.of(item("", "x", 1)));
        verify(mapper, never()).selectOne(any(Wrapper.class));
        verify(mapper, never()).insert(any(SysMenuVisibility.class));
    }

    @Test
    void batchUpsert_存在_更新() {
        MenuVisibilityService service = new MenuVisibilityService(mapper);
        SysMenuVisibility exist = new SysMenuVisibility();
        exist.setId(1L);
        exist.setRouteKey("/tools");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(exist);

        service.batchUpsert(List.of(item("/tools", "工具", 1)));

        verify(mapper).updateById(exist);
        verify(mapper, never()).insert(any(SysMenuVisibility.class));
        assertEquals(1, exist.getPublicVisible());
        assertEquals("工具", exist.getRouteName());
    }

    @Test
    void batchUpsert_不存在_插入且默认值() {
        MenuVisibilityService service = new MenuVisibilityService(mapper);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);

        service.batchUpsert(List.of(item("/home", "首页", null)));

        ArgumentCaptor<SysMenuVisibility> cap = ArgumentCaptor.forClass(SysMenuVisibility.class);
        verify(mapper).insert(cap.capture());
        assertEquals("/home", cap.getValue().getRouteKey());
        assertEquals(0, cap.getValue().getPublicVisible());
        verify(mapper, never()).updateById(any(SysMenuVisibility.class));
    }
}
