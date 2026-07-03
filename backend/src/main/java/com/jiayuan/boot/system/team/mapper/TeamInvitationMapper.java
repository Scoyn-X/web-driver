package com.jiayuan.boot.system.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiayuan.boot.system.team.model.entity.TeamInvitation;
import com.jiayuan.boot.system.team.model.vo.InvitationResponseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 团队邀请 Mapper 接口
 *
 * @author didongchen
 * @since 2026/05/14
 */
@Mapper
public interface TeamInvitationMapper extends BaseMapper<TeamInvitation> {

    /**
     * 查询团队邀请响应列表。
     *
     * @param teamId 团队ID
     * @param status 邀请状态
     * @return 邀请响应列表
     */
    List<InvitationResponseVO> selectTeamInvitationResponses(@Param("teamId") Long teamId,
                                                             @Param("status") String status);

    /**
     * 同步标记团队下已过期待处理邀请。
     *
     * @param teamId        团队ID
     * @param pendingStatus 待处理状态
     * @param expiredStatus 已过期状态
     * @param reason        过期原因
     * @param now           当前时间
     * @return 影响行数
     */
    int expirePendingInvitationsByTeam(@Param("teamId") Long teamId,
                                        @Param("pendingStatus") String pendingStatus,
                                        @Param("expiredStatus") String expiredStatus,
                                        @Param("reason") String reason,
                                        @Param("now") LocalDateTime now);

    /**
     * 查询账户收到的邀请响应列表。
     *
     * @param inviteeAccountId 被邀请人账户ID
     * @param status           邀请状态
     * @return 邀请响应列表
     */
    List<InvitationResponseVO> selectMyInvitationResponses(@Param("inviteeAccountId") Long inviteeAccountId,
                                                           @Param("status") String status);

    /**
     * 同步标记账户收到的已过期待处理邀请。
     *
     * @param inviteeAccountId 被邀请人账户ID
     * @param pendingStatus    待处理状态
     * @param expiredStatus    已过期状态
     * @param reason           过期原因
     * @param now              当前时间
     * @return 影响行数
     */
    int expirePendingInvitationsByInviteeAccount(@Param("inviteeAccountId") Long inviteeAccountId,
                                                 @Param("pendingStatus") String pendingStatus,
                                                 @Param("expiredStatus") String expiredStatus,
                                                 @Param("reason") String reason,
                                                 @Param("now") LocalDateTime now);

    /**
     * 查询单个邀请响应。
     *
     * @param id 邀请ID
     * @return 邀请响应
     */
    InvitationResponseVO selectInvitationResponseById(Long id);

    /**
     * 查询待处理邀请。
     *
     * @param teamId        团队ID
     * @param invitationId  邀请ID
     * @param pendingStatus 待处理状态
     * @return 待处理邀请
     */
    TeamInvitation selectPendingInvitation(@Param("teamId") Long teamId,
                                           @Param("invitationId") Long invitationId,
                                           @Param("pendingStatus") String pendingStatus);

    /**
     * 查询团队待处理邀请。
     *
     * @param teamId        团队ID
     * @param pendingStatus 待处理状态
     * @return 待处理邀请列表
     */
    List<TeamInvitation> selectPendingInvitationsByTeamId(@Param("teamId") Long teamId,
                                                          @Param("pendingStatus") String pendingStatus);

    /**
     * 判断是否存在未过期待处理邀请。
     *
     * @param teamId        团队ID
     * @param inviteeAccountId 被邀请人账户ID
     * @param pendingStatus 待处理状态
     * @param now           当前时间
     * @return 是否存在未过期待处理邀请
     */
    boolean existsActivePendingInvitation(@Param("teamId") Long teamId,
                                          @Param("inviteeAccountId") Long inviteeAccountId,
                                          @Param("pendingStatus") String pendingStatus,
                                          @Param("now") LocalDateTime now);

    /**
     * 判断账户是否为有效团队成员。
     *
     * @param teamId       团队ID
     * @param accountId    账户ID
     * @param activeStatus 有效状态
     * @return 是否为有效成员
     */
    boolean existsActiveMemberByAccount(@Param("teamId") Long teamId,
                                        @Param("accountId") Long accountId,
                                        @Param("activeStatus") String activeStatus);

    /**
     * 原子更新待处理邀请状态。
     *
     * @param id            邀请ID
     * @param pendingStatus 待处理状态
     * @param targetStatus  目标状态
     * @param reason        原因
     * @return 影响行数
     */
    int updatePendingInvitationStatus(@Param("id") Long id,
                                      @Param("pendingStatus") String pendingStatus,
                                      @Param("targetStatus") String targetStatus,
                                      @Param("reason") String reason);

    /**
     * 更新 Flowable 流程实例ID。
     *
     * @param id                 邀请ID
     * @param flowableInstanceId Flowable 流程实例ID
     * @return 影响行数
     */
    int updateFlowableInstanceId(@Param("id") Long id,
                                 @Param("flowableInstanceId") String flowableInstanceId);
}
