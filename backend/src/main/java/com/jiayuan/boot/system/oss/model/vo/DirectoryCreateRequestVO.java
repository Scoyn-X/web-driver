package com.jiayuan.boot.system.oss.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建目录请求VO
 *
 * @author didongchen
 * @since 2026/04/11
 */
@Data
@Schema(description = "创建目录请求")
public class DirectoryCreateRequestVO {

    @Schema(description = "目录名称", example = "我的文档")
    @NotBlank(message = "目录名称不能为空")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5a-zA-Z0-9_-]{1,50}$", message = "目录名只能包含中英文、数字、下划线和短横线，长度1-50")
    private String name;

    @Schema(description = "父目录ID（0表示根目录）", example = "0")
    private Long parentId = 0L;

}
