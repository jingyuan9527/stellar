package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.BusinessException;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.entity.SysFile;
import com.stellar.enums.OperationType;
import com.stellar.mapper.SysFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 文件上传。登录方可上传，游客可读（GET /file/{id} 标 @PublicAccess）。
 * <p>文件二进制存数据库 sys_file 表，无磁盘依赖；仅允许图片类型，防可执行文件上传。
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final SysFileMapper fileMapper;

    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico");

    @PostMapping("/upload")
    @Log(title = "文件上传", type = OperationType.OTHER)
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的文件类型，仅允许图片");
        }
        SysFile entity = new SysFile();
        entity.setOriginalName(original);
        entity.setExt(ext);
        entity.setContentType(file.getContentType());
        entity.setSize(file.getSize());
        try {
            entity.setData(file.getBytes());
        } catch (IOException e) {
            log.error("[文件上传] 读取字节失败 orig={} err={}", original, e.getMessage(), e);
            throw new BusinessException("文件读取失败");
        }
        entity.setCreateTime(LocalDateTime.now());
        fileMapper.insert(entity);
        String url = "/file/" + entity.getId();
        log.info("[文件上传] 成功 orig={} size={} -> {}", original, file.getSize(), url);
        return Result.success(url);
    }

    /**
     * 按 ID 获取文件二进制（游客可读，用于头像等公开图片展示）。
     * <p>命中 @PublicAccess 跳过登录校验；图片可长期缓存。
     */
    @PublicAccess
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        SysFile entity = fileMapper.selectFullById(id);
        if (entity == null || entity.getData() == null) {
            throw new BusinessException("文件不存在");
        }
        String contentType = entity.getContentType() != null
                ? entity.getContentType()
                : "application/octet-stream";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(entity.getData().length))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                .body(entity.getData());
    }
}
