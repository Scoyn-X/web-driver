package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.team.mapper.TeamSpaceMapper;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamQuotaServiceImpl 单元测试")
class TeamQuotaServiceImplTest {

    @Mock private TeamSpaceMapper teamSpaceMapper;
    @Mock private QuotaService quotaService;

    @InjectMocks
    private TeamQuotaServiceImpl teamQuotaService;

    @Test
    @DisplayName("团队配额充足：不抛异常")
    void checkQuota_sufficient() {
        when(teamSpaceMapper.selectById(1L)).thenReturn(buildTeam(1000L, 100L));

        teamQuotaService.checkQuota(1L, 900L);
        // no exception
    }

    @Test
    @DisplayName("团队配额不足：抛 USER_QUOTA_EXHAUSTED")
    void checkQuota_exhausted() {
        when(teamSpaceMapper.selectById(1L)).thenReturn(buildTeam(1000L, 950L));

        assertThatThrownBy(() -> teamQuotaService.checkQuota(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_QUOTA_EXHAUSTED);
    }

    @Test
    @DisplayName("团队已用容量超过降级后上限：新增容量校验被拦截")
    void checkQuota_overLimitAfterDowngrade_throws() {
        when(teamSpaceMapper.selectById(1L)).thenReturn(buildTeam(1000L, 1200L));

        assertThatThrownBy(() -> teamQuotaService.checkQuota(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_QUOTA_EXHAUSTED);
    }

    @Test
    @DisplayName("VIP 团队无限容量：新增容量校验不被团队上限拦截")
    void checkQuota_vipUnlimited() {
        when(teamSpaceMapper.selectById(1L)).thenReturn(buildTeam(Long.MAX_VALUE, Long.MAX_VALUE / 2));

        teamQuotaService.checkQuota(1L, Long.MAX_VALUE / 3);
        // no exception
    }

    @Test
    @DisplayName("团队彻底删除释放配额")
    void decreaseUsedSpace_success() {
        TeamSpace team = buildTeam(1000L, 400L);
        when(teamSpaceMapper.selectById(1L)).thenReturn(team);
        when(teamSpaceMapper.decreaseUsedSpace(1L, 500L)).thenReturn(1);

        teamQuotaService.decreaseUsedSpace(1L, 500L);

        assertThat(team.getUsedSpace()).isEqualTo(400L);
        verify(teamSpaceMapper).decreaseUsedSpace(1L, 500L);
        verify(teamSpaceMapper, never()).updateById(team);
    }

    @Test
    @DisplayName("团队上传占用配额使用原子更新")
    void increaseUsedSpace_success() {
        TeamSpace team = buildTeam(1000L, 400L);
        when(teamSpaceMapper.selectById(1L)).thenReturn(team);
        when(teamSpaceMapper.increaseUsedSpace(1L, 200L)).thenReturn(1);

        teamQuotaService.increaseUsedSpace(1L, 200L);

        assertThat(team.getUsedSpace()).isEqualTo(400L);
        verify(teamSpaceMapper).increaseUsedSpace(1L, 200L);
        verify(teamSpaceMapper, never()).updateById(team);
    }

    @Test
    @DisplayName("团队容量规格：普通团队为 1GB，VIP 团队为无限")
    void resolveTeamTotalQuota_byVipState() {
        assertThat(teamQuotaService.resolveTeamTotalQuota(false)).isEqualTo(1073741824L);
        assertThat(teamQuotaService.resolveTeamTotalQuota(true)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("按 Owner VIP 状态解析团队容量")
    void resolveOwnerTeamTotalQuota_byOwnerVip() {
        when(quotaService.isVip(7L)).thenReturn(true);

        assertThat(teamQuotaService.resolveOwnerTeamTotalQuota(7L)).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("Owner 降级普通用户：批量同步其团队容量且不读取或修改 usedSpace")
    void syncOwnerTeamTotalQuota_downgradeUpdatesOwnerTeamsOnly() {
        teamQuotaService.syncOwnerTeamTotalQuota(7L, false);

        verify(teamSpaceMapper).updateTotalQuotaByOwner(7L, 1073741824L);
        verify(teamSpaceMapper, never()).selectById(1L);
    }

    @Test
    @DisplayName("Owner 升级 VIP：批量同步其团队容量为无限")
    void syncOwnerTeamTotalQuota_upgradeUpdatesOwnerTeamsToUnlimited() {
        teamQuotaService.syncOwnerTeamTotalQuota(7L, true);

        verify(teamSpaceMapper).updateTotalQuotaByOwner(7L, Long.MAX_VALUE);
    }

    @Test
    @DisplayName("Owner 转让后：按新 Owner VIP 状态同步单个团队容量")
    void syncTeamTotalQuotaByOwner_usesNewOwnerVipState() {
        when(quotaService.isVip(8L)).thenReturn(true);
        when(teamSpaceMapper.updateTotalQuota(1L, Long.MAX_VALUE)).thenReturn(1);

        teamQuotaService.syncTeamTotalQuotaByOwner(1L, 8L);

        verify(teamSpaceMapper).updateTotalQuota(1L, Long.MAX_VALUE);
    }

    @Test
    @DisplayName("Owner 转让后同步容量：团队不存在时抛资源不存在")
    void syncTeamTotalQuotaByOwner_missingTeamThrows() {
        when(quotaService.isVip(8L)).thenReturn(false);
        when(teamSpaceMapper.updateTotalQuota(1L, 1073741824L)).thenReturn(0);

        assertThatThrownBy(() -> teamQuotaService.syncTeamTotalQuotaByOwner(1L, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("并发占用后配额不足：原子更新失败抛 USER_QUOTA_EXHAUSTED")
    void increaseUsedSpace_atomicQuotaExceeded_throws() {
        TeamSpace team = buildTeam(1000L, 900L);
        when(teamSpaceMapper.selectById(1L)).thenReturn(team);
        when(teamSpaceMapper.increaseUsedSpace(1L, 200L)).thenReturn(0);

        assertThatThrownBy(() -> teamQuotaService.increaseUsedSpace(1L, 200L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_QUOTA_EXHAUSTED);

        verify(teamSpaceMapper).increaseUsedSpace(1L, 200L);
        verify(teamSpaceMapper, never()).updateById(team);
    }

    @Test
    @DisplayName("团队配额记录不存在：校验容量抛资源不存在")
    void checkQuota_missingTeamThrows() {
        when(teamSpaceMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> teamQuotaService.checkQuota(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("团队配额字段缺失：校验容量抛资源不存在")
    void checkQuota_missingQuotaFieldsThrows() {
        when(teamSpaceMapper.selectById(1L)).thenReturn(buildTeam(null, 1L));

        assertThatThrownBy(() -> teamQuotaService.checkQuota(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("减少团队已用容量：原子更新失败抛团队不存在")
    void decreaseUsedSpace_atomicFailureThrows() {
        TeamSpace team = buildTeam(1000L, 400L);
        when(teamSpaceMapper.selectById(1L)).thenReturn(team);
        when(teamSpaceMapper.decreaseUsedSpace(1L, 500L)).thenReturn(0);

        assertThatThrownBy(() -> teamQuotaService.decreaseUsedSpace(1L, 500L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    private TeamSpace buildTeam(Long totalQuota, Long usedSpace) {
        TeamSpace team = new TeamSpace();
        team.setId(1L);
        team.setTotalQuota(totalQuota);
        team.setUsedSpace(usedSpace);
        return team;
    }
}
