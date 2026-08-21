package com.stellar.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.system.dto.SysFileQueryDTO;
import com.stellar.system.entity.SysFile;
import com.stellar.system.service.FileService;
import com.stellar.system.vo.SysFileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link FileController} 单测：上传/读取/分页/删除。GET /{id} 覆盖可见性——
 * 公开文件游客可读、私有文件匿名拒绝、缺失文件抛 BusinessException、缓存头与 Content-Type。
 */
@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    FileService fileService;

    FileController controller;

    @BeforeEach
    void setup() {
        controller = new FileController(fileService);
    }

    @Test
    void upload_正常_返回路径() {
        MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        when(fileService.upload(f, false)).thenReturn("/file/9");
        assertEquals("/file/9", controller.upload(f, false).getData());
    }

    @Test
    void upload_公开标记_透传service() {
        MockMultipartFile f = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        when(fileService.upload(f, true)).thenReturn("/file/10");
        assertEquals("/file/10", controller.upload(f, true).getData());
    }

    private SysFile publicFile() {
        SysFile sf = new SysFile();
        sf.setIsPublic(true);
        sf.setContentType("image/png");
        sf.setData(new byte[]{1, 2, 3});
        return sf;
    }

    @Test
    void get_公开文件_设缓存头与contentType() {
        when(fileService.getFull(9L)).thenReturn(publicFile());

        ResponseEntity<byte[]> resp = controller.get(9L);

        assertEquals("image/png", resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("public, max-age=604800", resp.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals(3, resp.getBody().length);
    }

    @Test
    void get_contentType为空_兜底octetStream() {
        SysFile sf = publicFile();
        sf.setContentType(null);
        when(fileService.getFull(9L)).thenReturn(sf);
        assertEquals("application/octet-stream", controller.get(9L).getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void get_私有文件_匿名拒绝() {
        SysFile sf = publicFile();
        sf.setIsPublic(false);
        when(fileService.getFull(9L)).thenReturn(sf);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            assertThrows(BusinessException.class, () -> controller.get(9L));
        }
    }

    @Test
    void get_私有文件_登录非owner拒绝() {
        SysFile sf = publicFile();
        sf.setIsPublic(false);
        sf.setUserId(7L);
        when(fileService.getFull(9L)).thenReturn(sf);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(8L);
            assertThrows(BusinessException.class, () -> controller.get(9L));
        }
    }

    @Test
    void get_私有文件_owner可读_私有缓存头() {
        SysFile sf = publicFile();
        sf.setIsPublic(false);
        sf.setUserId(7L);
        when(fileService.getFull(9L)).thenReturn(sf);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            ResponseEntity<byte[]> resp = controller.get(9L);
            assertEquals("private, max-age=3600", resp.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        }
    }

    @Test
    void get_文件不存在_抛BusinessException() {
        when(fileService.getFull(9L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> controller.get(9L));
    }

    @Test
    void get_data为空_抛BusinessException() {
        SysFile sf = new SysFile();
        sf.setContentType("image/png");
        sf.setData(null);
        when(fileService.getFull(9L)).thenReturn(sf);
        assertThrows(BusinessException.class, () -> controller.get(9L));
    }

    @Test
    void page_正常() {
        SysFileQueryDTO q = new SysFileQueryDTO();
        when(fileService.page(q)).thenReturn(new Page<SysFileVO>());
        assertNotNull(controller.page(q).getData());
    }

    @Test
    void remove_正常() {
        controller.remove(1L);
        verify(fileService).remove(1L);
    }

    @Test
    void removeBatch_正常() {
        controller.removeBatch(List.of(1L, 2L));
        verify(fileService).removeBatch(List.of(1L, 2L));
    }
}
