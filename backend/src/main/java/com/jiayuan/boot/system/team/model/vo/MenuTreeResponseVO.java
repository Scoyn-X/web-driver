package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 菜单树响应VO。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "菜单树响应")
public class MenuTreeResponseVO {

    @Schema(description = "菜单节点列表")
    private List<MenuNodeResponseVO> menus;
}
