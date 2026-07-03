package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.system.team.model.vo.MenuNodeResponseVO;
import com.jiayuan.boot.system.team.model.vo.MenuTreeResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamPermissionResponseVO;
import com.jiayuan.boot.system.team.service.TeamMenuService;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 团队菜单服务实现。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Service
@RequiredArgsConstructor
public class TeamMenuServiceImpl implements TeamMenuService {

    private static final long TEAM_ROOT_MENU_ID = 1000L;

    private final TeamPermissionService teamPermissionService;

    /**
     * 查询当前登录账户可见的全局菜单。
     */
    @Override
    public MenuTreeResponseVO listMyMenus() {
        return new MenuTreeResponseVO(List.of(
                node(100L, null, "文件管理", "/files", "FileManagement", "Folder", 10, List.of()),
                node(200L, null, "我的团队", "/teams", "TeamList", "UserFilled", 20, List.of()),
                node(300L, null, "我的邀请", "/invitations", "TeamInvitations", "Message", 30, List.of()),
                node(400L, null, "个人中心", "/profile", "UserProfile", "User", 40, List.of()),
                node(500L, null, "私密空间", "/private", "PrivateSpace", "Lock", 50, List.of()),
                node(600L, null, "访问计数", "/counter", "VisitCounter", "DataLine", 60, List.of())
        ));
    }

    /**
     * 查询当前账户在指定团队内可见的团队菜单。
     */
    @Override
    public MenuTreeResponseVO listTeamMenus(Long teamId) {
        TeamPermissionResponseVO permission = teamPermissionService.getTeamPermissions(teamId);
        Set<String> permissions = new HashSet<>(permission.getPermissions() == null
                ? List.of()
                : permission.getPermissions());

        List<MenuNodeResponseVO> children = new ArrayList<>();
        if (permissions.contains("file:list")) {
            children.add(node(1001L, TEAM_ROOT_MENU_ID, "团队文件",
                    "/teams/" + teamId, "TeamFiles", "Folder", 10, List.of()));
            children.add(node(1002L, TEAM_ROOT_MENU_ID, "成员管理",
                    "/teams/" + teamId, "TeamMembers", "User", 20, List.of()));
            children.add(node(1006L, TEAM_ROOT_MENU_ID, "团队配额",
                    "/teams/" + teamId, "TeamQuota", "PieChart", 60, List.of()));
        }
        if (permissions.contains(TeamPermissionService.MEMBER_INVITE_PERMISSION)) {
            children.add(node(1003L, TEAM_ROOT_MENU_ID, "团队邀请",
                    "/teams/" + teamId, "TeamInvitations", "Message", 30, List.of()));
        }
        if (permissions.contains("share:manage")) {
            children.add(node(1004L, TEAM_ROOT_MENU_ID, "团队分享",
                    "/teams/" + teamId, "TeamShares", "Share2", 40, List.of()));
        }
        if (permissions.contains("trash:list")) {
            children.add(node(1005L, TEAM_ROOT_MENU_ID, "团队回收站",
                    "/teams/" + teamId, "TeamTrash", "Delete", 50, List.of()));
        }

        if (children.isEmpty()) {
            return new MenuTreeResponseVO(List.of());
        }
        return new MenuTreeResponseVO(List.of(node(
                TEAM_ROOT_MENU_ID, null, "团队空间", "/teams/" + teamId,
                "TeamLayout", "UserFilled", 10, children)));
    }

    private MenuNodeResponseVO node(Long id, Long parentId, String title, String path,
                                    String componentKey, String icon, Integer sort,
                                    List<MenuNodeResponseVO> children) {
        return new MenuNodeResponseVO(id, parentId, title, path, componentKey, icon, sort, children);
    }
}
