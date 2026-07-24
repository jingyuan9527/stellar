package com.stellar.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.dto.ShowcaseDTO;
import com.stellar.dto.ShowcaseQueryDTO;
import com.stellar.entity.SysShowcase;
import com.stellar.enums.OperationType;
import com.stellar.service.ShowcaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作品橱窗管理（管理后台，需登录）。
 */
@RestController
@RequestMapping("/showcase")
@RequiredArgsConstructor
public class ShowcaseController {

    private final ShowcaseService showcaseService;

    @GetMapping("/page")
    @Log(title = "作品橱窗", type = OperationType.QUERY)
    public Result<Page<SysShowcase>> page(@ModelAttribute ShowcaseQueryDTO query) {
        return Result.success(showcaseService.page(query));
    }

    @PostMapping
    @Log(title = "作品橱窗", type = OperationType.INSERT)
    public Result<Void> create(@RequestBody ShowcaseDTO dto) {
        showcaseService.create(dto);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Log(title = "作品橱窗", type = OperationType.UPDATE)
    public Result<Void> update(@PathVariable Long id, @RequestBody ShowcaseDTO dto) {
        showcaseService.update(id, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Log(title = "作品橱窗", type = OperationType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        showcaseService.delete(id);
        return Result.success();
    }
}
