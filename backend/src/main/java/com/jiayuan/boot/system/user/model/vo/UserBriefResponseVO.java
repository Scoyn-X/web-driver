package com.jiayuan.boot.system.user.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户基础信息响应对象
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
public class UserBriefResponseVO {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "账户ID")
    private Long accountId;

    @Schema(description = "主账户名")
    private String accountName;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "邮箱")
    private String email;
}
