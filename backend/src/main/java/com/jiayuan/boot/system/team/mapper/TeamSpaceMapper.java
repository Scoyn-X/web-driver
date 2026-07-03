package com.jiayuan.boot.system.team.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 团队空间 Mapper 接口
 *
 * @author didongchen
 * @since 2026/05/14
 */
@Mapper
public interface TeamSpaceMapper extends BaseMapper<TeamSpace> {

    /**
     * 原子增加团队已用容量。
     *
     * @param teamId 团队ID
     * @param size   增加容量
     * @return 影响行数
     */
    int increaseUsedSpace(@Param("teamId") Long teamId,
                          @Param("size") Long size);

    /**
     * 原子减少团队已用容量。
     *
     * @param teamId 团队ID
     * @param size   减少容量
     * @return 影响行数
     */
    int decreaseUsedSpace(@Param("teamId") Long teamId,
                          @Param("size") Long size);

    /**
     * 更新指定 Owner 拥有的团队容量规格。
     *
     * @param ownerId    Owner 用户ID
     * @param totalQuota 团队总配额
     * @return 影响行数
     */
    int updateTotalQuotaByOwner(@Param("ownerId") Long ownerId,
                                @Param("totalQuota") Long totalQuota);

    /**
     * 更新单个团队容量规格。
     *
     * @param teamId     团队ID
     * @param totalQuota 团队总配额
     * @return 影响行数
     */
    int updateTotalQuota(@Param("teamId") Long teamId,
                         @Param("totalQuota") Long totalQuota);

    /**
     * 锁定正常团队空间行，用于串行化团队文件变更。
     *
     * @param teamId 团队ID
     * @return 团队ID
     */
    Long lockActiveTeamSpace(Long teamId);

    /**
     * 更新团队状态。
     *
     * @param teamId 团队ID
     * @param status 目标状态
     * @return 影响行数
     */
    int updateTeamStatus(@Param("teamId") Long teamId,
                         @Param("status") String status);

    /**
     * 更新团队 Owner。
     *
     * @param teamId         团队ID
     * @param ownerId        Owner 用户ID
     * @param ownerAccountId Owner 账户ID
     * @return 影响行数
     */
    int updateOwner(@Param("teamId") Long teamId,
                    @Param("ownerId") Long ownerId,
                    @Param("ownerAccountId") Long ownerAccountId);

    /**
     * 查询所有正常团队ID。
     *
     * @return 正常团队ID列表
     */
    default List<Long> selectActiveTeamIds() {
        return selectList(new QueryWrapper<TeamSpace>()
                        .select("id")
                        .eq("status", "ACTIVE"))
                .stream()
                .map(TeamSpace::getId)
                .toList();
    }

}
