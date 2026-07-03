package com.jiayuan.boot.system.team.service;

import com.jiayuan.boot.system.team.model.vo.InvitationActionRequestVO;
import com.jiayuan.boot.system.team.model.vo.InvitationActionResponseVO;
import com.jiayuan.boot.system.team.model.vo.InvitationResponseVO;
import com.jiayuan.boot.system.workflow.model.vo.ProcessDiagramResponseVO;

import java.util.List;

/**
 * 团队邀请服务接口。
 *
 * @author charleslam
 * @since 2026/05/20
 */
public interface TeamInvitationService {

    /**
     * 查询团队邀请列表。
     *
     * @param teamId 团队ID
     * @param status 邀请状态
     * @return 邀请响应列表
     */
    List<InvitationResponseVO> listTeamInvitations(Long teamId, String status);

    /**
     * 查询当前用户收到的团队邀请。
     *
     * @param status 邀请状态
     * @return 邀请响应列表
     */
    List<InvitationResponseVO> listMyInvitations(String status);

    /**
     * 处理团队邀请动作。
     *
     * @param teamId  团队ID
     * @param request 邀请动作请求
     * @return 邀请动作响应
     */
    InvitationActionResponseVO handleInvitationAction(Long teamId, InvitationActionRequestVO request);

    /**
     * 将团队待处理邀请标记为团队解散。
     *
     * @param teamId 团队ID
     */
    void markPendingAsTeamDissolved(Long teamId);

    /**
     * 获取邀请流程图表数据。
     *
     * @param invitationId 邀请ID
     * @return 流程图表响应
     */
    ProcessDiagramResponseVO getProcessDiagram(Long invitationId);
}
