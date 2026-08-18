package com.stellar.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.common.BusinessException;
import com.stellar.ai.dto.AiDocumentAddDTO;
import com.stellar.ai.dto.AiKnowledgeBaseDTO;
import com.stellar.ai.entity.AiKnowledgeBase;
import com.stellar.ai.entity.AiKnowledgeChunk;
import com.stellar.ai.service.AiKnowledgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link AiKnowledgeController} 单测：KB CRUD/分块/删除/重建透传；文档上传的扩展名白名单与空文件分支。
 */
@ExtendWith(MockitoExtension.class)
class AiKnowledgeControllerTest {

    @Mock
    AiKnowledgeService knowledgeService;

    AiKnowledgeController controller;

    @BeforeEach
    void setup() {
        controller = new AiKnowledgeController(knowledgeService);
    }

    @Test
    void list_正常() {
        when(knowledgeService.listAll()).thenReturn(List.of(new AiKnowledgeBase()));
        assertEquals(1, controller.list().getData().size());
    }

    @Test
    void create_正常() {
        AiKnowledgeBaseDTO dto = new AiKnowledgeBaseDTO();
        controller.create(dto);
        verify(knowledgeService).createKb(dto);
    }

    @Test
    void update_正常() {
        AiKnowledgeBaseDTO dto = new AiKnowledgeBaseDTO();
        controller.update(dto);
        verify(knowledgeService).updateKb(dto);
    }

    @Test
    void delete_正常() {
        controller.delete(1L);
        verify(knowledgeService).deleteKb(1L);
    }

    @Test
    void pageChunks_正常() {
        when(knowledgeService.pageChunks(1L, 1, 10)).thenReturn(new Page<AiKnowledgeChunk>());
        assertNotNull(controller.pageChunks(1L, 1, 10).getData());
    }

    @Test
    void addDocument_正常() {
        AiDocumentAddDTO dto = new AiDocumentAddDTO();
        dto.setText("内容");
        dto.setSourceName("src");
        when(knowledgeService.addDocument(1L, "内容", "src")).thenReturn(3);
        assertEquals(3, controller.addDocument(1L, dto).getData());
    }

    @Test
    void uploadDocument_空文件_返回0() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);
        assertEquals(0, controller.uploadDocument(1L, empty).getData());
        verify(knowledgeService, never()).addDocument(anyLong(), anyString(), anyString());
    }

    @Test
    void uploadDocument_支持txt() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        when(knowledgeService.addDocument(1L, "hello", "a.txt")).thenReturn(1);
        assertEquals(1, controller.uploadDocument(1L, f).getData());
    }

    @Test
    void uploadDocument_支持md与markdown() throws Exception {
        MockMultipartFile md = new MockMultipartFile("file", "b.md", "text/markdown", "hi".getBytes());
        when(knowledgeService.addDocument(1L, "hi", "b.md")).thenReturn(1);
        assertEquals(1, controller.uploadDocument(1L, md).getData());

        MockMultipartFile mk = new MockMultipartFile("file", "c.markdown", "text/markdown", "yo".getBytes());
        when(knowledgeService.addDocument(1L, "yo", "c.markdown")).thenReturn(1);
        assertEquals(1, controller.uploadDocument(1L, mk).getData());
    }

    @Test
    void uploadDocument_扩展名大写_归一化() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "a.TXT", "text/plain", "hi".getBytes());
        when(knowledgeService.addDocument(1L, "hi", "a.TXT")).thenReturn(1);
        assertEquals(1, controller.uploadDocument(1L, f).getData());
    }

    @Test
    void uploadDocument_不支持扩展名_抛BusinessException() {
        MockMultipartFile f = new MockMultipartFile("file", "a.pdf", "application/pdf", "x".getBytes());
        assertThrows(BusinessException.class, () -> controller.uploadDocument(1L, f));
        verify(knowledgeService, never()).addDocument(anyLong(), anyString(), anyString());
    }

    @Test
    void uploadDocument_无扩展名_抛BusinessException() {
        MockMultipartFile f = new MockMultipartFile("file", "readme", "text/plain", "x".getBytes());
        assertThrows(BusinessException.class, () -> controller.uploadDocument(1L, f));
    }

    @Test
    void updateDocument_正常() {
        AiDocumentAddDTO dto = new AiDocumentAddDTO();
        dto.setText("新内容");
        dto.setSourceName("src");
        when(knowledgeService.updateDocument(1L, "src", "新内容")).thenReturn(4);
        assertEquals(4, controller.updateDocument(1L, dto).getData());
    }

    @Test
    void listSources_正常() {
        when(knowledgeService.listSources(1L)).thenReturn(List.of("a.md", "b.md"));
        assertEquals(2, controller.listSources(1L).getData().size());
    }

    @Test
    void deleteChunk_正常() {
        controller.deleteChunk(1L);
        verify(knowledgeService).deleteChunk(1L);
    }

    @Test
    void rebuild_正常() {
        controller.rebuild(1L);
        verify(knowledgeService).rebuild(1L);
    }
}
