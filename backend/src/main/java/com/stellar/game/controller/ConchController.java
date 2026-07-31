package com.stellar.game.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.common.annotation.PublicAccess;
import com.stellar.common.annotation.RateLimit;
import com.stellar.game.dto.ConchAnswerDTO;
import com.stellar.game.dto.ConchAnswerQueryDTO;
import com.stellar.game.dto.ConchAskDTO;
import com.stellar.system.entity.SysFile;
import com.stellar.enums.OperationType;
import com.stellar.game.service.ConchService;
import com.stellar.game.vo.ConchAnswerVO;
import com.stellar.game.vo.ConchAskResultVO;
import com.stellar.game.vo.ConchRecordVO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 神奇海螺接口。
 * <p>提问与音频读取对游客开放（@PublicAccess + @RateLimit）；预设管理需登录。
 */
@Slf4j
@RestController
@RequestMapping("/game/conch")
@RequiredArgsConstructor
public class ConchController {

    private final ConchService conchService;

    /**
     * 提问：AI 语义匹配预设回答，返回文本 + 音频地址。对游客开放，IP 日限 10 次。
     */
    @PublicAccess
    @RateLimit(daily = 10)
    @PostMapping("/ask")
    @Log(title = "神奇海螺", type = OperationType.OTHER)
    public Result<ConchAskResultVO> ask(@Valid @RequestBody ConchAskDTO dto) {
        return Result.success(conchService.ask(dto));
    }

    /**
     * 按预设 ID 获取音频（公共墙，游客可试听，可长期缓存）。
     */
    @PublicAccess
    @GetMapping("/answer/{id}/audio")
    public ResponseEntity<byte[]> answerAudio(@PathVariable Long id) {
        SysFile file = conchService.getAnswerFile(id);
        String contentType = file.getContentType() != null
                ? file.getContentType() : "audio/mpeg";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=conch_" + id + "." + file.getExt())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.getData().length))
                .body(file.getData());
    }

    /**
     * 预设回答分页（管理后台）。
     */
    @GetMapping("/answer/page")
    @Log(title = "海螺预设", type = OperationType.QUERY)
    public Result<Page<ConchAnswerVO>> answerPage(@ModelAttribute ConchAnswerQueryDTO query) {
        return Result.success(conchService.answerPage(query));
    }

    /**
     * 新增预设回答（先上传音频拿 fileId，再调此接口）。
     */
    @PostMapping("/answer")
    @Log(title = "海螺预设", type = OperationType.INSERT)
    public Result<Void> createAnswer(@Valid @RequestBody ConchAnswerDTO dto) {
        conchService.createAnswer(dto);
        return Result.success();
    }

    /**
     * 编辑预设回答。
     */
    @PutMapping("/answer")
    @Log(title = "海螺预设", type = OperationType.UPDATE)
    public Result<Void> updateAnswer(@Valid @RequestBody ConchAnswerDTO dto) {
        conchService.updateAnswer(dto);
        return Result.success();
    }

    /**
     * 删除预设回答（逻辑删除）。
     */
    @DeleteMapping("/answer/{id}")
    @Log(title = "海螺预设", type = OperationType.DELETE)
    public Result<Void> deleteAnswer(@PathVariable Long id) {
        conchService.deleteAnswer(id);
        return Result.success();
    }

    /**
     * 切换预设启用状态。
     */
    @PutMapping("/answer/{id}/enabled")
    @Log(title = "海螺预设", type = OperationType.UPDATE)
    public Result<Void> toggleEnabled(@PathVariable Long id, @RequestParam Integer enabled) {
        conchService.toggleEnabled(id, enabled);
        return Result.success();
    }

    /**
     * 提问历史分页（管理后台）。
     */
    @GetMapping("/record/page")
    @Log(title = "海螺提问历史", type = OperationType.QUERY)
    public Result<Page<ConchRecordVO>> recordPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(conchService.recordPage(pageNum, pageSize));
    }
}
