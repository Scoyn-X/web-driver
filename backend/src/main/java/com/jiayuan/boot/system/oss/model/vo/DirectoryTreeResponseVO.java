package com.jiayuan.boot.system.oss.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 目录树节点响应对象
 *
 * @author didongchen
 * @since 2026/04/16
 */
@Data
@Schema(description = "目录树节点响应")
public class DirectoryTreeResponseVO {

    @Schema(description = "目录ID", example = "1")
    private Long id;

    @Schema(description = "目录名称", example = "我的文档")
    private String name;

    @Schema(description = "父目录ID（0表示根目录）", example = "0")
    private Long parentId;

    @Schema(description = "子目录列表")
    private List<DirectoryTreeResponseVO> children;

}
