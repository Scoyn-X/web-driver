package com.jiayuan.boot.system.team.model.bo;

import com.jiayuan.boot.system.team.model.entity.TeamMember;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import lombok.Data;

import java.util.List;

/**
 * 团队权限响应构造参数。
 *
 * @author charleslam
 * @since 2026/05/22
 */
@Data
public class TeamPermissionBuildBO {

    /**
     * 团队空间。
     */
    private TeamSpace team;

    /**
     * 当前团队成员。
     */
    private TeamMember member;

    /**
     * 当前成员权限点。
     */
    private List<String> permissions;

    /**
     * 团队配额状态。
     */
    private String quotaState;

    /**
     * 当前用户 VIP 状态。
     */
    private String vipState;

    /**
     * 当前用户单文件限制。
     */
    private Long singleFileLimit;

    /**
     * 下载是否限速。
     */
    private Boolean downloadLimited;

    /**
     * 创建团队权限响应构造参数。
     *
     * @param team            团队空间
     * @param member          当前团队成员
     * @param permissions     当前成员权限点
     * @param quotaState      团队配额状态
     * @param vipState        当前用户 VIP 状态
     * @param singleFileLimit 当前用户单文件限制
     * @param downloadLimited 下载是否限速
     * @return 团队权限响应构造参数
     */
    public static TeamPermissionBuildBO of(TeamSpace team, TeamMember member, List<String> permissions,
                                           String quotaState, String vipState,
                                           Long singleFileLimit, Boolean downloadLimited) {
        TeamPermissionBuildBO build = new TeamPermissionBuildBO();
        build.setTeam(team);
        build.setMember(member);
        build.setPermissions(permissions);
        build.setQuotaState(quotaState);
        build.setVipState(vipState);
        build.setSingleFileLimit(singleFileLimit);
        build.setDownloadLimited(downloadLimited);
        return build;
    }
}
