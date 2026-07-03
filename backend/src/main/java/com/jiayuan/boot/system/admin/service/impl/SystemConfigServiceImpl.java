package com.jiayuan.boot.system.admin.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.common.util.FileUtils;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.admin.model.vo.SystemConfigUpdateRequestVO;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.admin.model.vo.SystemConfigResponseVO;
import com.jiayuan.boot.system.admin.service.SystemConfigService;
import com.jiayuan.boot.system.oss.service.impl.TrashRetentionCleanupService;
import com.jiayuan.boot.system.quota.mapper.UserQuotaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 系统配置管理服务实现。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigProperties properties;
    private final TrashRetentionCleanupService cleanupService;
    private final UserQuotaMapper userQuotaMapper;
    private final SysFileMapper sysFileMapper;
    private final Scheduler scheduler;

    // 获取当前系统配置。
    @Override
    public SystemConfigResponseVO getConfig() {
        return buildConfigVO();
    }

    // 更新系统配置。
    @Override
    public SystemConfigResponseVO updateConfig(SystemConfigUpdateRequestVO request) {
        if (request.getTrashRetentionSeconds() != null) {
            if (request.getTrashRetentionSeconds() < 1 || request.getTrashRetentionSeconds() > 31536000L) {
                throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR,
                        "回收站保留秒数必须在 1-31536000 之间（最大 365 天）");
            }
            properties.setTrashRetentionSeconds(request.getTrashRetentionSeconds());
            sysFileMapper.updateRecycleBinExpireAt(request.getTrashRetentionSeconds());
            log.info("system_config_updated trashRetentionSeconds={}", request.getTrashRetentionSeconds());
        }
        if (request.getPrivateGracePeriodSeconds() != null) {
            if (request.getPrivateGracePeriodSeconds() < 1 || request.getPrivateGracePeriodSeconds() > 31536000L) {
                throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR,
                        "私密空间宽限期秒数必须在 1-31536000 之间（最大 365 天）");
            }
            properties.setPrivateGracePeriodSeconds(request.getPrivateGracePeriodSeconds());
            log.info("system_config_updated privateGracePeriodSeconds={}", request.getPrivateGracePeriodSeconds());
        }
        if (request.getCleanupIntervalSeconds() != null) {
            if (request.getCleanupIntervalSeconds() < 1 || request.getCleanupIntervalSeconds() > 86400) {
                throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR,
                        "清理间隔秒数必须在 1-86400 之间");
            }
            properties.setCleanupIntervalSeconds(request.getCleanupIntervalSeconds());
            rescheduleCleanupTrigger(request.getCleanupIntervalSeconds());
            log.info("system_config_updated cleanupIntervalSeconds={}", request.getCleanupIntervalSeconds());
        }
        if (request.getNormalTotalQuota() != null) {
            if (request.getNormalTotalQuota() <= 0) {
                throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR,
                        "普通用户总配额必须大于 0");
            }
            properties.setNormalTotalQuota(request.getNormalTotalQuota());
            userQuotaMapper.resetNormalUserQuota(request.getNormalTotalQuota());
            log.info("system_config_updated normalTotalQuota={}", request.getNormalTotalQuota());
        }
        if (request.getNormalSingleFileLimit() != null) {
            if (request.getNormalSingleFileLimit() <= 0) {
                throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR,
                        "单文件大小限制必须大于 0");
            }
            properties.setNormalSingleFileLimit(request.getNormalSingleFileLimit());
            log.info("system_config_updated normalSingleFileLimit={}", request.getNormalSingleFileLimit());
        }
        if (request.getDownloadThrottleThreshold() != null) {
            if (request.getDownloadThrottleThreshold() <= 0) {
                throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR,
                        "下载限速阈值必须大于 0");
            }
            properties.setDownloadThrottleThreshold(request.getDownloadThrottleThreshold());
            log.info("system_config_updated downloadThrottleThreshold={}", request.getDownloadThrottleThreshold());
        }
        if (request.getNormalDownloadBytesPerSecond() != null) {
            if (request.getNormalDownloadBytesPerSecond() <= 0) {
                throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR,
                        "下载限速必须大于 0");
            }
            properties.setNormalDownloadBytesPerSecond(request.getNormalDownloadBytesPerSecond());
            log.info("system_config_updated normalDownloadBytesPerSecond={}", request.getNormalDownloadBytesPerSecond());
        }
        return buildConfigVO();
    }

    // 触发回收站清理任务。
    @Override
    public String triggerCleanup() {
        TrashRetentionCleanupService.CleanupResult result = cleanupService.cleanupExpiredRetention();
        return String.format("deletedTrashRoots=%d, movedPrivateRoots=%d",
                result.deletedTrashRoots(), result.movedPrivateRoots());
    }

    private void rescheduleCleanupTrigger(long intervalSeconds) {
        try {
            TriggerKey key = TriggerKey.triggerKey("trashRetentionCleanupTrigger", "lab3");
            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(key)
                    .forJob(JobKey.jobKey("trashRetentionCleanupJob", "lab3"))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInMilliseconds(intervalSeconds * 1000)
                            .repeatForever())
                    .build();
            scheduler.rescheduleJob(key, newTrigger);
            log.info("cleanup_trigger_rescheduled intervalSeconds={}", intervalSeconds);
        } catch (Exception e) {
            log.warn("cleanup_trigger_reschedule_failed intervalSeconds={}", intervalSeconds, e);
        }
    }

    private SystemConfigResponseVO buildConfigVO() {
        return SystemConfigResponseVO.builder()
                .trashRetentionSeconds(properties.getTrashRetentionSeconds())
                .privateGracePeriodSeconds(properties.getPrivateGracePeriodSeconds())
                .cleanupIntervalSeconds(properties.getCleanupIntervalSeconds())
                .normalTotalQuota(FileUtils.formatFileSize(properties.getNormalTotalQuota()))
                .normalTotalQuotaBytes(properties.getNormalTotalQuota())
                .normalSingleFileLimit(FileUtils.formatFileSize(properties.getNormalSingleFileLimit()))
                .normalSingleFileLimitBytes(properties.getNormalSingleFileLimit())
                .downloadThrottleThreshold(FileUtils.formatFileSize(properties.getDownloadThrottleThreshold()))
                .downloadThrottleThresholdBytes(properties.getDownloadThrottleThreshold())
                .normalDownloadSpeed(FileUtils.formatFileSize(properties.getNormalDownloadBytesPerSecond()) + "/s")
                .normalDownloadBytesPerSecond(properties.getNormalDownloadBytesPerSecond())
                .yamlDefaults(Map.of(
                        "trashRetentionSeconds", "259200 (3天)",
                        "privateGracePeriodSeconds", "259200 (3天)",
                        "cleanupIntervalSeconds", "600",
                        "normalTotalQuota", "100 MB",
                        "normalSingleFileLimit", "100 MB",
                        "downloadThrottleThreshold", "5 MB",
                        "normalDownloadSpeed", "512 KB/s"))
                .build();
    }
}
