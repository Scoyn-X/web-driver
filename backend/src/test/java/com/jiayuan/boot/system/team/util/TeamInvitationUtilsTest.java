package com.jiayuan.boot.system.team.util;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.system.team.model.enums.InvitationStatus;
import com.jiayuan.boot.system.team.model.enums.MemberRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 团队邀请工具测试。
 *
 * @author charleslam
 * @since 2026/05/20
 */
class TeamInvitationUtilsTest {

    @Test
    void normalizeStatusShouldTrimUpperCaseAndKeepBlankAsNull() {
        assertThat(TeamInvitationUtils.normalizeStatus(null)).isNull();
        assertThat(TeamInvitationUtils.normalizeStatus("  ")).isNull();
        assertThat(TeamInvitationUtils.normalizeStatus(" pending "))
                .isEqualTo(InvitationStatus.PENDING.getValue());
    }

    @Test
    void pendingStatusShouldUseInvitationStatusEnum() {
        assertThat(TeamInvitationUtils.pendingStatus()).isEqualTo(InvitationStatus.PENDING.getValue());
    }

    @Test
    void resolveTargetRoleShouldReturnInviteRole() {
        assertThat(TeamInvitationUtils.resolveTargetRole(MemberRole.Editor.getValue()))
                .isEqualTo(MemberRole.Editor);
    }

    @Test
    void resolveTargetRoleShouldRejectEmptyRole() {
        assertThatThrownBy(() -> TeamInvitationUtils.resolveTargetRole(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标角色不能为空");
    }

    @Test
    void resolveTargetRoleShouldRejectInvalidRole() {
        assertThatThrownBy(() -> TeamInvitationUtils.resolveTargetRole("Invalid"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标角色不合法");
    }

    @Test
    void resolveTargetRoleShouldRejectOwnerRole() {
        assertThatThrownBy(() -> TeamInvitationUtils.resolveTargetRole(MemberRole.Owner.getValue()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("不能邀请为 Owner");
    }
}
