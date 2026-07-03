package com.jiayuan.boot.common.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;

import lombok.Getter;

/**
 * 环境枚举
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Getter
public enum EnvEnum implements BaseEnum<String> {

    DEV("dev", "开发环境"),
    PROD("prod", "生产环境");

    private final String value;

    private final String label;

    EnvEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

}
