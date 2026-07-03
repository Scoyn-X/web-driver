package com.jiayuan.boot.system.team.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;

/**
 * 团队状态枚举
 *
 * @author didongchen
 * @since 2026/05/14
 */
@Getter
public enum TeamStatus implements BaseEnum<String> {

    ACTIVE("ACTIVE", "正常"),
    DISSOLVED("DISSOLVED", "已解散");

    private final String value;

    private final String label;

    TeamStatus(String value, String label) {
        this.value = value;
        this.label = label;
    }
}
