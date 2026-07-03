package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建团队请求VO
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@Schema(description = "创建团队请求")
public class TeamCreateRequestVO {

    @Schema(description = "团队名称", example = "软件工程第14组")
    @NotBlank(message = "团队名称不能为空")
    private String name;

    @Schema(description = "团队描述", example = "我们的开发团队")
    private String description;

}
