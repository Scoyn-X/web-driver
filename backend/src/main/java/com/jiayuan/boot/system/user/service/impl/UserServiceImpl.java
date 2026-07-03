package com.jiayuan.boot.system.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.privatespace.service.PrivateSpaceService;
import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.quota.service.impl.QuotaServiceImpl;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.service.TeamQuotaService;
import com.jiayuan.boot.system.user.converter.UserConverter;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import com.jiayuan.boot.system.user.model.bo.UserVipProfileBO;
import com.jiayuan.boot.system.user.model.bo.UserVipProfileBuildBO;
import com.jiayuan.boot.system.user.model.enums.VipState;
import com.jiayuan.boot.system.user.model.vo.CurrentUserResponseVO;
import com.jiayuan.boot.system.user.model.vo.UserBriefResponseVO;
import com.jiayuan.boot.system.user.model.vo.UserVipResponseVO;
import com.jiayuan.boot.system.user.model.vo.VipUpdateRequestVO;
import com.jiayuan.boot.system.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户信息与 VIP 服务实现
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final QuotaService quotaService;
    private final UserConverter userConverter;
    private final PrivateSpaceService privateSpaceService;
    private final TeamQuotaService teamQuotaService;

    /**
     * 查询当前登录用户信息。
     *
     * @return 当前用户响应信息
     */
    @Override
    public CurrentUserResponseVO getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        Long accountId = SecurityUtils.getCurrentAccountId();
        UserBriefBO brief = requireAccountBrief(accountId);
        QuotaResponseVO quota = quotaService.getQuotaInfo(userId);
        VipState vipState = toVipState(quotaService.isVip(userId));
        return userConverter.toCurrentUserResponseVO(brief, vipState, quota, null);
    }

    /**
     * 按关键词搜索用户。
     *
     * @param keyword 昵称、邮箱或用户名关键词
     * @return 用户摘要列表
     */
    @Override
    public List<UserBriefResponseVO> searchUsers(String keyword) {
        String trimmedKeyword = StrUtil.trim(keyword);
        if (StrUtil.isBlank(trimmedKeyword)) {
            throw new BusinessException(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY, "搜索关键词不能为空");
        }
        return sysUserMapper.selectActiveUserBriefs(trimmedKeyword).stream()
                .map(userConverter::toUserBriefResponseVO)
                .toList();
    }

    /**
     * 更新当前用户 VIP 状态。
     *
     * @param userId  用户 ID
     * @param request VIP 更新请求
     * @return VIP 状态响应
     */
    @Override
    @Transactional
    public UserVipResponseVO updateVip(Long userId, VipUpdateRequestVO request) {
        requireSelfOperation(userId);
        requireUserBrief(userId);
        boolean vip = Boolean.TRUE.equals(request.getVip());
        boolean previousVip = quotaService.isVip(userId);
        quotaService.setVipState(userId, vip);
        teamQuotaService.syncOwnerTeamTotalQuota(userId, vip);
        if (previousVip != vip) {
            privateSpaceService.handleVipStateChanged(userId, vip);
        }
        return userConverter.toUserVipResponseVO(buildVipProfile(userId, vip));
    }

    /**
     * 校验只能切换自己的 VIP 状态。
     */
    private void requireSelfOperation(Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!currentUserId.equals(userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "只能切换当前用户的 VIP 状态");
        }
    }

    /**
     * 获取用户基础信息，不存在时抛业务异常。
     */
    private UserBriefBO requireUserBrief(Long userId) {
        UserBriefBO brief = sysUserMapper.selectUserBriefById(userId);
        if (brief == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "用户不存在");
        }
        return brief;
    }

    /**
     * 获取账户基础信息，不存在时抛业务异常。
     */
    private UserBriefBO requireAccountBrief(Long accountId) {
        UserBriefBO brief = sysUserMapper.selectUserBriefByAccountId(accountId);
        if (brief == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "账户不存在");
        }
        return brief;
    }

    /**
     * 构造 VIP 限制信息。
     */
    private UserVipProfileBO buildVipProfile(Long userId, boolean vip) {
        VipState vipState = toVipState(vip);
        Long personalQuotaLimit = vip ? null : QuotaServiceImpl.NORMAL_TOTAL_QUOTA;
        Long teamQuotaLimit = vip ? null : teamQuotaService.resolveTeamTotalQuota(false);
        Long singleFileLimit = vip ? null : QuotaServiceImpl.NORMAL_SINGLE_FILE_LIMIT;
        UserVipProfileBuildBO build = new UserVipProfileBuildBO(
                userId, vipState, personalQuotaLimit, teamQuotaLimit, singleFileLimit);
        return userConverter.toUserVipProfileBO(build);
    }

    /**
     * 转换 VIP 状态枚举。
     */
    private VipState toVipState(boolean vip) {
        return vip ? VipState.VIP : VipState.NORMAL;
    }
}
