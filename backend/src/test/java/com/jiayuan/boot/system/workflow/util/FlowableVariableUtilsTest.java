package com.jiayuan.boot.system.workflow.util;

import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Flowable 流程变量工具单元测试。
 *
 * @author charleslam
 * @since 2026/06/05
 */
@DisplayName("FlowableVariableUtils 单元测试")
class FlowableVariableUtilsTest {

    @Test
    @DisplayName("toLong：支持 Long、Number、非空字符串，空值返回 null")
    void toLong_convertsSupportedValues() {
        assertThat(FlowableVariableUtils.toLong(7L)).isEqualTo(7L);
        assertThat(FlowableVariableUtils.toLong(7)).isEqualTo(7L);
        assertThat(FlowableVariableUtils.toLong("8")).isEqualTo(8L);
        assertThat(FlowableVariableUtils.toLong(" ")).isNull();
        assertThat(FlowableVariableUtils.toLong(null)).isNull();
    }

    @Test
    @DisplayName("getLong：从 DelegateExecution 读取变量后转换")
    void getLong_readsExecutionVariable() {
        DelegateExecution execution = mock(DelegateExecution.class);
        when(execution.getVariable("invitationId")).thenReturn("42");

        assertThat(FlowableVariableUtils.getLong(execution, "invitationId")).isEqualTo(42L);
    }
}
