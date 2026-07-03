package com.jiayuan.boot.system.workflow.service;

/**
 * 团队邀请过期业务回调。
 *
 * @author charleslam
 * @since 2026/05/20
 */
public interface TeamInvitationExpireHandler {

    /**
     * 处理 Flowable 边界定时器触发的邀请过期事件。
     *
     * @param invitationId      邀请ID
     * @param processInstanceId 流程实例ID
     */
    void expireInvitation(Long invitationId, String processInstanceId);
}
