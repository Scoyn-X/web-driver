package com.jiayuan.boot.system.oss.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 文件列表响应信封：含面包屑路径 + 当前目录条目。
 * <p>
 * 替代原裸 {@code List<FileInfoResponseVO>} 响应，让前端在一次请求中同时拿到「当前位置面包屑」
 * 与「子项列表」，避免二次请求与状态漂移。
 *
 * @author charleslam
 * @since 2026/04/16
 */
@Data
public class FileListResponseVO {

    @Schema(description = "从根到当前目录的面包屑（按层级顺序）")
    private List<BreadcrumbItemResponseVO> breadcrumb;

    @Schema(description = "当前目录下的文件与子目录混合列表")
    private List<FileInfoResponseVO> items;

}
