package com.jiayuan.boot.system.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiayuan.boot.system.team.model.entity.TeamMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 团队成员 Mapper 接口
 *
 * @author didongchen
 * @since 2026/05/14
 */
@Mapper
public interface TeamMemberMapper extends BaseMapper<TeamMember> {

    /**
     * 查询正常团队下的有效成员。
     *
     * @return 有效成员列表
     */
    List<TeamMember> selectActiveMembersInActiveTeams();

    /**
     * 查询当前账户在团队中的有效成员记录。
     *
     * @param teamId    团队ID
     * @param accountId 账户ID
     * @return 有效成员记录
     */
    TeamMember selectActiveMemberByAccount(@Param("teamId") Long teamId,
                                           @Param("accountId") Long accountId);

    /**
     * 查询指定团队的有效成员。
     *
     * @param teamId 团队ID
     * @return 有效成员列表
     */
    List<TeamMember> selectActiveMembersByTeam(Long teamId);

    /**
     * 查询指定账户加入的有效团队成员记录。
     *
     * @param accountId 账户ID
     * @return 有效成员列表
     */
    List<TeamMember> selectActiveMembershipsByAccount(Long accountId);

    /**
     * 接受邀请时新增或恢复成员记录。
     *
     * @param member 成员记录
     * @return 影响行数
     */
    int upsertAcceptedMember(TeamMember member);

    /**
     * 更新有效成员状态。
     *
     * @param teamId    团队ID
     * @param accountId 账户ID
     * @param status    目标状态
     * @return 影响行数
     */
    int updateActiveMemberStatus(@Param("teamId") Long teamId,
                                 @Param("accountId") Long accountId,
                                 @Param("status") String status);

    /**
     * 更新有效成员角色。
     *
     * @param teamId    团队ID
     * @param accountId 账户ID
     * @param role      目标角色
     * @return 影响行数
     */
    int updateActiveMemberRole(@Param("teamId") Long teamId,
                               @Param("accountId") Long accountId,
                               @Param("role") String role);

    /**
     * 批量更新团队有效成员状态。
     *
     * @param teamId 团队ID
     * @param status 目标状态
     * @return 影响行数
     */
    int updateActiveMembersStatusByTeam(@Param("teamId") Long teamId,
                                        @Param("status") String status);

}
