package com.jiayuan.boot.system.team.model.vo;

import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 团队配额响应VO，继承个人配额字段。
 *
 * @author didongchen
 * @since 2026/05/15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "团队配额响应")
public class TeamQuotaResponseVO extends QuotaResponseVO {
}
