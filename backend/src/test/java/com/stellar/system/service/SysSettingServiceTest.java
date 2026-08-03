package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.stellar.system.entity.SysSetting;
import com.stellar.system.mapper.SysSettingMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SysSettingService} 单测：get/getAsBoolean 的默认值、值解析、空 key、
 * set 的 upsert 两分支。
 */
@ExtendWith(MockitoExtension.class)
class SysSettingServiceTest {

    @Mock
    SysSettingMapper settingMapper;

    private SysSetting setting(String key, String value) {
        SysSetting s = new SysSetting();
        s.setSettingKey(key);
        s.setSettingValue(value);
        return s;
    }

    @Test
    void get_无记录_返回默认值() {
        SysSettingService service = new SysSettingService(settingMapper);
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertEquals("def", service.get("k", "def"));
    }

    @Test
    void get_空key_返回默认值() {
        SysSettingService service = new SysSettingService(settingMapper);
        assertEquals("def", service.get("", "def"));
        verify(settingMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void get_有值_返回() {
        SysSettingService service = new SysSettingService(settingMapper);
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("k", "v1"));
        assertEquals("v1", service.get("k", "def"));
    }

    @Test
    void getAsBoolean_无记录_返回默认() {
        SysSettingService service = new SysSettingService(settingMapper);
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertTrue(service.getAsBoolean("k", true));
        assertFalse(service.getAsBoolean("k", false));
    }

    @Test
    void getAsBoolean_值为1_true() {
        SysSettingService service = new SysSettingService(settingMapper);
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("k", "1"));
        assertTrue(service.getAsBoolean("k", false));
    }

    @Test
    void getAsBoolean_值为true_true() {
        SysSettingService service = new SysSettingService(settingMapper);
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("k", "true"));
        assertTrue(service.getAsBoolean("k", false));
    }

    @Test
    void getAsBoolean_其他值_false() {
        SysSettingService service = new SysSettingService(settingMapper);
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(setting("k", "0"));
        assertFalse(service.getAsBoolean("k", true));
    }

    @Test
    void set_空key_忽略() {
        SysSettingService service = new SysSettingService(settingMapper);
        service.set("", "v", "d");
        verify(settingMapper, never()).selectOne(any(Wrapper.class));
    }

    @Test
    void set_不存在_插入() {
        SysSettingService service = new SysSettingService(settingMapper);
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        service.set("k", "v", "desc");
        ArgumentCaptor<SysSetting> cap = ArgumentCaptor.forClass(SysSetting.class);
        verify(settingMapper).insert(cap.capture());
        assertEquals("k", cap.getValue().getSettingKey());
        assertEquals("v", cap.getValue().getSettingValue());
        verify(settingMapper, never()).updateById(any(SysSetting.class));
    }

    @Test
    void set_存在_更新值() {
        SysSettingService service = new SysSettingService(settingMapper);
        SysSetting exist = setting("k", "old");
        when(settingMapper.selectOne(any(Wrapper.class))).thenReturn(exist);
        service.set("k", "new", null);
        verify(settingMapper).updateById(exist);
        assertEquals("new", exist.getSettingValue());
        // description 为空时不覆盖
        assertNull(exist.getDescription());
    }
}
