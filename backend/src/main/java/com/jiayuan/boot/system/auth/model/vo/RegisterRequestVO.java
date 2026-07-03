package com.jiayuan.boot.system.auth.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户注册请求VO
 *
 * @author didongchen
 * @since 2026/04/10
 */
@Data
@Schema(description = "用户注册请求")
public class RegisterRequestVO {

    @Schema(description = "用户昵称", example = "张三")
    @NotBlank(message = "用户昵称不能为空")
    private String nickname;

    @Schema(description = "账户名", example = "test_user")
    @NotBlank(message = "账户名不能为空")
    private String accountName;

    @Schema(description = "密码", example = "test1234")
    @NotBlank(message = "密码不能为空")
    private String password;

    @Schema(description = "邮箱", example = "test@example.com")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "账户类型", example = "personal", allowableValues = {"personal", "work", "team"})
    @NotBlank(message = "账户类型不能为空")
    private String accountType;

}
