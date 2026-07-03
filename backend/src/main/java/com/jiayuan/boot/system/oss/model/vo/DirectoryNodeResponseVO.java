package com.jiayuan.boot.system.oss.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 目录树节点视图对象（用于左侧目录树懒加载）。
 * <p>
 * 仅包含目录展示必需字段；不含文件、不含层级路径——
 * 客户端通过多次按 {@code parentId} 调 {@code GET /api/v1/directories} 拼装树。
 *
 * @author charleslam
 * @since 2026/04/16
 */
@Data
@Schema(description = "目录树节点视图对象")
public class DirectoryNodeResponseVO {

    @Schema(description = "目录ID", example = "1")
    private Long id;

    @Schema(description = "目录名称", example = "我的文档")
    private String name;

    @Schema(description = "父目录ID（0表示根目录）", example = "0")
    private Long parentId;

    @Schema(description = "是否包含子目录", example = "true")
    private Boolean hasChildren;

}
