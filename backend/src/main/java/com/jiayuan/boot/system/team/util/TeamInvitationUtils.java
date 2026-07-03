package com.jiayuan.boot.system.team.util;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.team.model.enums.InvitationStatus;
import com.jiayuan.boot.system.team.model.enums.MemberRole;

import java.util.Locale;

/**
 * 团队邀请工具类。
 *
 * @author charleslam
 * @since 2026/05/20
 */
public final class TeamInvitationUtils {

    private TeamInvitationUtils() {
    }

    /**
     * 获取待处理邀请状态值。
     *
     * @return 待处理状态值
     */
    public static String pendingStatus() {
        return InvitationStatus.PENDING.getValue();
    }

    /**
     * 标准化邀请状态查询条件。
     *
     * @param status 邀请状态
     * @return 标准化后的邀请状态
     */
    public static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 解析并校验邀请目标角色。
     *
     * @param roleCode 目标角色编码
     * @return 目标角色
     */
    public static MemberRole resolveTargetRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            throw new BusinessException(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY, "目标角色不能为空");
        }
        MemberRole role = MemberRole.fromValue(roleCode);
        if (role == null) {
            throw new BusinessException(ResultCode.REQUEST_PARAMETER_VALUE_EXCEEDS_ALLOWED_RANGE, "目标角色不合法");
        }
        if (MemberRole.Owner == role) {
            throw new BusinessException(ResultCode.USER_OPERATION_EXCEPTION, "不能邀请为 Owner");
        }
        return role;
    }
}
