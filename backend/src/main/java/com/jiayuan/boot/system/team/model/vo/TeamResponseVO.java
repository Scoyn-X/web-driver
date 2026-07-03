package com.jiayuan.boot.system.team.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队信息响应VO（用于列表项和详情）
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@Schema(description = "团队信息响应")
public class TeamResponseVO {

    @Schema(description = "团队ID", example = "1")
    private Long id;

    @Schema(description = "团队名称", example = "软件工程第14组")
    private String name;

    @Schema(description = "团队描述", example = "我们的开发团队")
    private String description;

    @Schema(description = "团队Owner用户ID", example = "1")
    private Long ownerId;

    @Schema(description = "团队Owner账户ID", example = "10")
    private Long ownerAccountId;

    @Schema(description = "团队Owner用户名", example = "张三")
    private String ownerName;

    @Schema(description = "团队状态(ACTIVE-正常 DISSOLVED-已解散)", example = "ACTIVE")
    private String status;

    @Schema(description = "当前用户在团队中的角色(Owner/Admin/Editor/Viewer)", example = "Admin")
    private String role;

    @Schema(description = "团队配额信息")
    private TeamQuotaResponseVO quota;

    @Schema(description = "创建时间", example = "2026-05-15 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}
