package com.jiayuan.boot.system.auth.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户登录响应VO
 *
 * @author didongchen
 * @since 2026/04/10
 */
@Data
@AllArgsConstructor
@Schema(description = "用户登录响应")
public class LoginResponseVO {

    @Schema(description = "JWT Token")
    private String token;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "账户ID")
    private Long accountId;

    @Schema(description = "用户昵称")
    private String nickname;

    @Schema(description = "账户名")
    private String accountName;

}
