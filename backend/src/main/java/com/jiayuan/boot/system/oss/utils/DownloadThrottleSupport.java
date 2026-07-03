package com.jiayuan.boot.system.oss.utils;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.quota.service.QuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 下载限速辅助工具。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DownloadThrottleSupport {

    public static final int STREAM_BUFFER_SIZE = 8192;

    private final SystemConfigProperties configProperties;

    /**
     * 判断当前下载是否需要普通用户限速。
     */
    public boolean shouldThrottle(Long fileSize, Long userId, QuotaService quotaService) {
        if (fileSize == null || fileSize <= configProperties.getDownloadThrottleThreshold()) {
            return false;
        }
        boolean isVip = quotaService.isVip(userId);
        log.debug("download_throttle_check fileSize={} userId={} isVip={}", fileSize, userId, isVip);
        return !isVip;
    }

    /**
     * 判断匿名分享下载是否需要按普通用户策略限速。
     */
    public boolean shouldThrottleSharedDownload(Long fileSize) {
        return fileSize != null && fileSize > configProperties.getDownloadThrottleThreshold();
    }

    /**
     * 根据本次写出字节数执行简单 sleep 限速。
     */
    public void throttleIfNecessary(boolean throttled, int bytesRead) {
        if (!throttled || bytesRead <= 0) {
            return;
        }
        long sleepMillis = Math.max(10L, bytesRead * 1000L / configProperties.getNormalDownloadBytesPerSecond());
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "下载被中断");
        }
    }

    /**
     * 带限速的流式拷贝，统一团队和个人下载行为。
     */
    public void throttledCopy(InputStream inputStream, OutputStream outputStream,
                               Long fileSize, Long userId, QuotaService quotaService) {
        boolean throttled = shouldThrottle(fileSize, userId, quotaService);
        copyWithThrottle(inputStream, outputStream, throttled);
    }

    /**
     * 按普通用户策略拷贝匿名分享下载流。
     */
    public void throttledCopyForSharedDownload(InputStream inputStream, OutputStream outputStream,
                                               Long fileSize) {
        copyWithThrottle(inputStream, outputStream, shouldThrottleSharedDownload(fileSize));
    }

    private void copyWithThrottle(InputStream inputStream, OutputStream outputStream, boolean throttled) {
        try {
            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                throttleIfNecessary(throttled, bytesRead);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "下载流式拷贝失败");
        }
    }
}
