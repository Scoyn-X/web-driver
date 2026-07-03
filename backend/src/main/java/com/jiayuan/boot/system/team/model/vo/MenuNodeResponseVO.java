package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 菜单节点响应VO。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单节点响应")
public class MenuNodeResponseVO {

    @Schema(description = "菜单节点ID", example = "101")
    private Long id;

    @Schema(description = "父菜单节点ID", example = "100")
    private Long parentId;

    @Schema(description = "菜单标题", example = "团队文件")
    private String title;

    @Schema(description = "前端路由路径", example = "/teams/1")
    private String path;

    @Schema(description = "前端组件标识", example = "TeamFiles")
    private String componentKey;

    @Schema(description = "菜单图标", example = "Folder")
    private String icon;

    @Schema(description = "排序值", example = "10")
    private Integer sort;

    @Schema(description = "子菜单节点")
    private List<MenuNodeResponseVO> children;
}
