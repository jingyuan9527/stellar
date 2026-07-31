package com.stellar.system.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.stellar.annotation.Log;
import com.stellar.common.Result;
import com.stellar.system.dto.SysLogQueryDTO;
import com.stellar.system.entity.SysLog;
import com.stellar.enums.OperationType;
import com.stellar.system.service.SysLogService;
import com.stellar.system.vo.SysLogExportVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/log")
@RequiredArgsConstructor
public class SysLogController {

    private final SysLogService sysLogService;

    @GetMapping("/page")
    @Log(title = "日志管理", type = OperationType.QUERY)
    public Result<Page<SysLog>> page(@ModelAttribute SysLogQueryDTO query) {
        return Result.success(sysLogService.page(query));
    }

    @GetMapping("/{id}")
    @Log(title = "日志管理", type = OperationType.QUERY)
    public Result<SysLog> detail(@PathVariable Long id) {
        return Result.success(sysLogService.getById(id));
    }

    @GetMapping("/export")
    @Log(title = "日志管理", type = OperationType.EXPORT)
    public void export(@ModelAttribute SysLogQueryDTO query, HttpServletResponse response) {
        try {
            List<SysLog> list = sysLogService.list(query);
            List<SysLogExportVO> exportList = list.stream().map(SysLogExportVO::of).collect(Collectors.toList());
            String fileName = URLEncoder.encode("操作日志", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + fileName + ".xlsx");
            EasyExcel.write(response.getOutputStream(), SysLogExportVO.class)
                    .sheet("操作日志")
                    .doWrite(exportList);
        } catch (Exception e) {
            log.error("导出操作日志失败: {}", e.getMessage(), e);
        }
    }
}
