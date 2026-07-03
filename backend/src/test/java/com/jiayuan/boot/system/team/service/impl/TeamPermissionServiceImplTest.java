package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.converter.TeamPermissionConverter;
import com.jiayuan.boot.system.team.mapper.TeamMemberMapper;
import com.jiayuan.boot.system.team.mapper.TeamPermissionMapper;
import com.jiayuan.boot.system.team.mapper.TeamRoleMapper;
import com.jiayuan.boot.system.team.mapper.TeamRolePermissionMapper;
import com.jiayuan.boot.system.team.mapper.TeamSpaceMapper;
import com.jiayuan.boot.system.team.model.bo.TeamPermissionBuildBO;
import com.jiayuan.boot.system.team.model.entity.TeamMember;
import com.jiayuan.boot.system.team.model.entity.TeamPermission;
import com.jiayuan.boot.system.team.model.entity.TeamRole;
import com.jiayuan.boot.system.team.model.entity.TeamRolePermission;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import com.jiayuan.boot.system.team.model.enums.MemberRole;
import com.jiayuan.boot.system.team.model.vo.PermissionResponseVO;
import com.jiayuan.boot.system.team.model.vo.RoleOptionResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamPermissionResponseVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamPermissionServiceImpl 单元测试")
class TeamPermissionServiceImplTest {

    private static final String ACTIVE_TEAMS_KEY = "team:permission:active-teams";
    private static final String SYSTEM_PERMISSIONS_KEY = "team:permission:system-permissions";
    private static final String MEMBER_KEY = "team:permission:members:1";
    private static final String MEMBER_PERMISSIONS_KEY = "team:permission:member-permissions:1";
    private static final Long ACCOUNT_ID = 200L;

    @Mock private TeamSpaceMapper teamSpaceMapper;
    @Mock private TeamMemberMapper teamMemberMapper;
    @Mock private TeamRoleMapper teamRoleMapper;
    @Mock private TeamPermissionMapper teamPermissionMapper;
    @Mock private TeamRolePermissionMapper teamRolePermissionMapper;
    @Mock private TeamPermissionConverter teamPermissionConverter;
    @Mock private QuotaService quotaService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private SetOperations<String, String> setOperations;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private TeamPermissionServiceImpl teamPermissionService;

    @Test
    @DisplayName("Editor 允许上传文件")
    void checkPermission_editorUploadAllowed() {
        mockRedisRole(MemberRole.Editor.name());

        teamPermissionService.checkPermission(1L, ACCOUNT_ID, "file:upload");
        verifyNoInteractions(teamSpaceMapper, teamMemberMapper);
    }

    @Test
    @DisplayName("Viewer 不允许上传文件")
    void checkPermission_viewerUploadDenied() {
        mockRedisRole(MemberRole.Viewer.name());

        assertThatThrownBy(() -> teamPermissionService.checkPermission(1L, ACCOUNT_ID, "file:upload"))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NO_PERMISSION_TO_USE_API);
        verifyNoInteractions(teamSpaceMapper, teamMemberMapper);
    }

    @Test
    @DisplayName("团队已解散：拒绝访问")
    void checkPermission_dissolvedTeamDenied() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(ACTIVE_TEAMS_KEY, "1")).thenReturn(false, false);
        when(stringRedisTemplate.keys("team:permission:members:*")).thenReturn(Set.of());
        when(stringRedisTemplate.keys("team:permission:member-permissions:*")).thenReturn(Set.of());
        when(teamRoleMapper.selectList(null)).thenReturn(List.of());
        when(teamPermissionMapper.selectList(null)).thenReturn(List.of());
        when(teamRolePermissionMapper.selectList(null)).thenReturn(List.of());
        when(teamMemberMapper.selectActiveMembersInActiveTeams()).thenReturn(List.of());

        assertThatThrownBy(() -> teamPermissionService.checkPermission(1L, ACCOUNT_ID, "file:list"))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NO_PERMISSION_TO_USE_API);
        verify(stringRedisTemplate).delete(ACTIVE_TEAMS_KEY);
    }

    @Test
    @DisplayName("成员角色缓存缺失：重新加载后允许访问")
    void checkPermission_activeTeamCacheMissReloadsAndAllows() {
        TeamMember member = buildMember(MemberRole.Editor.name());
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.isMember(ACTIVE_TEAMS_KEY, "1")).thenReturn(false, true);
        when(hashOperations.get(MEMBER_KEY, String.valueOf(ACCOUNT_ID))).thenReturn(MemberRole.Editor.name());
        when(hashOperations.get(MEMBER_PERMISSIONS_KEY, String.valueOf(ACCOUNT_ID))).thenReturn("file:upload");
        when(stringRedisTemplate.keys("team:permission:members:*")).thenReturn(Set.of());
        when(stringRedisTemplate.keys("team:permission:member-permissions:*")).thenReturn(Set.of());
        when(teamRoleMapper.selectList(null)).thenReturn(List.of(role(1L, MemberRole.Editor.name())));
        when(teamPermissionMapper.selectList(null)).thenReturn(List.of(permission(11L, "file:upload")));
        when(teamRolePermissionMapper.selectList(null)).thenReturn(List.of(rolePermission(1L, 11L)));
        when(teamMemberMapper.selectActiveMembersInActiveTeams()).thenReturn(List.of(member));

        teamPermissionService.checkPermission(1L, ACCOUNT_ID, "file:upload");

        verify(stringRedisTemplate).delete(ACTIVE_TEAMS_KEY);
        verify(setOperations).add(SYSTEM_PERMISSIONS_KEY, "file:upload");
        verify(hashOperations).putAll(MEMBER_KEY, Map.of(String.valueOf(ACCOUNT_ID), MemberRole.Editor.name()));
        verify(hashOperations).putAll(MEMBER_PERMISSIONS_KEY, Map.of(String.valueOf(ACCOUNT_ID), "file:upload"));
    }

    @Test
    @DisplayName("成员权限点缓存缺失：重新加载后允许访问")
    void checkPermission_memberCacheMissReloadsAndAllows() {
        TeamMember member = buildMember(MemberRole.Editor.name());
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.isMember(ACTIVE_TEAMS_KEY, "1")).thenReturn(true, true);
        when(hashOperations.get(MEMBER_KEY, String.valueOf(ACCOUNT_ID))).thenReturn(MemberRole.Editor.name());
        when(hashOperations.get(MEMBER_PERMISSIONS_KEY, String.valueOf(ACCOUNT_ID))).thenReturn(null, "file:upload");
        when(stringRedisTemplate.keys("team:permission:members:*")).thenReturn(Set.of(MEMBER_KEY));
        when(stringRedisTemplate.keys("team:permission:member-permissions:*")).thenReturn(Set.of(MEMBER_PERMISSIONS_KEY));
        when(teamRoleMapper.selectList(null)).thenReturn(List.of(role(1L, MemberRole.Editor.name())));
        when(teamPermissionMapper.selectList(null)).thenReturn(List.of(permission(11L, "file:upload")));
        when(teamRolePermissionMapper.selectList(null)).thenReturn(List.of(rolePermission(1L, 11L)));
        when(teamMemberMapper.selectActiveMembersInActiveTeams()).thenReturn(List.of(member));

        teamPermissionService.checkPermission(1L, ACCOUNT_ID, "file:upload");

        verify(stringRedisTemplate).delete(MEMBER_KEY);
        verify(stringRedisTemplate).delete(MEMBER_PERMISSIONS_KEY);
        verify(hashOperations).putAll(MEMBER_KEY, Map.of(String.valueOf(ACCOUNT_ID), MemberRole.Editor.name()));
        verify(hashOperations).putAll(MEMBER_PERMISSIONS_KEY, Map.of(String.valueOf(ACCOUNT_ID), "file:upload"));
    }

    @Test
    @DisplayName("成员不存在：拒绝访问")
    void checkPermission_missingMemberDenied() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(setOperations.isMember(ACTIVE_TEAMS_KEY, "1")).thenReturn(true, true);
        when(hashOperations.get(MEMBER_KEY, String.valueOf(ACCOUNT_ID))).thenReturn(null).thenReturn(null);
        when(stringRedisTemplate.keys("team:permission:members:*")).thenReturn(Set.of(MEMBER_KEY));
        when(stringRedisTemplate.keys("team:permission:member-permissions:*")).thenReturn(Set.of(MEMBER_PERMISSIONS_KEY));
        when(teamRoleMapper.selectList(null)).thenReturn(List.of());
        when(teamPermissionMapper.selectList(null)).thenReturn(List.of());
        when(teamRolePermissionMapper.selectList(null)).thenReturn(List.of());
        when(teamMemberMapper.selectActiveMembersInActiveTeams()).thenReturn(List.of());

        assertThatThrownBy(() -> teamPermissionService.checkPermission(1L, ACCOUNT_ID, "file:list"))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NO_PERMISSION_TO_USE_API);
        verify(stringRedisTemplate).delete(MEMBER_KEY);
        verify(stringRedisTemplate).delete(MEMBER_PERMISSIONS_KEY);
    }

    @Test
    @DisplayName("重新加载权限缓存：写入系统权限点、成员角色和成员权限点")
    void reloadPermissionCache_writesRedisCache() {
        TeamMember member = buildMember(MemberRole.Editor.name());
        TeamPermission list = permission(10L, "file:list");
        TeamPermission upload = permission(11L, "file:upload");
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.keys("team:permission:members:*"))
                .thenReturn(Set.of("team:permission:members:9"));
        when(stringRedisTemplate.keys("team:permission:member-permissions:*"))
                .thenReturn(Set.of("team:permission:member-permissions:9"));
        when(teamRoleMapper.selectList(null)).thenReturn(List.of(role(1L, MemberRole.Editor.name())));
        when(teamPermissionMapper.selectList(null)).thenReturn(List.of(upload, list));
        when(teamRolePermissionMapper.selectList(null)).thenReturn(List.of(
                rolePermission(1L, 10L),
                rolePermission(1L, 11L)));
        when(teamMemberMapper.selectActiveMembersInActiveTeams()).thenReturn(List.of(member));

        teamPermissionService.reloadPermissionCache();

        verify(stringRedisTemplate).delete("team:permission:members:9");
        verify(stringRedisTemplate).delete("team:permission:member-permissions:9");
        verify(stringRedisTemplate).delete(ACTIVE_TEAMS_KEY);
        verify(setOperations).add(SYSTEM_PERMISSIONS_KEY, "file:list", "file:upload");
        verify(setOperations).add(ACTIVE_TEAMS_KEY, "1");
        verify(hashOperations).putAll(MEMBER_KEY, Map.of(String.valueOf(ACCOUNT_ID), MemberRole.Editor.name()));
        verify(hashOperations).putAll(MEMBER_PERMISSIONS_KEY,
                Map.of(String.valueOf(ACCOUNT_ID), "file:list,file:upload"));
    }

    @Test
    @DisplayName("列出系统权限点：按权限点编码排序并转换")
    void listPermissions_mapsDefinitions() {
        TeamPermission upload = permission(11L, "file:upload");
        TeamPermission list = permission(12L, "file:list");
        PermissionResponseVO listResponse = mock(PermissionResponseVO.class);
        PermissionResponseVO uploadResponse = mock(PermissionResponseVO.class);
        when(teamPermissionMapper.selectList(null)).thenReturn(List.of(upload, list));
        when(teamPermissionConverter.toPermissionResponseVO(list)).thenReturn(listResponse);
        when(teamPermissionConverter.toPermissionResponseVO(upload)).thenReturn(uploadResponse);

        assertThat(teamPermissionService.listPermissions()).containsExactly(listResponse, uploadResponse);
    }

    @Test
    @DisplayName("列出系统角色：按固定角色顺序返回权限点")
    void listRoles_mapsRoleOptions() {
        TeamRole owner = role(1L, MemberRole.Owner.name());
        TeamRole editor = role(2L, MemberRole.Editor.name());
        TeamPermission list = permission(11L, "file:list");
        TeamPermission upload = permission(12L, "file:upload");
        RoleOptionResponseVO ownerResponse = mock(RoleOptionResponseVO.class);
        RoleOptionResponseVO editorResponse = mock(RoleOptionResponseVO.class);
        when(teamPermissionMapper.selectList(null)).thenReturn(List.of(list, upload));
        when(teamRolePermissionMapper.selectList(null)).thenReturn(List.of(
                rolePermission(1L, 11L),
                rolePermission(2L, 11L),
                rolePermission(2L, 12L)));
        when(teamRoleMapper.selectList(null)).thenReturn(List.of(editor, owner));
        when(teamPermissionConverter.toRoleOptionResponseVO(owner, List.of("file:list")))
                .thenReturn(ownerResponse);
        when(teamPermissionConverter.toRoleOptionResponseVO(editor, List.of("file:list", "file:upload")))
                .thenReturn(editorResponse);

        assertThat(teamPermissionService.listRoles()).containsExactly(ownerResponse, editorResponse);
    }

    @Test
    @DisplayName("获取团队权限：从 Redis 成员权限点聚合权限和 VIP 限制")
    void getTeamPermissions_buildsCurrentUserPermissions() {
        TeamSpace team = mock(TeamSpace.class);
        TeamMember member = mock(TeamMember.class);
        TeamPermissionResponseVO response = mock(TeamPermissionResponseVO.class);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(MEMBER_PERMISSIONS_KEY, String.valueOf(ACCOUNT_ID)))
                .thenReturn("file:list,file:upload");
        when(teamSpaceMapper.selectById(1L)).thenReturn(team);
        when(team.getUsedSpace()).thenReturn(10L);
        when(team.getTotalQuota()).thenReturn(100L);
        when(teamMemberMapper.selectActiveMemberByAccount(1L, ACCOUNT_ID)).thenReturn(member);
        when(quotaService.isVip(2L)).thenReturn(false);
        when(teamPermissionConverter.toTeamPermissionResponseVO(any(TeamPermissionBuildBO.class)))
                .thenReturn(response);
        ReflectionTestUtils.setField(teamPermissionService, "rolePermissionCache",
                Map.of(MemberRole.Editor.name(), Set.of("file:list")));

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ACCOUNT_ID);

            assertThat(teamPermissionService.getTeamPermissions(1L)).isSameAs(response);
        }

        ArgumentCaptor<TeamPermissionBuildBO> captor = ArgumentCaptor.forClass(TeamPermissionBuildBO.class);
        verify(teamPermissionConverter).toTeamPermissionResponseVO(captor.capture());
        assertThat(captor.getValue().getPermissions()).containsExactly("file:list", "file:upload");
        assertThat(captor.getValue().getQuotaState()).isEqualTo("NORMAL");
        assertThat(captor.getValue().getVipState()).isEqualTo("NORMAL");
        assertThat(captor.getValue().getDownloadLimited()).isTrue();
    }

    @Test
    @DisplayName("获取团队权限：同一用户多账户时使用当前账户角色")
    void getTeamPermissions_usesCurrentAccountRole() {
        TeamSpace team = mock(TeamSpace.class);
        TeamMember accountMember = mock(TeamMember.class);
        TeamPermissionResponseVO response = mock(TeamPermissionResponseVO.class);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(MEMBER_PERMISSIONS_KEY, String.valueOf(ACCOUNT_ID)))
                .thenReturn("file:list,file:download");
        when(teamSpaceMapper.selectById(1L)).thenReturn(team);
        when(team.getUsedSpace()).thenReturn(10L);
        when(team.getTotalQuota()).thenReturn(100L);
        when(teamMemberMapper.selectActiveMemberByAccount(1L, ACCOUNT_ID)).thenReturn(accountMember);
        when(quotaService.isVip(2L)).thenReturn(true);
        when(teamPermissionConverter.toTeamPermissionResponseVO(any(TeamPermissionBuildBO.class)))
                .thenReturn(response);
        ReflectionTestUtils.setField(teamPermissionService, "rolePermissionCache",
                Map.of(MemberRole.Viewer.name(), Set.of("file:list", "file:download")));

        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(2L);
            securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ACCOUNT_ID);

            assertThat(teamPermissionService.getTeamPermissions(1L)).isSameAs(response);
        }

        verify(teamMemberMapper).selectActiveMemberByAccount(1L, ACCOUNT_ID);
        ArgumentCaptor<TeamPermissionBuildBO> captor = ArgumentCaptor.forClass(TeamPermissionBuildBO.class);
        verify(teamPermissionConverter).toTeamPermissionResponseVO(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(accountMember);
        assertThat(captor.getValue().getPermissions()).containsExactly("file:download", "file:list");
    }

    private void mockRedisRole(String role) {
        ReflectionTestUtils.setField(teamPermissionService, "rolePermissionCache", Map.of(
                MemberRole.Editor.name(), Set.of("file:list", "file:upload"),
                MemberRole.Viewer.name(), Set.of("file:list")));
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(ACTIVE_TEAMS_KEY, "1")).thenReturn(true);
        when(hashOperations.get(MEMBER_KEY, String.valueOf(ACCOUNT_ID))).thenReturn(role);
        when(hashOperations.get(MEMBER_PERMISSIONS_KEY, String.valueOf(ACCOUNT_ID)))
                .thenReturn(MemberRole.Editor.name().equals(role) ? "file:list,file:upload" : "file:list");
    }

    private TeamMember buildMember(String role) {
        TeamMember member = new TeamMember();
        member.setTeamId(1L);
        member.setUserId(2L);
        member.setAccountId(ACCOUNT_ID);
        member.setRole(role);
        return member;
    }

    private TeamRole role(Long id, String roleCode) {
        TeamRole role = new TeamRole();
        role.setId(id);
        role.setRole(roleCode);
        return role;
    }

    private TeamPermission permission(Long id, String permissionCode) {
        TeamPermission permission = new TeamPermission();
        permission.setId(id);
        permission.setPermission(permissionCode);
        return permission;
    }

    private TeamRolePermission rolePermission(Long roleId, Long permissionId) {
        TeamRolePermission rolePermission = new TeamRolePermission();
        rolePermission.setRoleId(roleId);
        rolePermission.setPermissionId(permissionId);
        return rolePermission;
    }
}
