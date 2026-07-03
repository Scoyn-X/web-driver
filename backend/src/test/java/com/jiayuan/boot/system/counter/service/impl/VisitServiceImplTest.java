package com.jiayuan.boot.system.counter.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 访问计数服务单元测试。
 *
 * @author charleslam
 * @since 2026/06/05
 */
@DisplayName("VisitServiceImpl 单元测试")
class VisitServiceImplTest {

    @Test
    @DisplayName("访问计数：递增、读取、重置")
    void counter_incrementGetAndReset() {
        VisitServiceImpl service = new VisitServiceImpl();

        assertThat(service.getCount()).isZero();
        assertThat(service.incrementAndGet()).isEqualTo(1);
        assertThat(service.incrementAndGet()).isEqualTo(2);
        assertThat(service.getCount()).isEqualTo(2);

        service.reset();

        assertThat(service.getCount()).isZero();
    }
}
