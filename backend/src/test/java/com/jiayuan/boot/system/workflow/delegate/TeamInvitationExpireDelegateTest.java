package com.jiayuan.boot.system.workflow.delegate;

import com.jiayuan.boot.system.workflow.service.TeamInvitationExpireHandler;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 团队邀请过期委托测试
 *
 * @author charleslam
 * @since 2026/05/20
 */
@ExtendWith(MockitoExtension.class)
class TeamInvitationExpireDelegateTest {

    @Mock
    private ObjectProvider<TeamInvitationExpireHandler> expireHandlerProvider;

    @Mock
    private TeamInvitationExpireHandler expireHandler;

    @Mock
    private DelegateExecution execution;

    @InjectMocks
    private TeamInvitationExpireDelegate delegate;

    @Test
    void executeShouldCallDomainExpirationHookWithParsedInvitationId() {
        when(execution.getVariable("invitationId")).thenReturn("10");
        when(execution.getProcessInstanceId()).thenReturn("process-1");
        when(expireHandlerProvider.getIfAvailable()).thenReturn(expireHandler);

        delegate.execute(execution);

        verify(expireHandler).expireInvitation(10L, "process-1");
    }

    @Test
    void executeShouldSkipWhenInvitationIdMissing() {
        when(execution.getVariable("invitationId")).thenReturn(null);

        delegate.execute(execution);

        verifyNoInteractions(expireHandler);
    }

    @Test
    void executeShouldSkipWhenHandlerUnavailable() {
        when(execution.getVariable("invitationId")).thenReturn(10L);
        when(execution.getProcessInstanceId()).thenReturn("process-2");
        when(expireHandlerProvider.getIfAvailable()).thenReturn(null);

        delegate.execute(execution);

        verifyNoInteractions(expireHandler);
    }
}
