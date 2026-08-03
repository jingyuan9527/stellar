package com.stellar.system.service;

import com.stellar.system.dto.ProfileDTO;
import com.stellar.system.entity.SysProfile;
import com.stellar.system.mapper.SysProfileMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ProfileService} 单测：get 无记录返回 id=1 占位、update 的 upsert 两分支。
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    SysProfileMapper profileMapper;

    private ProfileDTO dto() {
        ProfileDTO d = new ProfileDTO();
        d.setNickname("nick");
        d.setTitle("title");
        d.setLocation("SH");
        d.setBio("bio");
        return d;
    }

    @Test
    void get_无记录_返回id1占位() {
        ProfileService service = new ProfileService(profileMapper);
        when(profileMapper.selectById(1L)).thenReturn(null);
        SysProfile p = service.get();
        assertNotNull(p);
        assertEquals(1L, p.getId());
    }

    @Test
    void get_有记录_返回() {
        ProfileService service = new ProfileService(profileMapper);
        SysProfile exist = new SysProfile();
        exist.setId(1L);
        exist.setNickname("exist");
        when(profileMapper.selectById(1L)).thenReturn(exist);
        assertEquals("exist", service.get().getNickname());
    }

    @Test
    void update_无记录_插入() {
        ProfileService service = new ProfileService(profileMapper);
        when(profileMapper.selectById(1L)).thenReturn(null);
        service.update(dto());
        ArgumentCaptor<SysProfile> cap = ArgumentCaptor.forClass(SysProfile.class);
        verify(profileMapper).insert(cap.capture());
        assertEquals(1L, cap.getValue().getId());
        assertEquals("nick", cap.getValue().getNickname());
        verify(profileMapper, never()).updateById(any(SysProfile.class));
    }

    @Test
    void update_有记录_更新() {
        ProfileService service = new ProfileService(profileMapper);
        SysProfile exist = new SysProfile();
        exist.setId(1L);
        exist.setNickname("old");
        when(profileMapper.selectById(1L)).thenReturn(exist);
        service.update(dto());
        verify(profileMapper).updateById(exist);
        assertEquals("nick", exist.getNickname());
        assertEquals("title", exist.getTitle());
    }
}
