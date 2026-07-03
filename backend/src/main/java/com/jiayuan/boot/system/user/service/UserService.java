package com.jiayuan.boot.system.user.service;

import com.jiayuan.boot.system.user.model.vo.CurrentUserResponseVO;
import com.jiayuan.boot.system.user.model.vo.UserBriefResponseVO;
import com.jiayuan.boot.system.user.model.vo.UserVipResponseVO;
import com.jiayuan.boot.system.user.model.vo.VipUpdateRequestVO;

import java.util.List;

/**
 * 用户信息与 VIP 服务接口
 *
 * @author charleslam
 * @since 2026/05/16
 */
public interface UserService {

    /**
     * 获取当前登录用户信息。
     *
     * @return 当前用户信息
     */
    CurrentUserResponseVO getCurrentUser();

    /**
     * 搜索可邀请用户。
     *
     * @param keyword 搜索关键词
     * @return 用户基础信息列表
     */
    List<UserBriefResponseVO> searchUsers(String keyword);

    /**
     * 切换用户 VIP 状态。
     *
     * @param userId  用户ID
     * @param request VIP 状态切换请求
     * @return 用户 VIP 状态
     */
    UserVipResponseVO updateVip(Long userId, VipUpdateRequestVO request);
}
