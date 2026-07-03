package com.jiayuan.boot.system.team.service;

import com.jiayuan.boot.system.team.model.vo.MemberRoleUpdateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamMemberResponseVO;
import com.jiayuan.boot.system.team.model.vo.TransferOwnerRequestVO;

import java.util.List;

/**
 * 团队成员管理服务接口
 *
 * @author didongchen
 * @since 2026/05/20
 */
public interface MemberService {

    /**
     * 从团队中移除指定成员。
     *
     * @param teamId 团队ID
     * @param memberId 目标成员记录ID
     */
    void removeMember(Long teamId, Long memberId);

    /**
     * 当前用户主动退出团队，Owner 不能直接退出。
     *
     * @param teamId 团队ID
     */
    void exitTeam(Long teamId);

    /**
     * 修改团队成员的默认角色。
     *
     * @param teamId  团队ID
     * @param memberId 目标成员记录ID
     * @param request 新角色请求
     * @return 更新后的成员信息
     */
    TeamMemberResponseVO updateMemberRole(Long teamId, Long memberId, MemberRoleUpdateRequestVO request);

    /**
     * Owner 转让团队所有权，仅 Owner 可操作。
     *
     * @param teamId  团队ID
     * @param request 转让请求
     */
    void transferOwner(Long teamId, TransferOwnerRequestVO request);

    /**
     * 列出团队成员。
     *
     * @param teamId 团队ID
     * @return 成员列表
     */
    List<TeamMemberResponseVO> listMembers(Long teamId);
}
