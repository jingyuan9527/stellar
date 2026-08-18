package com.stellar.ai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.ai.dto.AiDocumentAddDTO;
import com.stellar.ai.dto.AiKnowledgeBaseDTO;
import com.stellar.ai.entity.AiKnowledgeBase;
import com.stellar.ai.entity.AiKnowledgeChunk;
import com.stellar.enums.OperationType;
import com.stellar.ai.service.AiKnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * AI 知识库管理：KB CRUD、文档分块(文本/文件)、重建索引、分块查看删除。
 * <p>RAG 检索接口仅供后端聊天服务内部调用，不对外暴露。
 */
@Slf4j
@RestController
@RequestMapping("/ai/knowledge")
@RequiredArgsConstructor
public class AiKnowledgeController {

    private final AiKnowledgeService knowledgeService;

    private static final Set<String> ALLOWED_EXTS = Set.of("txt", "md", "markdown");

    @GetMapping
    @Log(title = "知识库", type = OperationType.QUERY)
    public Result<List<AiKnowledgeBase>> list() {
        return Result.success(knowledgeService.listAll());
    }

    @PostMapping
    @Log(title = "知识库", type = OperationType.INSERT)
    public Result<Void> create(@Valid @RequestBody AiKnowledgeBaseDTO dto) {
        knowledgeService.createKb(dto);
        return Result.success();
    }

    @PutMapping
    @Log(title = "知识库", type = OperationType.UPDATE)
    public Result<Void> update(@Valid @RequestBody AiKnowledgeBaseDTO dto) {
        knowledgeService.updateKb(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(title = "知识库", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.deleteKb(id);
        return Result.success();
    }

    @GetMapping("/{kbId}/chunk")
    @Log(title = "知识库分块", type = OperationType.QUERY)
    public Result<Page<AiKnowledgeChunk>> pageChunks(@PathVariable Long kbId,
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(knowledgeService.pageChunks(kbId, pageNum, pageSize));
    }

    /**
     * 添加纯文本文档：分块 + 向量化。
     */
    @PostMapping("/{kbId}/document")
    @Log(title = "知识库文档", type = OperationType.INSERT)
    public Result<Integer> addDocument(@PathVariable Long kbId,
                                       @Valid @RequestBody AiDocumentAddDTO dto) {
        return Result.success(knowledgeService.addDocument(kbId, dto.getText(), dto.getSourceName()));
    }

    /**
     * 上传 txt/md 文件添加文档：读取文本后分块 + 向量化。
     */
    @PostMapping("/{kbId}/document/file")
    @Log(title = "知识库文档", type = OperationType.INSERT)
    public Result<Integer> uploadDocument(@PathVariable Long kbId,
                                           @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return Result.success(0);
        }
        String original = file.getOriginalFilename();
        String ext = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!ALLOWED_EXTS.contains(ext)) {
            throw new com.stellar.common.BusinessException("仅支持 txt/md 文件");
        }
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        return Result.success(knowledgeService.addDocument(kbId, text, original));
    }

    /**
     * 更新文档：按来源名替换全部旧分块并重新向量化（解决"改文档只能删了重加"）。
     */
    @PutMapping("/{kbId}/document")
    @Log(title = "知识库文档", type = OperationType.UPDATE)
    public Result<Integer> updateDocument(@PathVariable Long kbId,
                                          @Valid @RequestBody AiDocumentAddDTO dto) {
        return Result.success(knowledgeService.updateDocument(kbId, dto.getSourceName(), dto.getText()));
    }

    /** 该知识库全部文档来源名（更新文档下拉用）。 */
    @GetMapping("/{kbId}/sources")
    @Log(title = "知识库", type = OperationType.QUERY)
    public Result<List<String>> listSources(@PathVariable Long kbId) {
        return Result.success(knowledgeService.listSources(kbId));
    }

    @DeleteMapping("/chunk/{id}")
    @Log(title = "知识库分块", type = OperationType.DELETE)
    public Result<Void> deleteChunk(@PathVariable Long id) {
        knowledgeService.deleteChunk(id);
        return Result.success();
    }

    /**
     * 重建索引：对所有分块重新向量化。
     */
    @PutMapping("/{kbId}/rebuild")
    @Log(title = "知识库重建", type = OperationType.OTHER)
    public Result<Void> rebuild(@PathVariable Long kbId) {
        knowledgeService.rebuild(kbId);
        return Result.success();
    }
}
