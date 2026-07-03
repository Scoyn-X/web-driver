package com.jiayuan.boot.system.admin.service;

import com.jiayuan.boot.system.admin.model.vo.SystemConfigUpdateRequestVO;
import com.jiayuan.boot.system.admin.model.vo.SystemConfigResponseVO;

/**
 * 系统配置管理服务接口。
 *
 * @author charleslam
 * @since 2026/05/22
 */
public interface SystemConfigService {

    /**
     * 获取当前系统配置。
     *
     * @return 系统配置
     */
    SystemConfigResponseVO getConfig();

    /**
     * 更新系统配置。
     *
     * @param request 更新请求
     * @return 更新后的系统配置
     */
    SystemConfigResponseVO updateConfig(SystemConfigUpdateRequestVO request);

    /**
     * 触发回收站清理任务（立即执行一次）。
     *
     * @return 清理结果描述
     */
    String triggerCleanup();
}
