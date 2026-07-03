package com.jiayuan.boot.system.user.model.bo;

import com.jiayuan.boot.system.user.model.enums.VipState;
import lombok.Value;

/**
 * 用户 VIP 限制信息构造参数
 *
 * @author charleslam
 * @since 2026/05/24
 */
@Value
public class UserVipProfileBuildBO {

    /**
     * 用户ID
     */
    Long userId;

    /**
     * VIP 状态
     */
    VipState vipState;

    /**
     * 个人容量限制，VIP 为 null
     */
    Long personalQuotaLimit;

    /**
     * 团队容量限制，VIP 为 null
     */
    Long teamQuotaLimit;

    /**
     * 单文件大小限制，VIP 为 null
     */
    Long singleFileLimit;
}
