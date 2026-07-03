package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.common.util.FileUtils;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.quota.service.impl.QuotaServiceImpl;
import com.jiayuan.boot.system.team.mapper.TeamSpaceMapper;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import com.jiayuan.boot.system.team.service.TeamQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 团队配额服务实现
 * <p>
 * 该实现仅覆盖 B 功能所需的团队上传、复制、转存、恢复和彻底删除容量联动。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Service
@RequiredArgsConstructor
public class TeamQuotaServiceImpl implements TeamQuotaService {

    /**
     * 普通团队总配额：1GB。
     */
    public static final long NORMAL_TEAM_TOTAL_QUOTA = 1073741824L;

    private final TeamSpaceMapper teamSpaceMapper;
    private final QuotaService quotaService;

    /**
     * 根据 VIP 状态解析团队总容量规格。
     *
     * @param vip Owner 是否为 VIP
     * @return 团队总容量
     */
    @Override
    public Long resolveTeamTotalQuota(boolean vip) {
        return vip ? QuotaServiceImpl.UNLIMITED_TOTAL_QUOTA : NORMAL_TEAM_TOTAL_QUOTA;
    }

    /**
     * 根据 Owner 当前 VIP 状态解析团队总容量规格。
     *
     * @param ownerId Owner 用户 ID
     * @return 团队总容量
     */
    @Override
    public Long resolveOwnerTeamTotalQuota(Long ownerId) {
        return resolveTeamTotalQuota(quotaService.isVip(ownerId));
    }

    /**
     * 同步指定 Owner 拥有的所有团队容量规格。
     *
     * @param ownerId Owner 用户 ID
     * @param vip     Owner 是否为 VIP
     */
    @Override
    @Transactional
    public void syncOwnerTeamTotalQuota(Long ownerId, boolean vip) {
        teamSpaceMapper.updateTotalQuotaByOwner(ownerId, resolveTeamTotalQuota(vip));
    }

    /**
     * 按当前 Owner 身份同步单个团队容量规格。
     *
     * @param teamId  团队 ID
     * @param ownerId Owner 用户 ID
     */
    @Override
    @Transactional
    public void syncTeamTotalQuotaByOwner(Long teamId, Long ownerId) {
        int updated = teamSpaceMapper.updateTotalQuota(teamId, resolveOwnerTeamTotalQuota(ownerId));
        if (updated == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队不存在");
        }
    }

    /**
     * 检查团队剩余容量是否足够。
     *
     * @param teamId 团队 ID
     * @param size   需要占用的容量，单位字节
     */
    @Override
    public void checkQuota(Long teamId, long size) {
        TeamSpace team = requireTeam(teamId);
        if (isUnlimited(team)) {
            return;
        }
        long remaining = team.getTotalQuota() - team.getUsedSpace();
        if (remaining < size) {
            throw new BusinessException(ResultCode.USER_QUOTA_EXHAUSTED,
                    "团队配额不足，剩余 " + FileUtils.formatFileSize(Math.max(remaining, 0))
                            + "，需要 " + FileUtils.formatFileSize(size));
        }
    }

    /**
     * 增加团队已用容量。
     *
     * @param teamId 团队 ID
     * @param size   增加容量，单位字节
     */
    @Override
    @Transactional
    public void increaseUsedSpace(Long teamId, long size) {
        requireTeam(teamId);
        if (teamSpaceMapper.increaseUsedSpace(teamId, size) == 0) {
            throw new BusinessException(ResultCode.USER_QUOTA_EXHAUSTED, "团队配额不足");
        }
    }

    /**
     * 减少团队已用容量。
     *
     * @param teamId 团队 ID
     * @param size   释放容量，单位字节
     */
    @Override
    @Transactional
    public void decreaseUsedSpace(Long teamId, long size) {
        requireTeam(teamId);
        if (teamSpaceMapper.decreaseUsedSpace(teamId, size) == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队不存在");
        }
    }

    /**
     * 查询团队配额记录，不存在时抛业务异常。
     */
    private TeamSpace requireTeam(Long teamId) {
        TeamSpace team = teamSpaceMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队不存在");
        }
        if (team.getTotalQuota() == null || team.getUsedSpace() == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队配额信息不存在");
        }
        return team;
    }

    /**
     * 判断团队是否使用 VIP 无限容量规格。
     */
    private boolean isUnlimited(TeamSpace team) {
        return team.getTotalQuota() != null && team.getTotalQuota() == QuotaServiceImpl.UNLIMITED_TOTAL_QUOTA;
    }
}
