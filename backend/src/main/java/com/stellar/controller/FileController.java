package com.stellar.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.BusinessException;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.dto.SysFileQueryDTO;
import com.stellar.entity.SysFile;
import com.stellar.enums.OperationType;
import com.stellar.service.FileService;
import com.stellar.vo.SysFileVO;
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

/**
 * 文件管理：上传需登录、读取对游客公开（GET /file/{id} 标 @PublicAccess），
 * 列表/删除需登录。二进制存库 sys_file，无磁盘依赖。
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    @Log(title = "文件上传", type = OperationType.OTHER)
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.upload(file));
    }

    /**
     * 按 ID 获取文件二进制（游客可读，用于头像等公开图片展示）。
     * <p>命中 @PublicAccess 跳过登录校验；图片可长期缓存。
     */
    @PublicAccess
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        SysFile entity = fileService.getFull(id);
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

    /**
     * 文件分页（管理后台，不含二进制）。
     */
    @GetMapping("/page")
    @Log(title = "文件管理", type = OperationType.QUERY)
    public Result<Page<SysFileVO>> page(@ModelAttribute SysFileQueryDTO query) {
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
