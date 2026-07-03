package com.jiayuan.boot.system.team.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;

/**
 * 团队成员状态枚举
 *
 * @author didongchen
 * @since 2026/05/14
 */
@Getter
public enum MemberStatus implements BaseEnum<String> {

    ACTIVE("ACTIVE", "正常"),
    REMOVED("REMOVED", "已被移除"),
    EXITED("EXITED", "已退出");

    private final String value;

    private final String label;

    MemberStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }
}
