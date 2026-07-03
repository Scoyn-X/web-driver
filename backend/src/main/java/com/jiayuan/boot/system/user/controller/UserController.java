package com.jiayuan.boot.system.user.controller;

import com.jiayuan.boot.common.result.Result;
import com.jiayuan.boot.system.user.model.vo.CurrentUserResponseVO;
import com.jiayuan.boot.system.user.model.vo.UserBriefResponseVO;
import com.jiayuan.boot.system.user.model.vo.UserVipResponseVO;
import com.jiayuan.boot.system.user.model.vo.VipUpdateRequestVO;
import com.jiayuan.boot.system.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户与 VIP 控制器
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "获取当前用户")
    public Result<CurrentUserResponseVO> getCurrentUser() {
        return Result.success(userService.getCurrentUser());
    }

    @GetMapping("/search")
    @Operation(summary = "搜索用户")
    public Result<List<UserBriefResponseVO>> searchUsers(@RequestParam String keyword) {
        return Result.success(userService.searchUsers(keyword));
    }

    @PutMapping("/{id}/vip")
    @Operation(summary = "切换VIP状态")
    public Result<UserVipResponseVO> updateVip(@PathVariable Long id,
                                               @Valid @RequestBody VipUpdateRequestVO request) {
        return Result.success(userService.updateVip(id, request));
    }
}
