package com.jiayuan.boot.system.workflow.service;

import com.jiayuan.boot.system.workflow.model.vo.ProcessDiagramResponseVO;

import java.util.Optional;

/**
 * 团队邀请流程服务接口。
 *
 * @author charleslam
 * @since 2026/05/20
 */
public interface TeamInvitationWorkflowService {

    /**
     * 启动团队邀请流程实例。
     *
     * @param invitationId 邀请ID
     * @param teamId       团队ID
     * @param inviterAccountId 邀请人账户ID
     * @param inviteeAccountId 被邀请人账户ID
     * @param targetRole   目标角色
     * @param expireSeconds  邀请有效期秒数
     * @return Flowable 流程实例ID
     */
    String startInvitation(Long invitationId, Long teamId, Long inviterAccountId, Long inviteeAccountId,
                           String targetRole, Long expireSeconds);

    /**
     * 查询流程实例中的被邀请人待办任务ID。
     *
     * @param processInstanceId 流程实例ID
     * @return 任务ID
     */
    Optional<String> findActiveInviteeTaskId(String processInstanceId);

    /**
     * 根据邀请ID查询被邀请人待办任务ID。
     *
     * @param invitationId 邀请ID
     * @return 任务ID
     */
    Optional<String> findActiveInviteeTaskIdByInvitationId(Long invitationId);

    /**
     * 完成被邀请人的审批任务。
     *
     * @param processInstanceId 流程实例ID
     * @param decision          审批决策，支持 ACCEPT 或 REJECT
     */
    void completeInvitation(String processInstanceId, String decision);

    /**
     * 取消团队邀请流程实例。
     *
     * @param processInstanceId 流程实例ID
     * @param reason            取消原因
     */
    void cancelInvitation(String processInstanceId, String reason);

    /**
     * 获取邀请流程图表数据。
     *
     * @param processInstanceId 流程实例ID
     * @return 流程图表响应
     */
    ProcessDiagramResponseVO getProcessDiagram(String processInstanceId);
}
