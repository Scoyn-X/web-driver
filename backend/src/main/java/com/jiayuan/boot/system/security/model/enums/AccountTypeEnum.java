package com.jiayuan.boot.system.security.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;

/**
 * 账户类型枚举
 *
 * @author didongchen
 * @since 2026/04/10
 */
@Getter
public enum AccountTypeEnum implements BaseEnum<String> {

    /**
     * 个人账户
     */
    PERSONAL("personal", "个人账户"),

    /**
     * 工作账户
     */
    WORK("work", "工作账户"),

    /**
     * 团队账户
     */
    TEAM("team", "团队账户");

    private final String value;

    private final String label;

    AccountTypeEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

}
