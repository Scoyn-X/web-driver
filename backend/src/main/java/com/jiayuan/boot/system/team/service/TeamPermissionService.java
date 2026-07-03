package com.jiayuan.boot.system.team.service;

import com.jiayuan.boot.system.team.model.vo.PermissionResponseVO;
import com.jiayuan.boot.system.team.model.vo.RoleOptionResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamPermissionResponseVO;

import java.util.List;

/**
 * 团队权限校验服务接口
 *
 * @author charleslam
 * @since 2026/05/16
 */
public interface TeamPermissionService {

    /** 邀请团队成员权限点 */
    String MEMBER_INVITE_PERMISSION = "member:invite";

    /**
     * 校验用户是否拥有团队权限点。
     *
     * @param teamId     团队ID
     * @param accountId  账户ID
     * @param permission 权限点
     */
    void checkPermission(Long teamId, Long accountId, String permission);

    /**
     * 列出系统权限点定义。
     *
     * @return 系统权限点列表
     */
    List<PermissionResponseVO> listPermissions();

    /**
     * 列出团队角色选项。
     *
     * @return 团队角色选项列表
     */
    List<RoleOptionResponseVO> listRoles();

    /**
     * 获取当前用户在团队内的权限信息。
     *
     * @param teamId 团队ID
     * @return 团队权限信息
     */
    TeamPermissionResponseVO getTeamPermissions(Long teamId);

    /**
     * 重新加载团队权限缓存。
     */
    void reloadPermissionCache();
}
