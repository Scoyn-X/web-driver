package com.jiayuan.boot.system.workflow.delegate;

import com.jiayuan.boot.system.workflow.service.TeamInvitationExpireHandler;
import com.jiayuan.boot.system.workflow.util.FlowableVariableUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import static com.jiayuan.boot.system.workflow.service.impl.FlowableTeamInvitationWorkflowService.VAR_INVITATION_ID;

/**
 * 团队邀请过期流程委托。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Slf4j
@Component("teamInvitationExpireDelegate")
@RequiredArgsConstructor
public class TeamInvitationExpireDelegate implements JavaDelegate {

    private final ObjectProvider<TeamInvitationExpireHandler> expireHandlerProvider;

    /**
     * 处理 Flowable 边界定时器回调。
     */
    @Override
    public void execute(DelegateExecution execution) {
        Long invitationId = FlowableVariableUtils.getLong(execution, VAR_INVITATION_ID);
        if (invitationId == null) {
            log.warn("团队邀请过期回调缺少邀请ID processInstanceId={}", execution.getProcessInstanceId());
            return;
        }
        TeamInvitationExpireHandler handler = expireHandlerProvider.getIfAvailable();
        if (handler == null) {
            log.warn("团队邀请过期回调缺少业务处理器 invitationId={} processInstanceId={}",
                    invitationId, execution.getProcessInstanceId());
            return;
        }
        handler.expireInvitation(invitationId, execution.getProcessInstanceId());
    }
}
