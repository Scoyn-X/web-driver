package com.jiayuan.boot.system.oss.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 目录路径面包屑节点。
 * <p>
 * 用于文件列表响应，让前端能渲染「根目录 / A / B」并支持点击跳转。
 * 根目录的 {@code id} 固定为 0，名称固定为「根目录」。
 *
 * @author charleslam
 * @since 2026/04/16
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "目录路径面包屑节点")
public class BreadcrumbItemResponseVO {

    @Schema(description = "目录ID（0表示根目录）", example = "0")
    private Long id;

    @Schema(description = "目录名称", example = "根目录")
    private String name;

}
