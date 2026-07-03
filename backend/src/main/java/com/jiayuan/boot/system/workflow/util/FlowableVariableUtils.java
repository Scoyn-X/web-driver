package com.jiayuan.boot.system.workflow.util;

import org.flowable.engine.delegate.DelegateExecution;

/**
 * Flowable 流程变量读取工具。
 *
 * @author charleslam
 * @since 2026/05/20
 */
public final class FlowableVariableUtils {

    private FlowableVariableUtils() {
    }

    /**
     * 读取 Long 类型流程变量。
     *
     * @param execution    流程执行上下文
     * @param variableName 变量名
     * @return Long 值，无法转换时返回 null
     */
    public static Long getLong(DelegateExecution execution, String variableName) {
        return toLong(execution.getVariable(variableName));
    }

    /**
     * 将流程变量转换为 Long。
     *
     * @param value 变量值
     * @return Long 值，无法转换时返回 null
     */
    public static Long toLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            return Long.valueOf((String) value);
        }
        return null;
    }
}
