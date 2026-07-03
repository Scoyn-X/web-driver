package com.jiayuan.boot.system.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiayuan.boot.system.auth.model.entity.SysUser;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户 Mapper 接口
 *
 * @author jiayuan
 * @since 2026/04/09
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户基础信息。
     *
     * @param userId 用户ID
     * @return 用户基础信息
     */
    UserBriefBO selectUserBriefById(Long userId);

    /**
     * 根据账户ID查询用户基础信息。
     *
     * @param accountId 账户ID
     * @return 用户基础信息
     */
    UserBriefBO selectUserBriefByAccountId(Long accountId);

    /**
     * 搜索启用状态的用户基础信息。
     *
     * @param keyword 搜索关键词
     * @return 用户基础信息列表
     */
    List<UserBriefBO> selectActiveUserBriefs(String keyword);

    /**
     * 批量查询用户基础信息（含主账户名）。
     *
     * @param userIds 用户ID列表
     * @return 用户基础信息列表
     */
    List<UserBriefBO> selectUserBriefByIds(List<Long> userIds);
}
