package com.jiayuan.boot.system.user.model.bo;

import com.jiayuan.boot.system.user.model.enums.VipState;
import lombok.Data;

/**
 * 用户 VIP 限制信息
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
public class UserVipProfileBO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * VIP 状态
     */
    private VipState vipState;

    /**
     * 个人容量限制，VIP 为 null
     */
    private Long personalQuotaLimit;

    /**
     * 团队容量限制，VIP 为 null
     */
    private Long teamQuotaLimit;

    /**
     * 是否下载限速
     */
    private Boolean downloadLimited;

    /**
     * 单文件大小限制，VIP 为 null
     */
    private Long singleFileLimit;
}
