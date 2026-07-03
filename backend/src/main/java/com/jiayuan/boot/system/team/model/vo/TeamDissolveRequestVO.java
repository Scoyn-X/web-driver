package com.jiayuan.boot.system.team.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 解散团队请求VO
 *
 * @author didongchen
 * @since 2026/05/17
 */
@Data
@Schema(description = "解散团队请求")
public class TeamDissolveRequestVO {

    @Schema(description = "解散原因", example = "团队已完成使命")
    private String reason;

}
