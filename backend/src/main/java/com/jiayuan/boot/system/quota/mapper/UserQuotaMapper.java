package com.jiayuan.boot.system.quota.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiayuan.boot.system.quota.model.entity.UserQuota;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户配额 Mapper 接口
 *
 * @author didongchen
 * @since 2026/04/13
 */
@Mapper
public interface UserQuotaMapper extends BaseMapper<UserQuota> {

    /**
     * 原子增加用户已用空间；非 VIP 用户同时校验不超过总配额。
     *
     * @param userId 用户ID
     * @param size   增加空间大小（字节）
     * @return 影响行数
     */
    int increaseUsedSpace(@Param("userId") Long userId, @Param("size") long size);

    /**
     * 原子归还用户已用空间，最小归零。
     *
     * @param userId 用户ID
     * @param size   归还空间大小（字节）
     * @return 影响行数
     */
    int decreaseUsedSpace(@Param("userId") Long userId, @Param("size") long size);

    /**
     * 重置所有非 VIP 普通用户的总配额。
     *
     * @param newQuota 新配额大小（字节）
     * @return 影响行数
     */
    int resetNormalUserQuota(@Param("newQuota") long newQuota);

}
