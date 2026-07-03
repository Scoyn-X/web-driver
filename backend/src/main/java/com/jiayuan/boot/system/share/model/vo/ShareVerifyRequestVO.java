package com.jiayuan.boot.system.share.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 校验分享提取码请求 VO
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Data
@Schema(description = "校验分享提取码请求")
public class ShareVerifyRequestVO {

    @Schema(description = "4-6 位字母数字提取码", example = "A3FK", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "提取码不能为空")
    private String extractCode;

}
