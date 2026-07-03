package com.jiayuan.boot.system.user.model.vo;

import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;
import com.jiayuan.boot.system.user.model.enums.VipState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前用户响应对象
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
public class CurrentUserResponseVO {

    @Schema(description = "当前用户ID")
    private Long userId;

    @Schema(description = "当前账户ID")
    private Long accountId;

    @Schema(description = "当前账户名")
    private String accountName;

    @Schema(description = "当前用户昵称")
    private String nickname;

    @Schema(description = "当前用户邮箱")
    private String email;

    @Schema(description = "VIP 状态")
    private VipState vipState;

    @Schema(description = "个人配额")
    private QuotaResponseVO personalQuota;

    @Schema(description = "私密空间提醒")
    private String privateSpaceReminder;
}
