package com.jiayuan.boot.common.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 字符串工具类
 *
 * @author didongchen
 * @since 2026/05/15
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * 将逗号分隔的ID字符串转为 List&lt;Long&gt;
     *
     * @param fullPath 逗号分隔的ID字符串
     * @return ID列表
     */
    public static List<Long> parseIdList(String fullPath) {
        if (fullPath == null || fullPath.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(fullPath.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .toList();
    }

}
