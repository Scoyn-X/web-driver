package com.jiayuan.boot.system.user.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.privatespace.service.PrivateSpaceService;
import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;
import com.jiayuan.boot.system.quota.service.QuotaService;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用户服务单元测试
 *
 * @author charleslam
 * @since 2026/05/16
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 单元测试")
class UserServiceImplTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long CURRENT_ACCOUNT_ID = 10L;

    @Mock private SysUserMapper sysUserMapper;
    @Mock private QuotaService quotaService;
    @Mock private UserConverter userConverter;
    @Mock private PrivateSpaceService privateSpaceService;
    @Mock private TeamQuotaService teamQuotaService;

    @InjectMocks
    private UserServiceImpl userService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(CURRENT_USER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(CURRENT_ACCOUNT_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Nested
    @DisplayName("getCurrentUser")
    class GetCurrentUserTests {

        @Test
        @DisplayName("返回当前用户、VIP 状态和个人配额")
        void getCurrentUser_success() {
            UserBriefBO brief = buildBrief(CURRENT_USER_ID, "charles");
            QuotaResponseVO quota = new QuotaResponseVO();
            CurrentUserResponseVO expected = new CurrentUserResponseVO();
            when(sysUserMapper.selectUserBriefByAccountId(CURRENT_ACCOUNT_ID)).thenReturn(brief);
            when(quotaService.getQuotaInfo(CURRENT_USER_ID)).thenReturn(quota);
            when(quotaService.isVip(CURRENT_USER_ID)).thenReturn(true);
            when(userConverter.toCurrentUserResponseVO(brief, VipState.VIP, quota, null)).thenReturn(expected);

            CurrentUserResponseVO result = userService.getCurrentUser();

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("用户不存在：抛 USER_RESOURCE_NOT_FOUND")
        void getCurrentUser_notFound() {
            when(sysUserMapper.selectUserBriefByAccountId(CURRENT_ACCOUNT_ID)).thenReturn(null);

            assertThatThrownBy(() -> userService.getCurrentUser())
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("searchUsers")
    class SearchUsersTests {

        @Test
        @DisplayName("按关键词搜索正式用户")
        void searchUsers_success() {
            UserBriefBO brief = buildBrief(2L, "teammate");
            UserBriefResponseVO vo = new UserBriefResponseVO();
            when(sysUserMapper.selectActiveUserBriefs("team")).thenReturn(List.of(brief));
            when(userConverter.toUserBriefResponseVO(brief)).thenReturn(vo);

            List<UserBriefResponseVO> result = userService.searchUsers(" team ");

            assertThat(result).containsExactly(vo);
        }

        @Test
        @DisplayName("搜索关键词为空：抛 REQUEST_REQUIRED_PARAMETER_IS_EMPTY")
        void searchUsers_blankKeyword() {
            assertThatThrownBy(() -> userService.searchUsers(" "))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY);
        }
    }

    @Nested
    @DisplayName("updateVip")
    class UpdateVipTests {

        @Test
        @DisplayName("当前用户可以切换自己的 VIP 状态")
        void updateVip_success() {
            VipUpdateRequestVO request = new VipUpdateRequestVO();
            request.setVip(true);
            UserBriefBO brief = buildBrief(CURRENT_USER_ID, "vip-user");
            UserVipProfileBO profile = new UserVipProfileBO();
            profile.setVipState(VipState.VIP);
            profile.setSingleFileLimit(null);
            UserVipResponseVO expected = new UserVipResponseVO();
            UserVipProfileBuildBO build = new UserVipProfileBuildBO(
                    CURRENT_USER_ID, VipState.VIP, null, null, null);
            when(sysUserMapper.selectUserBriefById(CURRENT_USER_ID)).thenReturn(brief);
            when(userConverter.toUserVipProfileBO(build)).thenReturn(profile);
            when(userConverter.toUserVipResponseVO(profile)).thenReturn(expected);

            UserVipResponseVO result = userService.updateVip(CURRENT_USER_ID, request);

            assertThat(result).isSameAs(expected);
            verify(quotaService).setVipState(CURRENT_USER_ID, true);
            verify(teamQuotaService).syncOwnerTeamTotalQuota(CURRENT_USER_ID, true);
            verify(privateSpaceService).handleVipStateChanged(CURRENT_USER_ID, true);
            verify(userConverter).toUserVipProfileBO(build);
            verify(userConverter).toUserVipResponseVO(profile);
        }

        @Test
        @DisplayName("降级普通用户时启动私密空间宽限期")
        void updateVip_downgradeStartsPrivateSpaceGracePeriod() {
            VipUpdateRequestVO request = new VipUpdateRequestVO();
            request.setVip(false);
            UserBriefBO brief = buildBrief(CURRENT_USER_ID, "normal-user");
            UserVipProfileBO profile = new UserVipProfileBO();
            UserVipResponseVO expected = new UserVipResponseVO();
            UserVipProfileBuildBO build = new UserVipProfileBuildBO(
                    CURRENT_USER_ID, VipState.NORMAL, 104857600L, 1073741824L, 104857600L);
            when(sysUserMapper.selectUserBriefById(CURRENT_USER_ID)).thenReturn(brief);
            when(quotaService.isVip(CURRENT_USER_ID)).thenReturn(true);
            when(teamQuotaService.resolveTeamTotalQuota(false)).thenReturn(1073741824L);
            when(userConverter.toUserVipProfileBO(build)).thenReturn(profile);
            when(userConverter.toUserVipResponseVO(profile)).thenReturn(expected);

            UserVipResponseVO result = userService.updateVip(CURRENT_USER_ID, request);

            assertThat(result).isSameAs(expected);
            verify(quotaService).setVipState(CURRENT_USER_ID, false);
            verify(teamQuotaService).syncOwnerTeamTotalQuota(CURRENT_USER_ID, false);
            verify(privateSpaceService).handleVipStateChanged(CURRENT_USER_ID, false);
        }

        @Test
        @DisplayName("VIP 状态未变化：仍同步 Owner 团队容量以修复历史配额")
        void updateVip_sameStateStillSyncsOwnerTeamQuota() {
            VipUpdateRequestVO request = new VipUpdateRequestVO();
            request.setVip(true);
            UserBriefBO brief = buildBrief(CURRENT_USER_ID, "vip-user");
            UserVipProfileBO profile = new UserVipProfileBO();
            UserVipResponseVO expected = new UserVipResponseVO();
            UserVipProfileBuildBO build = new UserVipProfileBuildBO(
                    CURRENT_USER_ID, VipState.VIP, null, null, null);
            when(sysUserMapper.selectUserBriefById(CURRENT_USER_ID)).thenReturn(brief);
            when(quotaService.isVip(CURRENT_USER_ID)).thenReturn(true);
            when(userConverter.toUserVipProfileBO(build)).thenReturn(profile);
            when(userConverter.toUserVipResponseVO(profile)).thenReturn(expected);

            userService.updateVip(CURRENT_USER_ID, request);

            verify(quotaService).setVipState(CURRENT_USER_ID, true);
            verify(teamQuotaService).syncOwnerTeamTotalQuota(CURRENT_USER_ID, true);
            verifyNoInteractions(privateSpaceService);
        }

        @Test
        @DisplayName("不能切换其他用户的 VIP 状态")
        void updateVip_otherUserDenied() {
            VipUpdateRequestVO request = new VipUpdateRequestVO();
            request.setVip(false);

            assertThatThrownBy(() -> userService.updateVip(2L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.NO_PERMISSION_TO_USE_API);
            verifyNoInteractions(sysUserMapper, quotaService, userConverter, teamQuotaService);
        }
    }

    private UserBriefBO buildBrief(Long userId, String accountName) {
        UserBriefBO brief = new UserBriefBO();
        brief.setUserId(userId);
        brief.setAccountId(CURRENT_ACCOUNT_ID);
        brief.setAccountName(accountName);
        brief.setNickname(accountName);
        brief.setEmail(accountName + "@example.com");
        return brief;
    }
}
