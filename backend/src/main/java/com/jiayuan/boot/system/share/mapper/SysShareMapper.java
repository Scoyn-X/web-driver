package com.jiayuan.boot.system.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiayuan.boot.system.share.model.entity.SysShare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件分享 Mapper 接口
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Mapper
public interface SysShareMapper extends BaseMapper<SysShare> {

    /**
     * 查询当前用户创建的个人分享。
     *
     * @param userId 当前用户ID
     * @return 个人分享列表
     */
    List<SysShare> selectPersonalShares(Long userId);

    /**
     * 查询团队全部分享。
     *
     * @param teamId 团队ID
     * @return 团队分享列表
     */
    List<SysShare> selectTeamShares(Long teamId);

    /**
     * 查询当前用户创建的个人分享记录。
     *
     * @param userId  当前用户ID
     * @param shareId 分享记录ID
     * @return 个人分享记录
     */
    SysShare selectPersonalShare(@Param("userId") Long userId,
                                 @Param("shareId") Long shareId);

    /**
     * 查询团队分享记录。
     *
     * @param teamId  团队ID
     * @param shareId 分享记录ID
     * @return 团队分享记录
     */
    SysShare selectTeamShare(@Param("teamId") Long teamId,
                             @Param("shareId") Long shareId);

    /**
     * 逻辑删除团队分享记录。
     *
     * @param teamId  团队ID
     * @param shareId 分享记录ID
     * @return 影响行数
     */
    int deleteTeamShare(@Param("teamId") Long teamId,
                        @Param("shareId") Long shareId);

    /**
     * 失效团队全部分享。
     *
     * @param teamId 团队ID
     * @return 影响行数
     */
    int invalidateTeamShares(Long teamId);

    /**
     * 失效指定成员创建的团队分享。
     *
     * @param teamId 团队ID
     * @param accountId 成员账户ID
     * @return 影响行数
     */
    int invalidateTeamSharesByCreator(@Param("teamId") Long teamId,
                                      @Param("accountId") Long accountId);

    /**
     * 根据分享 token 查询有效分享记录，支持个人与团队分享。
     *
     * @param shareToken 分享标识
     * @return 分享记录
     */
    SysShare selectShareByToken(String shareToken);

}
