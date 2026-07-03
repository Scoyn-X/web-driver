package com.jiayuan.boot.common.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 请求方法枚举
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Getter
@AllArgsConstructor
public enum RequestMethodEnum implements BaseEnum<String> {

    GET("GET", "GET"),
    POST("POST", "POST"),
    PUT("PUT", "PUT"),
    PATCH("PATCH", "PATCH"),
    DELETE("DELETE", "DELETE"),
    ALL("ALL", "所有请求方法");

    /**
     * 请求方法值
     */
    private final String value;

    /**
     * 请求方法标签
     */
    private final String label;

    public static RequestMethodEnum find(String type) {
        for (RequestMethodEnum value : RequestMethodEnum.values()) {
            if (value.getValue().equals(type)) {
                return value;
            }
        }
        return ALL;
    }

}
