package com.jiayuan.boot.system.workflow.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.team.mapper.TeamInvitationMapper;
import com.jiayuan.boot.system.team.model.entity.TeamInvitation;
import com.jiayuan.boot.system.team.model.enums.InvitationAction;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import com.jiayuan.boot.system.workflow.model.vo.ProcessDiagramResponseVO;
import com.jiayuan.boot.system.workflow.model.vo.ProcessNodeInfoResponseVO;
import com.jiayuan.boot.system.workflow.service.TeamInvitationWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Flowable 团队邀请流程服务实现。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowableTeamInvitationWorkflowService implements TeamInvitationWorkflowService {

    static final String PROCESS_KEY = "teamInvitationProcess";
    static final String INVITEE_TASK_KEY = "inviteeApprovalTask";
    static final String VAR_DECISION = "decision";
    public static final String VAR_INVITATION_ID = "invitationId";

    private static final Set<String> SUPPORTED_DECISIONS = Set.of(
            InvitationAction.ACCEPT.getValue(), InvitationAction.REJECT.getValue());

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final SysUserMapper sysUserMapper;
    private final TeamInvitationMapper teamInvitationMapper;

    /**
     * 启动团队邀请流程实例。
     */
    @Override
    public String startInvitation(Long invitationId, Long teamId, Long inviterAccountId, Long inviteeAccountId,
                                  String targetRole, Long expireSeconds) {
        Map<String, Object> variables = Map.of(
                VAR_INVITATION_ID, invitationId,
                "teamId", teamId,
                "inviterAccountId", inviterAccountId,
                "inviteeAccountId", inviteeAccountId,
                "targetRole", targetRole,
                "expireDuration", "PT" + expireSeconds + "S");
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
                PROCESS_KEY, String.valueOf(invitationId), variables);
        log.info("团队邀请流程启动 invitationId={} processInstanceId={}",
                invitationId, instance.getProcessInstanceId());
        return instance.getProcessInstanceId();
    }

    /**
     * 查询流程实例中的被邀请人任务。
     */
    @Override
    public Optional<String> findActiveInviteeTaskId(String processInstanceId) {
        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(INVITEE_TASK_KEY)
                .singleResult();
        return Optional.ofNullable(task).map(Task::getId);
    }

    /**
     * 根据邀请ID查询被邀请人任务。
     */
    @Override
    public Optional<String> findActiveInviteeTaskIdByInvitationId(Long invitationId) {
        Task task = taskService.createTaskQuery()
                .processVariableValueEquals(VAR_INVITATION_ID, invitationId)
                .taskDefinitionKey(INVITEE_TASK_KEY)
                .singleResult();
        return Optional.ofNullable(task).map(Task::getId);
    }

    /**
     * 完成被邀请人的审批任务。
     */
    @Override
    public void completeInvitation(String processInstanceId, String decision) {
        if (!SUPPORTED_DECISIONS.contains(decision)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不支持的邀请流程决策");
        }
        String taskId = findActiveInviteeTaskId(processInstanceId)
                .orElseThrow(() -> new BusinessException(
                        ResultCode.USER_OPERATION_EXCEPTION, "邀请流程任务不存在或已处理"));
        taskService.complete(taskId, Map.of(VAR_DECISION, decision));
        log.info("团队邀请流程任务完成 processInstanceId={} decision={}", processInstanceId, decision);
    }

    /**
     * 取消团队邀请流程实例。
     */
    @Override
    public void cancelInvitation(String processInstanceId, String reason) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            return;
        }
        runtimeService.deleteProcessInstance(processInstanceId, reason);
        log.info("团队邀请流程取消 processInstanceId={} reason={}", processInstanceId, reason);
    }

    /**
     * 获取邀请流程图表数据。
     */
    @Override
    public ProcessDiagramResponseVO getProcessDiagram(String processInstanceId) {
        BpmnModel bpmnModel = getBpmnModelForProcessInstance(processInstanceId);
        String processDefinitionId = getProcessDefinitionIdForProcessInstance(processInstanceId);
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        List<String> activeNodeIds;
        List<String> completedNodeIds;
        boolean ended;

        if (processInstance != null) {
            activeNodeIds = runtimeService.getActiveActivityIds(processInstanceId)
                    .stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            completedNodeIds = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .finished()
                    .list()
                    .stream()
                    .map(HistoricActivityInstance::getActivityId)
                    .collect(Collectors.toList());
            ended = false;
        } else {
            activeNodeIds = Collections.emptyList();
            completedNodeIds = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .list()
                    .stream()
                    .map(HistoricActivityInstance::getActivityId)
                    .collect(Collectors.toList());
            ended = true;
        }

        String bpmnXml = null;
        try {
            org.flowable.engine.repository.ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            if (def != null) {
                String deploymentId = def.getDeploymentId();
                String resourceName = def.getResourceName();
                log.debug("流程定义部署 id={} resourceName={}", deploymentId, resourceName);

                // Try the declared resource name first
                java.io.InputStream is = repositoryService.getResourceAsStream(deploymentId, resourceName);
                // If not found, try to find a matching resource name in the deployment
                if (is == null) {
                    try {
                        for (String rn : repositoryService.getDeploymentResourceNames(deploymentId)) {
                            if (rn != null && rn.contains("team-invitation")) {
                                log.debug("尝试使用部署资源名: {}", rn);
                                is = repositoryService.getResourceAsStream(deploymentId, rn);
                                if (is != null) {
                                    resourceName = rn;
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("获取部署资源列表失败: {}", e.getMessage());
                    }
                }

                if (is != null) {
                    try (java.io.InputStream bis = is) {
                        byte[] bytes = bis.readAllBytes();
                        bpmnXml = new String(bytes, StandardCharsets.UTF_8);
                        log.debug("已读取部署资源 XML，长度={} resourceName={}", bpmnXml.length(), resourceName);
                    }
                } else {
                    log.debug("部署资源未找到，deploymentId={} resourceName={}", deploymentId, def.getResourceName());
                }
            }
        } catch (Exception ex) {
            log.debug("读取部署资源原始 BPMN 失败，回退为从模型生成 XML：{}", ex.getMessage());
        }
        if (bpmnXml == null) {
            bpmnXml = convertBpmnToXml(bpmnModel, processDefinitionId);
        }

        // 获取流程变量中的账户ID，用于解析用户信息
        Map<Long, UserBriefBO> userBriefMap = resolveProcessUserBriefMap(processInstanceId);

        // 收集所有历史活动实例，构建节点详细信息
        List<HistoricActivityInstance> allActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
        List<ProcessNodeInfoResponseVO> nodes = buildNodeInfos(allActivities, userBriefMap);

        return ProcessDiagramResponseVO.builder()
                .bpmnXml(bpmnXml)
                .activeNodeIds(activeNodeIds)
                .completedNodeIds(completedNodeIds)
                .processInstanceId(processInstanceId)
                .ended(ended)
                .nodes(nodes)
                .build();
    }

    private List<ProcessNodeInfoResponseVO> buildNodeInfos(List<HistoricActivityInstance> activities,
                                                     Map<Long, UserBriefBO> userBriefMap) {
        List<ProcessNodeInfoResponseVO> nodes = new ArrayList<>();
        for (HistoricActivityInstance a : activities) {
            ProcessNodeInfoResponseVO.ProcessNodeInfoResponseVOBuilder builder = ProcessNodeInfoResponseVO.builder()
                    .nodeId(a.getActivityId())
                    .nodeName(a.getActivityName())
                    .nodeType(a.getActivityType())
                    .assignee(a.getAssignee())
                    .startTime(a.getStartTime() != null
                            ? a.getStartTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                            : null)
                    .endTime(a.getEndTime() != null
                            ? a.getEndTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                            : null)
                    .durationMillis(a.getDurationInMillis())
                    .completed(a.getEndTime() != null);
            // 解析assignee对应的用户信息
            Long accountId = parseAccountId(a.getAssignee());
            if (accountId != null) {
                UserBriefBO user = userBriefMap.get(accountId);
                if (user != null) {
                    builder.accountId(user.getAccountId())
                            .accountName(user.getAccountName())
                            .userId(user.getUserId())
                            .username(user.getNickname());
                }
            }
            nodes.add(builder.build());
        }
        return nodes;
    }

    private Map<Long, UserBriefBO> resolveProcessUserBriefMap(String processInstanceId) {
        // 通过流程实例 businessKey（invitationId）查找邀请记录，获取关联账户
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        String businessKey = processInstance != null ? processInstance.getBusinessKey() : null;
        if (businessKey == null) {
            // 流程已结束，从历史查询
            List<org.flowable.engine.history.HistoricProcessInstance> historicList =
                    historyService.createHistoricProcessInstanceQuery()
                            .processInstanceId(processInstanceId)
                            .list();
            if (!historicList.isEmpty()) {
                businessKey = historicList.get(0).getBusinessKey();
            }
        }
        if (businessKey == null) {
            return Collections.emptyMap();
        }
        Long invitationId;
        try {
            invitationId = Long.valueOf(businessKey);
        } catch (NumberFormatException e) {
            return Collections.emptyMap();
        }
        TeamInvitation invitation = teamInvitationMapper.selectById(invitationId);
        if (invitation == null) {
            return Collections.emptyMap();
        }
        Set<Long> accountIds = new HashSet<>();
        if (invitation.getInviteeAccountId() != null) {
            accountIds.add(invitation.getInviteeAccountId());
        }
        if (invitation.getInviterAccountId() != null) {
            accountIds.add(invitation.getInviterAccountId());
        }
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectUserBriefByIds(new ArrayList<>(accountIds))
                .stream()
                .collect(Collectors.toMap(UserBriefBO::getAccountId, u -> u, (a, b) -> a));
    }

    private static Long parseAccountId(String assignee) {
        if (assignee == null) {
            return null;
        }
        try {
            return Long.valueOf(assignee);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getProcessDefinitionIdForProcessInstance(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        String processDefinitionId;
        if (processInstance != null) {
            processDefinitionId = processInstance.getProcessDefinitionId();
        } else {
            List<org.flowable.engine.history.HistoricProcessInstance> historicList =
                    historyService.createHistoricProcessInstanceQuery()
                            .processInstanceId(processInstanceId)
                            .list();
            if (historicList.isEmpty()) {
                throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "流程实例不存在");
            }
            processDefinitionId = historicList.get(0).getProcessDefinitionId();
        }
        return processDefinitionId;
    }

    private BpmnModel getBpmnModelForProcessInstance(String processInstanceId) {
        return repositoryService.getBpmnModel(getProcessDefinitionIdForProcessInstance(processInstanceId));
    }

    private String convertBpmnToXml(BpmnModel bpmnModel, String processDefinitionId) {
        byte[] xmlBytes = new BpmnXMLConverter().convertToXML(bpmnModel, "UTF-8");
        String modelXml = new String(xmlBytes, StandardCharsets.UTF_8);

        try {
            org.flowable.engine.repository.ProcessDefinition def = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            if (def != null) {
                String deploymentId = def.getDeploymentId();
                try {
                    for (String rn : repositoryService.getDeploymentResourceNames(deploymentId)) {
                        if (rn == null) continue;
                        // try to find a deployed BPMN resource that may contain BPMNDI
                        if (rn.endsWith(".bpmn") || rn.endsWith(".bpmn20.xml") || rn.endsWith(".bpmn20")) {
                            try (java.io.InputStream is = repositoryService.getResourceAsStream(deploymentId, rn)) {
                                if (is == null) continue;
                                byte[] depBytes = is.readAllBytes();
                                String depXml = new String(depBytes, StandardCharsets.UTF_8);
                                // extract bpmndi block if present
                                Pattern p = Pattern.compile(
                                        "<bpmndi:BPMNDiagram[\\s\\S]*?</bpmndi:BPMNDiagram>",
                                        Pattern.CASE_INSENSITIVE);
                                Matcher m = p.matcher(depXml);
                                if (m.find()) {
                                    String bpmndiBlock = m.group();
                                    // if modelXml already contains bpmndi, skip
                                    if (!modelXml.contains("<bpmndi:BPMNDiagram")) {
                                        int idx = modelXml.lastIndexOf("</definitions>");
                                        if (idx > 0) {
                                            String merged = modelXml.substring(0, idx) + bpmndiBlock + modelXml.substring(idx);
                                            return merged;
                                        }
                                    } else {
                                        return modelXml;
                                    }
                                }
                            } catch (Exception ignore) {
                                log.debug("读取部署资源 {} 失败: {}", rn, ignore.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("遍历部署资源名称失败: {}", e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.debug("合并 BPMNDI 失败: {}", ex.getMessage());
        }

        return modelXml;
    }
}
