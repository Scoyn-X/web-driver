package com.jiayuan.boot.system.workflow.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 流程图表响应VO。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Data
@Builder
@Schema(description = "流程图表响应")
public class ProcessDiagramResponseVO {

    @Schema(description = "BPMN 2.0 流程图 XML", example = "<?xml version=\"1.0\" ...>")
    private String bpmnXml;

    @Schema(description = "当前活跃节点ID列表")
    private List<String> activeNodeIds;

    @Schema(description = "已完成节点ID列表")
    private List<String> completedNodeIds;

    @Schema(description = "流程实例ID", example = "abc123-def456")
    private String processInstanceId;

    @Schema(description = "流程是否已结束")
    private boolean ended;

    @Schema(description = "各节点详细信息")
    private List<ProcessNodeInfoResponseVO> nodes;
}
