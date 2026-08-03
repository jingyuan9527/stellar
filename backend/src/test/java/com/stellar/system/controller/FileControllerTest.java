package com.stellar.system.controller;

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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link FileController} 单测：上传/读取/分页/删除。GET /{id} 的缺失文件抛 BusinessException、
 * Content-Type 兜底、缓存头设置，以及批量删除透传。
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
        when(fileService.upload(f)).thenReturn("/file/9");
        assertEquals("/file/9", controller.upload(f).getData());
    }

    @Test
    void get_正常_设缓存头与contentType() {
        SysFile sf = new SysFile();
        sf.setContentType("image/png");
        sf.setData(new byte[]{1, 2, 3});
        when(fileService.getFull(9L)).thenReturn(sf);

        ResponseEntity<byte[]> resp = controller.get(9L);

        assertEquals("image/png", resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertEquals("public, max-age=604800", resp.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals(3, resp.getBody().length);
    }

    @Test
    void get_contentType为空_兜底octetStream() {
        SysFile sf = new SysFile();
        sf.setContentType(null);
        sf.setData(new byte[]{1});
        when(fileService.getFull(9L)).thenReturn(sf);
        assertEquals("application/octet-stream", controller.get(9L).getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
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
