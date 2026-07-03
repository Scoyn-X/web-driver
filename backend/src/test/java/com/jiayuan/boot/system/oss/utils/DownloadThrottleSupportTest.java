package com.jiayuan.boot.system.oss.utils;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.quota.service.QuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 下载限速单元测试。
 */
@ExtendWith(MockitoExtension.class)
class DownloadThrottleSupportTest {

    @Mock
    private SystemConfigProperties configProperties;

    @Mock
    private QuotaService quotaService;

    private DownloadThrottleSupport support;

    @BeforeEach
    void setUp() {
        lenient().when(configProperties.getDownloadThrottleThreshold()).thenReturn(5L * 1024 * 1024);
        lenient().when(configProperties.getNormalDownloadBytesPerSecond()).thenReturn(512L * 1024);
        support = new DownloadThrottleSupport(configProperties);
    }

    @Test
    void shouldThrottleWhenNonVipAndFileGt5MB() {
        when(quotaService.isVip(1L)).thenReturn(false);

        boolean result = support.shouldThrottle(10L * 1024 * 1024, 1L, quotaService);
        assertThat(result).isTrue();
    }

    @Test
    void shouldNotThrottleWhenVip() {
        when(quotaService.isVip(2L)).thenReturn(true);

        boolean result = support.shouldThrottle(10L * 1024 * 1024, 2L, quotaService);
        assertThat(result).isFalse();
    }

    @Test
    void shouldNotThrottleWhenFileUnderThreshold() {
        boolean result = support.shouldThrottle(1L * 1024 * 1024, 1L, quotaService);
        assertThat(result).isFalse();
    }

    @Test
    void shouldThrottleSharedDownloadWhenFileExceedsThreshold() {
        boolean result = support.shouldThrottleSharedDownload(10L * 1024 * 1024);
        assertThat(result).isTrue();
    }

    @Test
    void shouldNotThrottleSharedDownloadWhenFileUnderThreshold() {
        boolean result = support.shouldThrottleSharedDownload(1L * 1024 * 1024);
        assertThat(result).isFalse();
    }

    @Test
    void throttledCopyForSharedDownloadUsesNormalPolicyWithoutQuotaLookup() {
        when(configProperties.getDownloadThrottleThreshold()).thenReturn(1L);
        when(configProperties.getNormalDownloadBytesPerSecond()).thenReturn(1024L * 1024);

        byte[] mockFile = new byte[32 * 1024];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(mockFile);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        long start = System.currentTimeMillis();
        support.throttledCopyForSharedDownload(inputStream, outputStream, (long) mockFile.length);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isGreaterThan(20);
        assertThat(outputStream.size()).isEqualTo(mockFile.length);
        verifyNoInteractions(quotaService);
    }

    @Test
    void throttledCopySlowsDownLargeFile() throws Exception {
        when(quotaService.isVip(1L)).thenReturn(false);

        // 模拟 6MB 文件下载（刚超过 5MB 阈值）
        byte[] mockFile = new byte[6 * 1024 * 1024];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(mockFile);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        long start = System.currentTimeMillis();
        support.throttledCopy(inputStream, outputStream, (long) mockFile.length, 1L, quotaService);
        long elapsed = System.currentTimeMillis() - start;

        // 6MB @ 512KB/s ≈ 12秒，验证确实慢了（至少 0.5 秒，远小于不限速的 <0.1s）
        assertThat(elapsed).isGreaterThan(500);
        assertThat(outputStream.size()).isEqualTo(mockFile.length);
    }

    @Test
    void throttledCopyNoLimitForVip() throws Exception {
        when(quotaService.isVip(2L)).thenReturn(true);

        byte[] mockFile = new byte[6 * 1024 * 1024];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(mockFile);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        long start = System.currentTimeMillis();
        support.throttledCopy(inputStream, outputStream, (long) mockFile.length, 2L, quotaService);
        long elapsed = System.currentTimeMillis() - start;

        // VIP 用户不限速，应在极短时间内完成（< 0.5s）
        assertThat(elapsed).isLessThan(500);
        assertThat(outputStream.size()).isEqualTo(mockFile.length);
    }

    @Test
    void throttledCopyNoLimitForSmallFile() throws Exception {
        // 1MB 文件，小于阈值
        byte[] mockFile = new byte[1 * 1024 * 1024];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(mockFile);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        long start = System.currentTimeMillis();
        support.throttledCopy(inputStream, outputStream, (long) mockFile.length, 1L, quotaService);
        long elapsed = System.currentTimeMillis() - start;

        // 小文件不限速
        assertThat(elapsed).isLessThan(500);
        assertThat(outputStream.size()).isEqualTo(mockFile.length);
    }

    @Test
    void throttleIfNecessaryInterruptedThrowsDownloadExceptionAndRestoresInterruptFlag() {
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> support.throttleIfNecessary(true, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DOWNLOAD_FILE_EXCEPTION);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        // 清理当前测试线程的中断标记，避免影响后续测试。
        Thread.interrupted();
    }

    @Test
    void throttledCopyWrapsOutputStreamFailureAsDownloadException() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("broken".getBytes());
        OutputStream outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("closed");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                throw new IOException("closed");
            }
        };

        assertThatThrownBy(() -> support.throttledCopyForSharedDownload(inputStream, outputStream, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DOWNLOAD_FILE_EXCEPTION);
    }
}
