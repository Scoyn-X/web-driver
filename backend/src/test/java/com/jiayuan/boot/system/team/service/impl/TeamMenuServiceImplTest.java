package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.system.team.model.vo.MenuTreeResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamPermissionResponseVO;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 团队菜单服务测试。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamMenuServiceImpl 单元测试")
class TeamMenuServiceImplTest {

    @Mock
    private TeamPermissionService teamPermissionService;

    @InjectMocks
    private TeamMenuServiceImpl teamMenuService;

    @Test
    @DisplayName("全局菜单：返回前端兼容路径")
    void listMyMenus_returnsFrontendCompatiblePaths() {
        MenuTreeResponseVO response = teamMenuService.listMyMenus();

        assertThat(response.getMenus())
                .extracting("path")
                .containsExactly("/files", "/teams", "/invitations", "/profile", "/private", "/counter");
    }

    @Test
    @DisplayName("团队菜单：按当前用户权限过滤入口")
    void listTeamMenus_filtersByPermissions() {
        TeamPermissionResponseVO permissions = new TeamPermissionResponseVO();
        permissions.setPermissions(List.of("file:list", "share:manage"));
        when(teamPermissionService.getTeamPermissions(9L)).thenReturn(permissions);

        MenuTreeResponseVO response = teamMenuService.listTeamMenus(9L);

        assertThat(response.getMenus()).hasSize(1);
        assertThat(response.getMenus().get(0).getPath()).isEqualTo("/teams/9");
        assertThat(response.getMenus().get(0).getChildren())
                .extracting("componentKey")
                .containsExactly("TeamFiles", "TeamMembers", "TeamQuota", "TeamShares");
    }

    @Test
    @DisplayName("团队菜单：无权限时不返回团队入口")
    void listTeamMenus_emptyPermissionsReturnsEmptyTree() {
        TeamPermissionResponseVO permissions = new TeamPermissionResponseVO();
        permissions.setPermissions(null);
        when(teamPermissionService.getTeamPermissions(9L)).thenReturn(permissions);

        MenuTreeResponseVO response = teamMenuService.listTeamMenus(9L);

        assertThat(response.getMenus()).isEmpty();
    }

    @Test
    @DisplayName("团队菜单：完整权限返回邀请、分享、回收站等全部入口")
    void listTeamMenus_fullPermissionsReturnsAllTeamEntries() {
        TeamPermissionResponseVO permissions = new TeamPermissionResponseVO();
        permissions.setPermissions(List.of(
                "file:list",
                TeamPermissionService.MEMBER_INVITE_PERMISSION,
                "share:manage",
                "trash:list"));
        when(teamPermissionService.getTeamPermissions(9L)).thenReturn(permissions);

        MenuTreeResponseVO response = teamMenuService.listTeamMenus(9L);

        assertThat(response.getMenus()).hasSize(1);
        assertThat(response.getMenus().get(0).getChildren())
                .extracting("componentKey")
                .containsExactly("TeamFiles", "TeamMembers", "TeamQuota",
                        "TeamInvitations", "TeamShares", "TeamTrash");
    }
}
