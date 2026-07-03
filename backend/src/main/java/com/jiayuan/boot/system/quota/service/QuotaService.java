package com.jiayuan.boot.system.quota.service;

import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;

/**
 * 配额管理服务接口
 *
 * @author didongchen
 * @since 2026/04/13
 */
public interface QuotaService {

    /**
     * 初始化用户配额（注册时调用）
     *
     * @param userId 用户ID
     */
    void initQuota(Long userId);

    /**
     * 获取当前用户配额信息
     *
     * @return 配额信息
     */
    QuotaResponseVO getQuotaInfo();

    /**
     * 获取指定用户配额信息。
     *
     * @param userId 用户ID
     * @return 配额信息
     */
    QuotaResponseVO getQuotaInfo(Long userId);

    /**
     * 校验配额是否充足
     *
     * @param userId   用户ID
     * @param fileSize 需要的空间大小（字节）
     */
    void checkQuota(Long userId, long fileSize);

    /**
     * 校验单文件大小是否满足当前用户身份限制。
     *
     * @param userId   用户ID
     * @param fileSize 文件大小（字节）
     */
    void checkSingleFileLimit(Long userId, long fileSize);

    /**
     * 切换用户 VIP 状态对应的个人配额规格。
     *
     * @param userId 用户ID
     * @param vip    是否切换为 VIP
     */
    void setVipState(Long userId, boolean vip);

    /**
     * 判断用户是否为 VIP 配额规格。
     *
     * @param userId 用户ID
     * @return 是否为 VIP
     */
    boolean isVip(Long userId);

    /**
     * 增加已使用空间
     *
     * @param userId   用户ID
     * @param fileSize 文件大小（字节）
     */
    void increaseUsedSpace(Long userId, long fileSize);

    /**
     * 归还已使用空间
     *
     * @param userId   用户ID
     * @param fileSize 文件大小（字节）
     */
    void decreaseUsedSpace(Long userId, long fileSize);

}
