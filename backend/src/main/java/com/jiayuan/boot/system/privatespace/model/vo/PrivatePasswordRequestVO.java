package com.jiayuan.boot.system.privatespace.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 私密空间密码请求。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
@Schema(description = "私密空间密码请求")
public class PrivatePasswordRequestVO {

    @Schema(description = "旧密码，修改密码时必填")
    private String oldPassword;

    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "私密空间密码不能为空")
    private String password;
}
