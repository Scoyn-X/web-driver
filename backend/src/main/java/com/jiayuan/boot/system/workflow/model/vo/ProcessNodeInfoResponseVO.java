package com.jiayuan.boot.system.workflow.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流程节点信息响应 VO。
 *
 * @author charleslam
 * @since 2026/05/24
 */
@Data
@Builder
@Schema(description = "流程节点信息")
public class ProcessNodeInfoResponseVO {

    @Schema(description = "节点ID", example = "inviteeApprovalTask")
    private String nodeId;

    @Schema(description = "节点名称", example = "被邀请人审批")
    private String nodeName;

    @Schema(description = "节点类型", example = "userTask")
    private String nodeType;

    @Schema(description = "操作人（assignee）", example = "zhangsan")
    private String assignee;

    @Schema(description = "操作人用户ID")
    private Long userId;

    @Schema(description = "操作人用户名")
    private String username;

    @Schema(description = "操作人账户ID")
    private Long accountId;

    @Schema(description = "操作人账户名")
    private String accountName;

    @Schema(description = "节点开始时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "节点结束时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "节点经历时长（毫秒）")
    private Long durationMillis;

    @Schema(description = "节点是否已完成")
    private boolean completed;
}
