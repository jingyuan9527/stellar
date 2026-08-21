package com.stellar.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.common.FileConstants;
import com.stellar.system.dto.SysFileQueryDTO;
import com.stellar.system.entity.SysFile;
import com.stellar.system.entity.SysUser;
import com.stellar.system.mapper.SysFileMapper;
import com.stellar.system.mapper.SysUserMapper;
import com.stellar.system.vo.SysFileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link FileService} 单测：聚焦上传校验（空文件 / 非法扩展名 / 读取失败 / 正常写入）、
 * 分页（联查上传者用户名、按 image/audio 过滤）、单条与批量硬删除的校验分支。
 * 依赖 {@code SysFileMapper}/{@code SysUserMapper} 用 Mockito 隔离，{@code StpUtil} 静态调用 mock。
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private SysFileMapper fileMapper;
    @Mock
    private SysUserMapper sysUserMapper;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileMapper, sysUserMapper);
    }

    private MultipartFile mockFile(String name, String contentType, long size, byte[] bytes) {
        MultipartFile f = mock(MultipartFile.class);
        when(f.getOriginalFilename()).thenReturn(name);
        when(f.isEmpty()).thenReturn(false);
        lenient().when(f.getContentType()).thenReturn(contentType);
        lenient().when(f.getSize()).thenReturn(size);
        try {
            lenient().when(f.getBytes()).thenReturn(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return f;
    }

    // ===== upload =====

    @Test
    void upload_null_抛异常() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            assertThrows(BusinessException.class, () -> fileService.upload(null, false));
        }
    }

    @Test
    void upload_空文件_抛异常() {
        MultipartFile f = mock(MultipartFile.class);
        when(f.isEmpty()).thenReturn(true);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            BusinessException ex = assertThrows(BusinessException.class, () -> fileService.upload(f, false));
            assertTrue(ex.getMessage().contains("为空"));
        }
    }

    @Test
    void upload_非法扩展名_抛异常() {
        MultipartFile f = mockFile("malware.exe", "application/octet-stream", 10, new byte[10]);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            BusinessException ex = assertThrows(BusinessException.class, () -> fileService.upload(f, false));
            assertTrue(ex.getMessage().contains("不支持的文件类型"));
        }
        verify(fileMapper, never()).insert(any(SysFile.class));
    }

    @Test
    void upload_读取字节失败_抛异常() {
        MultipartFile f = mock(MultipartFile.class);
        when(f.getOriginalFilename()).thenReturn("a.jpg");
        when(f.isEmpty()).thenReturn(false);
        when(f.getSize()).thenReturn(10L);
        try {
            when(f.getBytes()).thenThrow(new java.io.IOException("disk error"));
        } catch (java.io.IOException ignored) {
        }
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            BusinessException ex = assertThrows(BusinessException.class, () -> fileService.upload(f, false));
            assertTrue(ex.getMessage().contains("文件读取失败"));
        }
    }

    @Test
    void upload_合法图片_写入并记登录用户() {
        MultipartFile f = mockFile("photo.JPG", "image/jpeg", 1024, new byte[1024]);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            String url = fileService.upload(f, false);
            assertTrue(url.startsWith("/file/"));
        }
        verify(fileMapper).insert(any(SysFile.class));
    }

    @Test
    void upload_合法音频扩展名_放行() {
        MultipartFile f = mockFile("voice.mp3", "audio/mpeg", 2048, new byte[2048]);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            assertDoesNotThrow(() -> fileService.upload(f, false));
        }
        verify(fileMapper).insert(any(SysFile.class));
    }

    @Test
    void upload_公开标记_写入isPublic() {
        MultipartFile f = mockFile("avatar.png", "image/png", 100, new byte[100]);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            fileService.upload(f, true);
        }
        verify(fileMapper).insert(argThat((SysFile sf) -> Boolean.TRUE.equals(sf.getIsPublic())));
    }

    // ===== getFull =====

    @Test
    void getFull_委托mapper() {
        SysFile sf = new SysFile();
        when(fileMapper.selectFullById(5L)).thenReturn(sf);
        assertSame(sf, fileService.getFull(5L));
    }

    // ===== page =====

    @Test
    void page_联查上传者用户名并按image过滤() {
        SysFileQueryDTO q = new SysFileQueryDTO();
        q.setPageNum(1);
        q.setPageSize(10);
        q.setFileType("image");
        q.setUserId(42L);
        q.setOriginalName("pic");

        SysFile sf = new SysFile();
        sf.setId(1L);
        sf.setUserId(42L);
        sf.setExt("jpg");
        Page<SysFile> page = new Page<>();
        page.setRecords(List.of(sf));
        page.setTotal(1);
        page.setSize(10);
        page.setCurrent(1);
        page.setPages(1);
        when(fileMapper.selectPage(any(), any())).thenReturn(page);

        SysUser u = new SysUser();
        u.setId(42L);
        u.setUsername("bob");
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(u));

        Page<SysFileVO> res = fileService.page(q);
        assertEquals(1, res.getRecords().size());
        assertEquals("bob", res.getRecords().get(0).getUploaderName());
        assertEquals(1L, res.getRecords().get(0).getId());
    }

    @Test
    void page_无上传者_不查用户表() {
        SysFileQueryDTO q = new SysFileQueryDTO();
        Page<SysFile> page = new Page<>();
        page.setRecords(List.of(new SysFile()));
        page.setTotal(0);
        when(fileMapper.selectPage(any(), any())).thenReturn(page);
        fileService.page(q);
        verify(sysUserMapper, never()).selectBatchIds(any());
    }

    // ===== remove =====

    @Test
    void remove_文件不存在_抛异常() {
        when(fileMapper.selectById(9L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> fileService.remove(9L));
        assertTrue(ex.getMessage().contains("文件不存在"));
        verify(fileMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void remove_存在_删除() {
        SysFile sf = new SysFile();
        sf.setId(9L);
        when(fileMapper.selectById(9L)).thenReturn(sf);
        fileService.remove(9L);
        verify(fileMapper).deleteById(9L);
    }

    // ===== removeBatch =====

    @Test
    void removeBatch_空列表_抛异常() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.removeBatch(List.of()));
        assertTrue(ex.getMessage().contains("未选择"));
        verify(fileMapper, never()).deleteBatchIds(any());
    }

    @Test
    void removeBatch_正常_批量删除() {
        SysFile sf = new SysFile();
        sf.setId(1L);
        when(fileMapper.selectBatchIds(List.of(1L, 2L))).thenReturn(List.of(sf));
        fileService.removeBatch(List.of(1L, 2L));
        verify(fileMapper).deleteBatchIds(List.of(1L, 2L));
    }
}
