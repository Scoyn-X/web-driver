package com.jiayuan.boot.system.oss.config;

import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.oss.service.impl.TrashRetentionCleanupService;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;
import org.springframework.scheduling.quartz.SimpleTriggerFactoryBean;

/**
 * 回收站保留期 Quartz 定时任务配置。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Configuration
@EnableConfigurationProperties(TrashRetentionProperties.class)
public class TrashRetentionQuartzConfig {

    /**
     * 创建回收站清理 JobDetail。
     *
     * @param cleanupService 清理服务
     * @return Quartz JobDetail 工厂
     */
    @Bean
    public MethodInvokingJobDetailFactoryBean trashRetentionCleanupJobDetail(
            TrashRetentionCleanupService cleanupService) {
        MethodInvokingJobDetailFactoryBean factory = new MethodInvokingJobDetailFactoryBean();
        factory.setName("trashRetentionCleanupJob");
        factory.setGroup("lab3");
        factory.setTargetObject(cleanupService);
        factory.setTargetMethod("cleanupExpiredRetention");
        factory.setConcurrent(false);
        return factory;
    }

    /**
     * 创建回收站清理触发器。
     *
     * @param trashRetentionCleanupJobDetail 清理 JobDetail
     * @param configProperties               系统运行时可调配置
     * @return Quartz Trigger 工厂
     */
    @Bean
    public SimpleTriggerFactoryBean trashRetentionCleanupTrigger(
            JobDetail trashRetentionCleanupJobDetail,
            SystemConfigProperties configProperties) {
        SimpleTriggerFactoryBean factory = new SimpleTriggerFactoryBean();
        factory.setName("trashRetentionCleanupTrigger");
        factory.setGroup("lab3");
        factory.setJobDetail(trashRetentionCleanupJobDetail);
        factory.setRepeatInterval(configProperties.getCleanupIntervalSeconds() * 1000);
        factory.setRepeatCount(SimpleTrigger.REPEAT_INDEFINITELY);
        return factory;
    }
}
