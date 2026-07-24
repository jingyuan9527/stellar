package com.stellar.controller;

import com.stellar.annotation.Log;
import com.stellar.common.BusinessException;
import com.stellar.common.Result;
import com.stellar.enums.OperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * 本地文件上传。登录方可上传，游客可读（/uploads/** 已在拦截器放行）。
 * <p>存磁盘目录，文件名 UUID 防重名；仅允许图片类型，防可执行文件上传。
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

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
        String fileName = java.util.UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName).normalize();
            if (!target.startsWith(dir)) {
                throw new BusinessException("非法文件路径");
            }
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("[文件上传] 存盘失败 orig={} err={}", original, e.getMessage(), e);
            throw new BusinessException("文件存储失败");
        }
        String url = "/uploads/" + fileName;
        log.info("[文件上传] 成功 orig={} size={} -> {}", original, file.getSize(), url);
        return Result.success(url);
    }
}
