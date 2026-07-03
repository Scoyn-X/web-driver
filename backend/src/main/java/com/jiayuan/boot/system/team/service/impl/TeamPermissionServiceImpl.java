package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.quota.service.impl.QuotaServiceImpl;
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
import com.jiayuan.boot.system.team.model.enums.MemberRole;
import com.jiayuan.boot.system.team.model.enums.MemberStatus;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import com.jiayuan.boot.system.team.model.vo.PermissionResponseVO;
import com.jiayuan.boot.system.team.model.vo.RoleOptionResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamPermissionResponseVO;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 团队权限校验服务实现
 * <p>
 * 权限点从数据库 team_role_permission 加载，不硬编码在枚举中。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TeamPermissionServiceImpl implements TeamPermissionService, ApplicationRunner {

    static final String ACTIVE_TEAMS_KEY = "team:permission:active-teams";
    static final String SYSTEM_PERMISSIONS_KEY = "team:permission:system-permissions";
    static final String MEMBER_KEY_PREFIX = "team:permission:members:";
    static final String MEMBER_PERMISSIONS_KEY_PREFIX = "team:permission:member-permissions:";
    private static final String PERMISSION_SEPARATOR = ",";

    private final TeamSpaceMapper teamSpaceMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamRoleMapper teamRoleMapper;
    private final TeamPermissionMapper teamPermissionMapper;
    private final TeamRolePermissionMapper teamRolePermissionMapper;
    private final TeamPermissionConverter teamPermissionConverter;
    private final QuotaService quotaService;
    private final StringRedisTemplate stringRedisTemplate;

    /** 角色 -> 权限点集合 本地缓存 */
    private volatile Map<String, Set<String>> rolePermissionCache = new ConcurrentHashMap<>();

    /**
     * 应用启动后加载团队权限缓存。
     */
    @Override
    public void run(ApplicationArguments args) {
        loadPermissionCacheOnStartup();
    }

    /**
     * 启动时从数据库批量加载团队权限到 Redis。
     */
    private void loadPermissionCacheOnStartup() {
        try {
            reloadPermissionCache();
        } catch (DataAccessException ex) {
            log.warn("团队权限缓存启动加载失败，跳过本次加载：{}", ex.getMessage());
        }
    }

    /**
     * 校验团队最小权限点。
     */
    @Override
    public void checkPermission(Long teamId, Long accountId, String permission) {
        PermissionSnapshot snapshot = readPermissionSnapshot(teamId, accountId, permission);
        if (snapshot.shouldReload()) {
            reloadPermissionCache();
            snapshot = readPermissionSnapshot(teamId, accountId, permission);
        }
        if (PermissionFailure.MEMBER_MISSING == snapshot.failure) {
            throw new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "您不是该团队成员");
        }
        if (!snapshot.isGranted()) {
            throw new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "缺少团队权限：" + permission);
        }
    }

    /**
     * 列出系统权限点定义。
     */
    @Override
    public List<PermissionResponseVO> listPermissions() {
        return teamPermissionMapper.selectList(null)
                .stream()
                .sorted(Comparator.comparing(TeamPermission::getPermission))
                .map(teamPermissionConverter::toPermissionResponseVO)
                .toList();
    }

    /**
     * 列出团队角色选项。
     */
    @Override
    public List<RoleOptionResponseVO> listRoles() {
        Map<Long, String> permissionNames = teamPermissionMapper.selectList(null)
                .stream()
                .collect(Collectors.toMap(TeamPermission::getId, TeamPermission::getPermission));
        Map<Long, List<String>> permissionsByRole = teamRolePermissionMapper.selectList(null)
                .stream()
                .collect(Collectors.groupingBy(
                        TeamRolePermission::getRoleId,
                        Collectors.mapping(rolePermission -> permissionNames.get(rolePermission.getPermissionId()),
                                Collectors.filtering(permission -> permission != null, Collectors.toList()))));
        Map<String, TeamRole> rolesByCode = teamRoleMapper.selectList(null)
                .stream()
                .collect(Collectors.toMap(TeamRole::getRole, role -> role, (left, right) -> left));
        return List.of(MemberRole.Owner, MemberRole.Admin, MemberRole.Editor, MemberRole.Viewer)
                .stream()
                .map(role -> rolesByCode.get(role.getValue()))
                .filter(role -> role != null)
                .map(role -> teamPermissionConverter.toRoleOptionResponseVO(
                        role, permissionsByRole.getOrDefault(role.getId(), Collections.emptyList())))
                .toList();
    }

    /**
     * 获取当前用户在团队内的权限信息。
     */
    @Override
    public TeamPermissionResponseVO getTeamPermissions(Long teamId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        TeamSpace team = teamSpaceMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队不存在");
        }
        TeamMember member = teamMemberMapper.selectActiveMemberByAccount(teamId, currentAccountId);
        if (member == null) {
            throw new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "您不是该团队成员");
        }
        boolean vip = quotaService.isVip(currentUserId);
        List<String> permissions = readCachedMemberPermissions(teamId, currentAccountId);
        if (permissions == null) {
            reloadPermissionCache();
            permissions = readCachedMemberPermissions(teamId, currentAccountId);
        }
        if (permissions == null) {
            permissions = rolePermissionCache.getOrDefault(member.getRole(), Collections.emptySet())
                    .stream()
                    .sorted()
                    .toList();
        }
        return teamPermissionConverter.toTeamPermissionResponseVO(TeamPermissionBuildBO.of(
                team,
                member,
                permissions,
                team.getUsedSpace() <= team.getTotalQuota() ? "NORMAL" : "OVER_LIMIT",
                vip ? "VIP" : "NORMAL",
                vip ? null : QuotaServiceImpl.NORMAL_SINGLE_FILE_LIMIT,
                !vip));
    }

    /**
     * 重新加载团队最小权限缓存。
     */
    @Override
    public void reloadPermissionCache() {
        clearExistingMemberCache();
        stringRedisTemplate.delete(SYSTEM_PERMISSIONS_KEY);
        stringRedisTemplate.delete(ACTIVE_TEAMS_KEY);
        loadRolePermissionCache();

        List<TeamMember> activeMembers = teamMemberMapper.selectActiveMembersInActiveTeams();
        String[] activeTeamIds = activeMembers.stream()
                .map(TeamMember::getTeamId)
                .distinct()
                .map(String::valueOf)
                .toArray(String[]::new);
        if (activeTeamIds.length > 0) {
            stringRedisTemplate.opsForSet().add(ACTIVE_TEAMS_KEY, activeTeamIds);
        }
        Map<Long, Map<String, String>> membersByTeam = activeMembers
                .stream()
                .collect(Collectors.groupingBy(
                        TeamMember::getTeamId,
                        Collectors.toMap(
                                member -> String.valueOf(member.getAccountId()),
                                TeamMember::getRole,
                                (left, right) -> right,
                                HashMap::new)));
        membersByTeam.forEach((teamId, members) -> stringRedisTemplate.opsForHash().putAll(memberKey(teamId), members));

        Map<Long, Map<String, String>> permissionsByTeam = activeMembers
                .stream()
                .collect(Collectors.groupingBy(
                        TeamMember::getTeamId,
                        Collectors.toMap(
                                member -> String.valueOf(member.getAccountId()),
                                member -> joinPermissions(rolePermissionCache.getOrDefault(
                                        member.getRole(), Collections.emptySet())),
                                (left, right) -> right,
                                HashMap::new)));
        permissionsByTeam.forEach((teamId, permissions) ->
                stringRedisTemplate.opsForHash().putAll(memberPermissionsKey(teamId), permissions));
    }

    /**
     * 从数据库加载角色-权限点映射到本地缓存。
     */
    private void loadRolePermissionCache() {
        List<TeamRole> roles = teamRoleMapper.selectList(null);
        List<TeamPermission> permissions = teamPermissionMapper.selectList(null);
        List<TeamRolePermission> rolePermissions = teamRolePermissionMapper.selectList(null);
        cacheSystemPermissions(permissions);

        Map<Long, String> permIdNameMap = permissions.stream()
                .collect(Collectors.toMap(TeamPermission::getId, TeamPermission::getPermission));

        Map<String, Set<String>> cache = new HashMap<>();
        for (TeamRole role : roles) {
            Set<String> permNames = rolePermissions.stream()
                    .filter(rp -> rp.getRoleId().equals(role.getId()))
                    .map(rp -> permIdNameMap.get(rp.getPermissionId()))
                    .filter(name -> name != null)
                    .collect(Collectors.toSet());
            cache.put(role.getRole(), permNames);
        }
        rolePermissionCache = cache;
        log.info("角色权限缓存已加载，角色数：{}", cache.size());
    }

    /**
     * 清理旧团队成员权限缓存。
     */
    private void clearExistingMemberCache() {
        deleteMatchingKeys(MEMBER_KEY_PREFIX + "*");
        deleteMatchingKeys(MEMBER_PERMISSIONS_KEY_PREFIX + "*");
    }

    /**
     * 按 Redis key pattern 删除已有缓存。
     */
    private void deleteMatchingKeys(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        keys.forEach(stringRedisTemplate::delete);
    }

    /**
     * 缓存系统中的全部团队权限点。
     */
    private void cacheSystemPermissions(List<TeamPermission> permissions) {
        String[] permissionCodes = permissions.stream()
                .map(TeamPermission::getPermission)
                .filter(permission -> permission != null && !permission.isBlank())
                .sorted()
                .toArray(String[]::new);
        if (permissionCodes.length > 0) {
            stringRedisTemplate.opsForSet().add(SYSTEM_PERMISSIONS_KEY, permissionCodes);
        }
    }

    /**
     * 从缓存读取一次团队权限快照。
     */
    private PermissionSnapshot readPermissionSnapshot(Long teamId, Long accountId, String permission) {
        Boolean active = stringRedisTemplate.opsForSet().isMember(ACTIVE_TEAMS_KEY, String.valueOf(teamId));
        if (!Boolean.TRUE.equals(active)) {
            return PermissionSnapshot.denied(PermissionFailure.MEMBER_MISSING);
        }
        Object role = stringRedisTemplate.opsForHash().get(memberKey(teamId), String.valueOf(accountId));
        if (role == null) {
            return PermissionSnapshot.denied(PermissionFailure.MEMBER_MISSING);
        }
        List<String> permissions = readCachedMemberPermissions(teamId, accountId);
        if (permissions == null) {
            return PermissionSnapshot.denied(PermissionFailure.ROLE_CACHE_MISSING);
        }
        return permissions.contains(permission)
                ? PermissionSnapshot.granted()
                : PermissionSnapshot.denied(PermissionFailure.PERMISSION_MISSING);
    }

    /**
     * 从 Redis 读取成员权限点。
     */
    private List<String> readCachedMemberPermissions(Long teamId, Long userId) {
        Object permissions = stringRedisTemplate.opsForHash()
                .get(memberPermissionsKey(teamId), String.valueOf(userId));
        if (permissions == null) {
            return null;
        }
        return parsePermissions(permissions.toString())
                .stream()
                .sorted()
                .toList();
    }

    /**
     * 构造团队成员权限缓存键。
     */
    private String memberKey(Long teamId) {
        return memberKey(String.valueOf(teamId));
    }

    /**
     * 构造团队成员权限缓存键。
     */
    private String memberKey(String teamId) {
        return MEMBER_KEY_PREFIX + teamId;
    }

    /**
     * 构造成员权限点缓存键。
     */
    private String memberPermissionsKey(Long teamId) {
        return MEMBER_PERMISSIONS_KEY_PREFIX + teamId;
    }

    /**
     * 序列化权限点集合，保持稳定顺序便于排查 Redis 内容。
     */
    private String joinPermissions(Set<String> permissions) {
        return permissions.stream()
                .sorted()
                .collect(Collectors.joining(PERMISSION_SEPARATOR));
    }

    /**
     * 解析 Redis 中的权限点集合。
     */
    private Set<String> parsePermissions(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return Collections.emptySet();
        }
        return Set.of(permissions.split(PERMISSION_SEPARATOR));
    }

    private enum PermissionFailure {
        MEMBER_MISSING,
        ROLE_CACHE_MISSING,
        PERMISSION_MISSING
    }

    private static final class PermissionSnapshot {

        private final PermissionFailure failure;

        private PermissionSnapshot(PermissionFailure failure) {
            this.failure = failure;
        }

        static PermissionSnapshot granted() {
            return new PermissionSnapshot(null);
        }

        static PermissionSnapshot denied(PermissionFailure failure) {
            return new PermissionSnapshot(failure);
        }

        boolean isGranted() {
            return failure == null;
        }

        boolean shouldReload() {
            return PermissionFailure.MEMBER_MISSING == failure
                    || PermissionFailure.ROLE_CACHE_MISSING == failure;
        }
    }
}
