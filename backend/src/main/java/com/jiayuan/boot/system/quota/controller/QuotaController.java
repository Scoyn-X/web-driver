package com.jiayuan.boot.system.quota.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;
import com.jiayuan.boot.system.quota.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 配额管理控制器
 *
 * @author didongchen
 * @since 2026/04/13
 */
@Tag(name = "配额接口")
@RestController
@RequestMapping("/api/v1/quota")
@RequiredArgsConstructor
public class QuotaController {

    private final QuotaService quotaService;

    @GetMapping
    @Operation(summary = "获取当前用户配额信息")
    public Result<QuotaResponseVO> getQuotaInfo() {
        return Result.success(quotaService.getQuotaInfo());
    }

}
