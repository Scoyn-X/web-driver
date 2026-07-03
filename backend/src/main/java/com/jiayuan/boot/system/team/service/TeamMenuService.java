package com.jiayuan.boot.system.team.service;

import com.jiayuan.boot.system.team.model.vo.MenuTreeResponseVO;

/**
 * 团队菜单服务接口。
 *
 * @author charleslam
 * @since 2026/05/22
 */
public interface TeamMenuService {

    /**
     * 获取当前用户全局菜单。
     *
     * @return 菜单树
     */
    MenuTreeResponseVO listMyMenus();

    /**
     * 获取团队上下文菜单。
     *
     * @param teamId 团队ID
     * @return 菜单树
     */
    MenuTreeResponseVO listTeamMenus(Long teamId);
}
