package com.stellar.system.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.stellar.system.dto.ProfileProjectDTO;
import com.stellar.system.entity.SysProfileProject;
import com.stellar.system.mapper.SysProfileProjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link ProfileProjectService} 单测：list 排序列表、create/update/delete、
 * update 不存在的抛 IllegalArgumentException。
 */
@ExtendWith(MockitoExtension.class)
class ProfileProjectServiceTest {

    @Mock
    SysProfileProjectMapper mapper;

    private ProfileProjectDTO dto(Long id) {
        ProfileProjectDTO d = new ProfileProjectDTO();
        d.setId(id);
        d.setName("proj");
        d.setSiteUrl("https://site.com");
        d.setDescription("desc");
        return d;
    }

    @Test
    void list_返回列表() {
        ProfileProjectService service = new ProfileProjectService(mapper);
        SysProfileProject p = new SysProfileProject();
        p.setId(1L);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(p));
        assertEquals(1, service.list().size());
    }

    @Test
    void create_插入并填充时间() {
        ProfileProjectService service = new ProfileProjectService(mapper);
        service.create(dto(null));
        ArgumentCaptor<SysProfileProject> cap = ArgumentCaptor.forClass(SysProfileProject.class);
        verify(mapper).insert(cap.capture());
        assertEquals("proj", cap.getValue().getName());
        assertNotNull(cap.getValue().getCreateTime());
        assertNotNull(cap.getValue().getUpdateTime());
    }

    @Test
    void update_不存在_抛IllegalArgument() {
        ProfileProjectService service = new ProfileProjectService(mapper);
        when(mapper.selectById(1L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.update(dto(1L)));
    }

    @Test
    void update_存在_更新() {
        ProfileProjectService service = new ProfileProjectService(mapper);
        SysProfileProject exist = new SysProfileProject();
        exist.setId(1L);
        exist.setName("old");
        when(mapper.selectById(1L)).thenReturn(exist);
        service.update(dto(1L));
        verify(mapper).updateById(exist);
        assertEquals("proj", exist.getName());
    }

    @Test
    void delete_删除() {
        ProfileProjectService service = new ProfileProjectService(mapper);
        service.delete(1L);
        verify(mapper).deleteById(1L);
    }
}
