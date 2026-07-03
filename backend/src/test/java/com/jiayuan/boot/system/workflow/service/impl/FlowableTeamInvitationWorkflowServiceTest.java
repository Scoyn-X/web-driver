package com.jiayuan.boot.system.workflow.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.team.mapper.TeamInvitationMapper;
import com.jiayuan.boot.system.team.model.entity.TeamInvitation;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import com.jiayuan.boot.system.workflow.model.vo.ProcessDiagramResponseVO;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 团队邀请 Flowable 流程服务测试
 *
 * @author charleslam
 * @since 2026/05/20
 */
@ExtendWith(MockitoExtension.class)
class FlowableTeamInvitationWorkflowServiceTest {

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private HistoryService historyService;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private TeamInvitationMapper teamInvitationMapper;

    @Mock
    private ProcessInstance processInstance;

    @Mock
    private ProcessInstanceQuery processInstanceQuery;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private Task task;

    @Mock
    private HistoricActivityInstanceQuery historicActivityQuery;

    @Mock
    private HistoricProcessInstanceQuery historicProcessQuery;

    @Mock
    private HistoricProcessInstance historicProcessInstance;

    @Mock
    private ProcessDefinitionQuery processDefinitionQuery;

    @Mock
    private ProcessDefinition processDefinition;

    @Mock
    private HistoricActivityInstance finishedActivity;

    @Mock
    private HistoricActivityInstance approvalActivity;

    @InjectMocks
    private FlowableTeamInvitationWorkflowService workflowService;

    @Test
    void bpmnShouldDefineInvitationTopology() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/processes/team-invitation.bpmn20.xml"));

        assertThat(xml)
                .contains("process id=\"teamInvitationProcess\"")
                .contains("userTask id=\"inviteeApprovalTask\"")
                .contains("flowable:assignee=\"${inviteeAccountId}\"")
                .contains("boundaryEvent id=\"inviteTimeoutEvent\"")
                .contains("<timeDuration>${expireDuration}</timeDuration>")
                .contains("${decision == 'ACCEPT'}")
                .contains("${decision == 'REJECT'}")
                .contains("flowable:delegateExpression=\"${teamInvitationExpireDelegate}\"")
                .contains("acceptedEnd")
                .contains("rejectedEnd")
                .contains("expiredEnd");
    }

    @Test
    void startInvitationShouldOnlyStartProcessWithVariables() {
        when(runtimeService.startProcessInstanceByKey(eq("teamInvitationProcess"), eq("10"), anyMap()))
                .thenReturn(processInstance);
        when(processInstance.getProcessInstanceId()).thenReturn("process-1");

        String processInstanceId = workflowService.startInvitation(10L, 20L, 30L, 40L, "Editor", 86400L);

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).startProcessInstanceByKey(eq("teamInvitationProcess"), eq("10"), variables.capture());
        assertThat(processInstanceId).isEqualTo("process-1");
        assertThat(variables.getValue())
                .containsEntry("invitationId", 10L)
                .containsEntry("teamId", 20L)
                .containsEntry("inviterAccountId", 30L)
                .containsEntry("inviteeAccountId", 40L)
                .containsEntry("targetRole", "Editor")
                .containsEntry("expireDuration", "PT86400S");
    }

    @Test
    void findActiveInviteeTaskByInvitationIdShouldQueryFlowableTask() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processVariableValueEquals("invitationId", 10L)).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("inviteeApprovalTask")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn("task-1");

        Optional<String> taskId = workflowService.findActiveInviteeTaskIdByInvitationId(10L);

        assertThat(taskId).contains("task-1");
    }

    @Test
    void findActiveInviteeTaskIdShouldReturnEmptyWhenTaskMissing() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("process-1")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("inviteeApprovalTask")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        Optional<String> taskId = workflowService.findActiveInviteeTaskId("process-1");

        assertThat(taskId).isEmpty();
    }

    @Test
    void completeInvitationShouldCompleteInviteeTaskWithDecisionVariable() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("process-1")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("inviteeApprovalTask")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn("task-1");

        workflowService.completeInvitation("process-1", "ACCEPT");

        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
        verify(taskService).complete(eq("task-1"), variables.capture());
        assertThat(variables.getValue()).containsEntry("decision", "ACCEPT");
    }

    @Test
    void completeInvitationShouldRejectUnsupportedDecision() {
        assertThatThrownBy(() -> workflowService.completeInvitation("process-1", "INVITE"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不支持的邀请流程决策");
        verifyNoInteractions(taskService);
    }

    @Test
    void completeInvitationShouldThrowWhenInviteeTaskMissing() {
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("process-1")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("inviteeApprovalTask")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> workflowService.completeInvitation("process-1", "ACCEPT"))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_OPERATION_EXCEPTION);
    }

    @Test
    void cancelInvitationShouldDeleteExistingProcessInstance() {
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("process-1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);

        workflowService.cancelInvitation("process-1", "邀请人撤销");

        verify(runtimeService).deleteProcessInstance("process-1", "邀请人撤销");
    }

    @Test
    void cancelInvitationShouldIgnoreMissingProcessInstance() {
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("process-1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(null);

        workflowService.cancelInvitation("process-1", "邀请人撤销");

        verify(runtimeService, never()).deleteProcessInstance(eq("process-1"), eq("邀请人撤销"));
    }

    @Test
    void getProcessDiagramShouldReadDeploymentXmlAndResolveAssigneeBrief() throws Exception {
        String deployedXml = "<definitions id=\"team-invitation\"></definitions>";
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("process-1")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("def-1");
        when(processInstance.getBusinessKey()).thenReturn("10");
        when(runtimeService.getActiveActivityIds("process-1")).thenReturn(List.of("inviteeApprovalTask"));
        when(repositoryService.getBpmnModel("def-1")).thenReturn(new BpmnModel());
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.processDefinitionId("def-1")).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.singleResult()).thenReturn(processDefinition);
        when(processDefinition.getDeploymentId()).thenReturn("deploy-1");
        when(processDefinition.getResourceName()).thenReturn("declared.bpmn20.xml");
        when(repositoryService.getResourceAsStream("deploy-1", "declared.bpmn20.xml")).thenReturn(null);
        when(repositoryService.getDeploymentResourceNames("deploy-1"))
                .thenReturn(List.of("processes/team-invitation.bpmn20.xml"));
        when(repositoryService.getResourceAsStream("deploy-1", "processes/team-invitation.bpmn20.xml"))
                .thenReturn(new ByteArrayInputStream(deployedXml.getBytes(StandardCharsets.UTF_8)));
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.processInstanceId("process-1")).thenReturn(historicActivityQuery);
        when(historicActivityQuery.finished()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.asc()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.list()).thenReturn(List.of(finishedActivity), List.of(approvalActivity));
        when(finishedActivity.getActivityId()).thenReturn("startEvent");
        when(approvalActivity.getActivityId()).thenReturn("inviteeApprovalTask");
        when(approvalActivity.getActivityName()).thenReturn("被邀请人审批");
        when(approvalActivity.getActivityType()).thenReturn("userTask");
        when(approvalActivity.getAssignee()).thenReturn("40");
        when(approvalActivity.getStartTime()).thenReturn(Date.from(Instant.parse("2026-06-05T08:00:00Z")));
        when(approvalActivity.getEndTime()).thenReturn(Date.from(Instant.parse("2026-06-05T08:01:00Z")));
        when(approvalActivity.getDurationInMillis()).thenReturn(60000L);
        when(teamInvitationMapper.selectById(10L)).thenReturn(invitation());
        when(sysUserMapper.selectUserBriefByIds(anyList())).thenReturn(List.of(inviteeBrief()));

        ProcessDiagramResponseVO result = workflowService.getProcessDiagram("process-1");

        assertThat(result.getBpmnXml()).isEqualTo(deployedXml);
        assertThat(result.getActiveNodeIds()).containsExactly("inviteeApprovalTask");
        assertThat(result.getCompletedNodeIds()).containsExactly("startEvent");
        assertThat(result.isEnded()).isFalse();
        assertThat(result.getNodes()).hasSize(1);
        assertThat(result.getNodes().get(0).getAccountId()).isEqualTo(40L);
        assertThat(result.getNodes().get(0).getAccountName()).isEqualTo("invitee-account");
        assertThat(result.getNodes().get(0).getUsername()).isEqualTo("被邀请人");
        assertThat(result.getNodes().get(0).isCompleted()).isTrue();
    }

    @Test
    void getProcessDiagramShouldThrowWhenRuntimeAndHistoryAreMissing() {
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("missing-process")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(null);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicProcessQuery);
        when(historicProcessQuery.processInstanceId("missing-process")).thenReturn(historicProcessQuery);
        when(historicProcessQuery.list()).thenReturn(List.of());

        assertThatThrownBy(() -> workflowService.getProcessDiagram("missing-process"))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    void getProcessDiagramEndedProcessShouldMergeBpmndiAndIgnoreInvalidUserKeys() throws Exception {
        String deployedXml = """
                <definitions>
                  <bpmndi:BPMNDiagram id="diagram-1"></bpmndi:BPMNDiagram>
                </definitions>
                """;
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("ended-process")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(null);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicProcessQuery);
        when(historicProcessQuery.processInstanceId("ended-process")).thenReturn(historicProcessQuery);
        when(historicProcessQuery.list()).thenReturn(List.of(historicProcessInstance));
        when(historicProcessInstance.getProcessDefinitionId()).thenReturn("def-ended");
        when(historicProcessInstance.getBusinessKey()).thenReturn("not-a-number");
        when(repositoryService.getBpmnModel("def-ended")).thenReturn(minimalBpmnModel());
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.processDefinitionId("def-ended")).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.singleResult()).thenReturn(processDefinition);
        when(processDefinition.getDeploymentId()).thenReturn("deploy-ended");
        when(processDefinition.getResourceName()).thenReturn("declared.bpmn20.xml");
        when(repositoryService.getResourceAsStream("deploy-ended", "declared.bpmn20.xml")).thenReturn(null);
        when(repositoryService.getDeploymentResourceNames("deploy-ended"))
                .thenReturn(List.of("other.bpmn20.xml"));
        when(repositoryService.getResourceAsStream("deploy-ended", "other.bpmn20.xml"))
                .thenReturn(new ByteArrayInputStream(deployedXml.getBytes(StandardCharsets.UTF_8)));
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.processInstanceId("ended-process")).thenReturn(historicActivityQuery);
        when(historicActivityQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.asc()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.list()).thenReturn(List.of(finishedActivity), List.of(approvalActivity));
        when(finishedActivity.getActivityId()).thenReturn("acceptedEnd");
        when(approvalActivity.getActivityId()).thenReturn("manual");
        when(approvalActivity.getActivityName()).thenReturn("手工节点");
        when(approvalActivity.getActivityType()).thenReturn("userTask");
        when(approvalActivity.getAssignee()).thenReturn("not-account");
        when(approvalActivity.getStartTime()).thenReturn(null);
        when(approvalActivity.getEndTime()).thenReturn(null);
        when(approvalActivity.getDurationInMillis()).thenReturn(null);

        ProcessDiagramResponseVO result = workflowService.getProcessDiagram("ended-process");

        assertThat(result.isEnded()).isTrue();
        assertThat(result.getActiveNodeIds()).isEmpty();
        assertThat(result.getCompletedNodeIds()).containsExactly("acceptedEnd");
        assertThat(result.getBpmnXml()).contains("<bpmndi:BPMNDiagram");
        assertThat(result.getNodes()).hasSize(1);
        assertThat(result.getNodes().get(0).getAccountId()).isNull();
        assertThat(result.getNodes().get(0).isCompleted()).isFalse();
        verify(teamInvitationMapper, never()).selectById(any());
    }

    @Test
    void getProcessDiagramShouldFallbackToGeneratedXmlWhenRepositoryReadFails() {
        when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
        when(processInstanceQuery.processInstanceId("process-fallback")).thenReturn(processInstanceQuery);
        when(processInstanceQuery.singleResult()).thenReturn(processInstance);
        when(processInstance.getProcessDefinitionId()).thenReturn("def-fallback");
        when(processInstance.getBusinessKey()).thenReturn(null);
        when(repositoryService.getBpmnModel("def-fallback")).thenReturn(minimalBpmnModel());
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
        when(processDefinitionQuery.processDefinitionId("def-fallback")).thenReturn(processDefinitionQuery);
        doThrow(new IllegalStateException("repository offline"))
                .when(processDefinitionQuery).singleResult();
        when(runtimeService.getActiveActivityIds("process-fallback")).thenReturn(List.of());
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.processInstanceId("process-fallback")).thenReturn(historicActivityQuery);
        when(historicActivityQuery.finished()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.asc()).thenReturn(historicActivityQuery);
        when(historicActivityQuery.list()).thenReturn(List.of(), List.of());
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicProcessQuery);
        when(historicProcessQuery.processInstanceId("process-fallback")).thenReturn(historicProcessQuery);
        when(historicProcessQuery.list()).thenReturn(List.of());

        ProcessDiagramResponseVO result = workflowService.getProcessDiagram("process-fallback");

        assertThat(result.getBpmnXml()).contains("definitions");
        assertThat(result.isEnded()).isFalse();
    }

    private static TeamInvitation invitation() {
        TeamInvitation invitation = new TeamInvitation();
        invitation.setInviterAccountId(30L);
        invitation.setInviteeAccountId(40L);
        return invitation;
    }

    private static UserBriefBO inviteeBrief() {
        UserBriefBO user = new UserBriefBO();
        user.setUserId(4L);
        user.setAccountId(40L);
        user.setAccountName("invitee-account");
        user.setNickname("被邀请人");
        return user;
    }

    private static BpmnModel minimalBpmnModel() {
        BpmnModel model = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("teamInvitationProcess");
        model.addProcess(process);
        return model;
    }
}
