package com.jiayuan.boot.system.team.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;

/**
 * 团队邀请状态枚举。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Getter
public enum InvitationStatus implements BaseEnum<String> {

    PENDING("PENDING", "待处理"),
    ACCEPTED("ACCEPTED", "已接受"),
    REJECTED("REJECTED", "已拒绝"),
    REVOKED("REVOKED", "已撤销"),
    EXPIRED("EXPIRED", "已过期", "邀请已过期"),
    TEAM_DISSOLVED("TEAM_DISSOLVED", "团队已解散", "团队已解散");

    private final String value;

    private final String label;

    private final String defaultReason;

    InvitationStatus(String value, String label) {
        this(value, label, null);
    }

    InvitationStatus(String value, String label, String defaultReason) {
        this.value = value;
        this.label = label;
        this.defaultReason = defaultReason;
    }
}
