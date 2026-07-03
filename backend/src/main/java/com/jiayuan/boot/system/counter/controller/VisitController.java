package com.jiayuan.boot.system.counter.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.counter.service.VisitService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 访问计数控制层
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Tag(name = "访问计数接口")
@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @GetMapping
    @Operation(summary = "获取访问次数")
    public Result<Integer> getVisitCount() {
        return Result.success(visitService.getCount());
    }

    @PostMapping
    @Operation(summary = "增加访问次数")
    public Result<Integer> incrementVisitCount() {
        return Result.success(visitService.incrementAndGet());
    }

    @DeleteMapping
    @Operation(summary = "重置访问次数")
    public Result<Void> resetVisitCount() {
        visitService.reset();
        return Result.success();
    }

}
