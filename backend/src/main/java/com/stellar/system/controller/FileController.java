package com.stellar.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.BusinessException;
import com.stellar.common.Result;
import com.stellar.common.ResultCode;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.system.dto.SysFileQueryDTO;
import com.stellar.system.entity.SysFile;
import com.stellar.enums.OperationType;
import com.stellar.system.service.FileService;
import com.stellar.system.vo.SysFileVO;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;

/**
 * 文件管理：上传需登录（默认私有）；GET /file/{id} 免登录但仅 is_public 文件游客可读，
 * 私有文件仅上传者本人可读（防匿名枚举 id 下载）。列表/删除需登录。二进制存库 sys_file。
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    @Log(title = "文件上传", type = OperationType.OTHER)
    public Result<String> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "isPublic", required = false, defaultValue = "false")
                                 boolean isPublic) {
        return Result.success(fileService.upload(file, isPublic));
    }

    /**
     * 按 ID 获取文件二进制。公开文件（头像/海螺预设等）游客可读且可长缓存；
     * 私有文件仅上传者本人可读，防 IDOR 枚举下载。
     * <p>命中 @PublicAccess 跳过登录校验，可见性在方法内校验。
     */
    @PublicAccess
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        SysFile entity = fileService.getFull(id);
        if (entity == null || entity.getData() == null) {
            throw new BusinessException("文件不存在");
        }
        boolean publicReadable = Boolean.TRUE.equals(entity.getIsPublic());
        if (!publicReadable
                && (!StpUtil.isLogin()
                    || !Objects.equals(StpUtil.getLoginIdAsLong(), entity.getUserId()))) {
            log.warn("[文件下载] 越权尝试 id={} requester={} owner={}",
                    id, StpUtil.getLoginIdDefaultNull(), entity.getUserId());
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        String contentType = entity.getContentType() != null
                ? entity.getContentType()
                : "application/octet-stream";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(entity.getData().length))
                // 公开可共享缓存；私有仅浏览器本地缓存，避免代理层泄露
                .header(HttpHeaders.CACHE_CONTROL, publicReadable ? "public, max-age=604800" : "private, max-age=3600")
                .body(entity.getData());
    }

    /**
     * 文件分页（管理后台，不含二进制）。
     */
    @GetMapping("/page")
    @Log(title = "文件管理", type = OperationType.QUERY)
    public Result<Page<SysFileVO>> page(@Valid @ModelAttribute SysFileQueryDTO query) {
        return Result.success(fileService.page(query));
    }

    /**
     * 硬删除单条（引用方需自行承担悬空风险）。
     */
    @DeleteMapping("/{id}")
    @Log(title = "文件管理", type = OperationType.DELETE)
    public Result<Void> remove(@PathVariable Long id) {
        fileService.remove(id);
        return Result.success();
    }

    /**
     * 批量硬删除。
     */
    @DeleteMapping("/batch")
    @Log(title = "文件管理", type = OperationType.DELETE)
    public Result<Void> removeBatch(@RequestBody List<Long> ids) {
        fileService.removeBatch(ids);
        return Result.success();
    }
}
