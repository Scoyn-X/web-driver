package com.jiayuan.boot.system.team.service;

/**
 * 团队配额服务接口
 *
 * @author charleslam
 * @since 2026/05/16
 */
public interface TeamQuotaService {

    /**
     * 根据 VIP 状态解析团队总容量规格。
     *
     * @param vip Owner 是否为 VIP
     * @return 团队总容量
     */
    Long resolveTeamTotalQuota(boolean vip);

    /**
     * 根据 Owner 当前 VIP 状态解析团队总容量规格。
     *
     * @param ownerId Owner 用户ID
     * @return 团队总容量
     */
    Long resolveOwnerTeamTotalQuota(Long ownerId);

    /**
     * 同步指定 Owner 拥有的所有团队容量规格。
     *
     * @param ownerId Owner 用户ID
     * @param vip     Owner 是否为 VIP
     */
    void syncOwnerTeamTotalQuota(Long ownerId, boolean vip);

    /**
     * 按当前 Owner 身份同步单个团队容量规格。
     *
     * @param teamId  团队ID
     * @param ownerId Owner 用户ID
     */
    void syncTeamTotalQuotaByOwner(Long teamId, Long ownerId);

    /**
     * 校验团队剩余配额是否充足。
     *
     * @param teamId 团队ID
     * @param size   需要新增的容量
     */
    void checkQuota(Long teamId, long size);

    /**
     * 增加团队已用容量。
     *
     * @param teamId 团队ID
     * @param size   增加容量
     */
    void increaseUsedSpace(Long teamId, long size);

    /**
     * 释放团队已用容量。
     *
     * @param teamId 团队ID
     * @param size   释放容量
     */
    void decreaseUsedSpace(Long teamId, long size);
}
