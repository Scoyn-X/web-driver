package com.jiayuan.boot.system.team.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;

import java.util.Locale;

/**
 * 团队邀请动作枚举。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Getter
public enum InvitationAction implements BaseEnum<String> {

    INVITE("INVITE", "发起邀请", "邀请已发起", true),
    ACCEPT("ACCEPT", "接受邀请", "邀请已接受", false),
    REJECT("REJECT", "拒绝邀请", "邀请已拒绝", false),
    REVOKE("REVOKE", "撤销邀请", "邀请已撤销", false, "邀请人撤销");

    private final String value;

    private final String label;

    private final String successMessage;

    private final boolean invitePermissionRequired;

    private final String defaultReason;

    InvitationAction(String value, String label, String successMessage, boolean invitePermissionRequired) {
        this(value, label, successMessage, invitePermissionRequired, null);
    }

    InvitationAction(String value, String label, String successMessage,
                     boolean invitePermissionRequired, String defaultReason) {
        this.value = value;
        this.label = label;
        this.successMessage = successMessage;
        this.invitePermissionRequired = invitePermissionRequired;
        this.defaultReason = defaultReason;
    }

    /**
     * 是否需要团队成员邀请权限。
     *
     * @return 是否需要邀请权限
     */
    public boolean requiresInvitePermission() {
        return invitePermissionRequired;
    }

    /**
     * 根据请求值解析邀请动作。
     *
     * @param value 请求动作值
     * @return 邀请动作枚举
     */
    public static InvitationAction fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (InvitationAction action : values()) {
            if (action.value.equals(normalized)) {
                return action;
            }
        }
        return null;
    }
}
