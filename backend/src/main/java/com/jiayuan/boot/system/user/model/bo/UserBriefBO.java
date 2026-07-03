package com.jiayuan.boot.system.user.model.bo;

import lombok.Data;

/**
 * 用户搜索与展示基础信息
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
public class UserBriefBO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 账户ID
     */
    private Long accountId;

    /**
     * 主账户名
     */
    private String accountName;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;
}
