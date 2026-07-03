package com.jiayuan.boot.system.admin.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.admin.model.vo.SystemConfigUpdateRequestVO;
import com.jiayuan.boot.system.admin.model.vo.SystemConfigResponseVO;
import com.jiayuan.boot.system.admin.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置管理控制器。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Tag(name = "系统配置接口")
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping("/config")
    @Operation(summary = "获取系统配置")
    public Result<SystemConfigResponseVO> getConfig() {
        return Result.success(systemConfigService.getConfig());
    }

    @PutMapping("/config")
    @Operation(summary = "修改系统配置")
    public Result<SystemConfigResponseVO> updateConfig(@Valid @RequestBody SystemConfigUpdateRequestVO request) {
        return Result.success(systemConfigService.updateConfig(request));
    }

    @PostMapping("/cleanup/trigger")
    @Operation(summary = "触发回收站清理")
    public Result<String> triggerCleanup() {
        return Result.success(systemConfigService.triggerCleanup());
    }
}
