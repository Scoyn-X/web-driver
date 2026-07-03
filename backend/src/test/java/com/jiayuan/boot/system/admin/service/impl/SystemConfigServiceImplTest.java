package com.jiayuan.boot.system.admin.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.admin.model.vo.SystemConfigResponseVO;
import com.jiayuan.boot.system.admin.model.vo.SystemConfigUpdateRequestVO;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.service.impl.TrashRetentionCleanupService;
import com.jiayuan.boot.system.quota.mapper.UserQuotaMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 系统配置服务单元测试。
 *
 * @author charleslam
 * @since 2026/05/24
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemConfigServiceImpl 单元测试")
class SystemConfigServiceImplTest {

    @Mock
    private TrashRetentionCleanupService cleanupService;
    @Mock
    private UserQuotaMapper userQuotaMapper;
    @Mock
    private SysFileMapper sysFileMapper;
    @Mock
    private Scheduler scheduler;

    @Test
    @DisplayName("更新清理间隔：重建的 Quartz 触发器保留 name/group")
    void updateConfig_cleanupInterval_reschedulesNamedTrigger() throws Exception {
        SystemConfigProperties properties = new SystemConfigProperties();
        SystemConfigServiceImpl service = newService(properties);
        when(scheduler.rescheduleJob(any(), any())).thenReturn(new Date());
        SystemConfigUpdateRequestVO request = new SystemConfigUpdateRequestVO();
        request.setCleanupIntervalSeconds(45L);

        service.updateConfig(request);

        TriggerKey expectedKey = TriggerKey.triggerKey("trashRetentionCleanupTrigger", "lab3");
        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(scheduler).rescheduleJob(eq(expectedKey), triggerCaptor.capture());
        assertThat(triggerCaptor.getValue().getKey()).isEqualTo(expectedKey);
    }

    @Test
    @DisplayName("更新普通用户总配额：同步运行时配置并批量重置普通用户配额")
    void updateConfig_normalTotalQuota_resetsNormalUsers() {
        SystemConfigProperties properties = new SystemConfigProperties();
        SystemConfigServiceImpl service = newService(properties);
        SystemConfigUpdateRequestVO request = new SystemConfigUpdateRequestVO();
        request.setNormalTotalQuota(200L * 1024 * 1024);

        SystemConfigResponseVO response = service.updateConfig(request);

        assertThat(properties.getNormalTotalQuota()).isEqualTo(200L * 1024 * 1024);
        assertThat(response.getNormalTotalQuotaBytes()).isEqualTo(200L * 1024 * 1024);
        verify(userQuotaMapper).resetNormalUserQuota(200L * 1024 * 1024);
    }

    @Test
    @DisplayName("读取配置：返回当前运行时配置和 YAML 默认说明")
    void getConfig_returnsCurrentProperties() {
        SystemConfigProperties properties = new SystemConfigProperties();
        properties.setTrashRetentionSeconds(60L);
        properties.setPrivateGracePeriodSeconds(120L);
        properties.setCleanupIntervalSeconds(30L);
        properties.setNormalTotalQuota(1024L);
        properties.setNormalSingleFileLimit(512L);
        properties.setDownloadThrottleThreshold(256L);
        properties.setNormalDownloadBytesPerSecond(128L);
        SystemConfigServiceImpl service = newService(properties);

        SystemConfigResponseVO response = service.getConfig();

        assertThat(response.getTrashRetentionSeconds()).isEqualTo(60L);
        assertThat(response.getPrivateGracePeriodSeconds()).isEqualTo(120L);
        assertThat(response.getCleanupIntervalSeconds()).isEqualTo(30L);
        assertThat(response.getNormalTotalQuotaBytes()).isEqualTo(1024L);
        assertThat(response.getNormalSingleFileLimitBytes()).isEqualTo(512L);
        assertThat(response.getDownloadThrottleThresholdBytes()).isEqualTo(256L);
        assertThat(response.getNormalDownloadBytesPerSecond()).isEqualTo(128L);
        assertThat(response.getYamlDefaults()).containsKey("trashRetentionSeconds");
    }

    @Test
    @DisplayName("更新回收站保留秒数：同步配置并批量刷新过期时间")
    void updateConfig_trashRetention_updatesRuntimeAndExpireAt() {
        SystemConfigProperties properties = new SystemConfigProperties();
        SystemConfigServiceImpl service = newService(properties);
        SystemConfigUpdateRequestVO request = new SystemConfigUpdateRequestVO();
        request.setTrashRetentionSeconds(86400L);

        SystemConfigResponseVO response = service.updateConfig(request);

        assertThat(properties.getTrashRetentionSeconds()).isEqualTo(86400L);
        assertThat(response.getTrashRetentionSeconds()).isEqualTo(86400L);
        verify(sysFileMapper).updateRecycleBinExpireAt(86400L);
    }

    @Test
    @DisplayName("更新清理间隔：Quartz 重建失败时仍返回最新配置")
    void updateConfig_cleanupInterval_rescheduleFailureDoesNotBlockUpdate() throws Exception {
        SystemConfigProperties properties = new SystemConfigProperties();
        SystemConfigServiceImpl service = newService(properties);
        doThrow(new org.quartz.SchedulerException("scheduler down"))
                .when(scheduler).rescheduleJob(any(), any());
        SystemConfigUpdateRequestVO request = new SystemConfigUpdateRequestVO();
        request.setCleanupIntervalSeconds(60L);

        SystemConfigResponseVO response = service.updateConfig(request);

        assertThat(properties.getCleanupIntervalSeconds()).isEqualTo(60L);
        assertThat(response.getCleanupIntervalSeconds()).isEqualTo(60L);
    }

    @Test
    @DisplayName("更新下载限速配置：响应返回最新阈值和普通用户限速")
    void updateConfig_downloadThrottle_returnsLatestBytes() {
        SystemConfigProperties properties = new SystemConfigProperties();
        SystemConfigServiceImpl service = newService(properties);
        SystemConfigUpdateRequestVO request = new SystemConfigUpdateRequestVO();
        request.setDownloadThrottleThreshold(8L * 1024 * 1024);
        request.setNormalDownloadBytesPerSecond(1024L * 1024);

        SystemConfigResponseVO response = service.updateConfig(request);

        assertThat(properties.getDownloadThrottleThreshold()).isEqualTo(8L * 1024 * 1024);
        assertThat(properties.getNormalDownloadBytesPerSecond()).isEqualTo(1024L * 1024);
        assertThat(response.getDownloadThrottleThresholdBytes()).isEqualTo(8L * 1024 * 1024);
        assertThat(response.getNormalDownloadBytesPerSecond()).isEqualTo(1024L * 1024);
        assertThat(response.getNormalDownloadSpeed()).isEqualTo("1.00 MB/s");
        verifyNoInteractions(userQuotaMapper, sysFileMapper, cleanupService, scheduler);
    }

    @Test
    @DisplayName("更新下载限速阈值为 0：拒绝且不触发任何持久化副作用")
    void updateConfig_zeroDownloadThrottleThreshold_rejectedWithoutSideEffects() {
        SystemConfigServiceImpl service = newService(new SystemConfigProperties());
        SystemConfigUpdateRequestVO request = new SystemConfigUpdateRequestVO();
        request.setDownloadThrottleThreshold(0L);

        assertThatThrownBy(() -> service.updateConfig(request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        verifyNoInteractions(userQuotaMapper, sysFileMapper, cleanupService, scheduler);
    }

    @Test
    @DisplayName("参数校验：越界配置拒绝且不触发持久化副作用")
    void updateConfig_invalidValuesRejectedWithoutSideEffects() {
        assertInvalidRequest(request -> request.setTrashRetentionSeconds(0L));
        assertInvalidRequest(request -> request.setPrivateGracePeriodSeconds(0L));
        assertInvalidRequest(request -> request.setCleanupIntervalSeconds(0L));
        assertInvalidRequest(request -> request.setNormalTotalQuota(0L));
        assertInvalidRequest(request -> request.setNormalSingleFileLimit(0L));
        assertInvalidRequest(request -> request.setNormalDownloadBytesPerSecond(0L));
        verifyNoInteractions(userQuotaMapper, sysFileMapper, cleanupService, scheduler);
    }

    @Test
    @DisplayName("手动触发清理：返回已删除和已移入回收站的根节点数量")
    void triggerCleanup_returnsCleanupSummary() {
        SystemConfigServiceImpl service = newService(new SystemConfigProperties());
        when(cleanupService.cleanupExpiredRetention())
                .thenReturn(new TrashRetentionCleanupService.CleanupResult(2, 1));

        String result = service.triggerCleanup();

        assertThat(result).isEqualTo("deletedTrashRoots=2, movedPrivateRoots=1");
    }

    private SystemConfigServiceImpl newService(SystemConfigProperties properties) {
        return new SystemConfigServiceImpl(
                properties, cleanupService, userQuotaMapper, sysFileMapper, scheduler);
    }

    private void assertInvalidRequest(java.util.function.Consumer<SystemConfigUpdateRequestVO> customizer) {
        SystemConfigServiceImpl service = newService(new SystemConfigProperties());
        SystemConfigUpdateRequestVO request = new SystemConfigUpdateRequestVO();
        customizer.accept(request);

        assertThatThrownBy(() -> service.updateConfig(request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }
}
