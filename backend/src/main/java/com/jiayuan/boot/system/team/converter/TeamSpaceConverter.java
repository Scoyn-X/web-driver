package com.jiayuan.boot.system.team.converter;

import com.jiayuan.boot.common.util.FileUtils;
import com.jiayuan.boot.system.team.model.entity.TeamSpace;
import com.jiayuan.boot.system.team.model.enums.TeamStatus;
import com.jiayuan.boot.system.team.model.vo.TeamCreateRequestVO;
import com.jiayuan.boot.system.team.model.vo.TeamQuotaResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamUpdateRequestVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * 团队空间对象转换器（MapStruct）
 *
 * @author didongchen
 * @since 2026/05/17
 */
@Mapper(componentModel = "spring")
public interface TeamSpaceConverter {

    // ==================== Entity -> VO ====================

    @Mapping(target = "ownerName", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "quota", ignore = true)
    TeamResponseVO toTeamVO(TeamSpace team);

    /**
     * 将 TeamSpace 转为 TeamResponseVO，同时填入 ownerName 和 role，避免调用方手写 set。
     */
    @Mapping(target = "ownerName", source = "ownerName")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "quota", ignore = true)
    TeamResponseVO toTeamVO(TeamSpace team, String ownerName, String role);

    @Mapping(target = "remainingSpace", ignore = true)
    @Mapping(target = "totalQuotaFormatted", ignore = true)
    @Mapping(target = "usedSpaceFormatted", ignore = true)
    @Mapping(target = "remainingSpaceFormatted", ignore = true)
    TeamQuotaResponseVO toQuotaVO(TeamSpace team);

    @AfterMapping
    default void toQuotaDerivedFields(TeamSpace team, @MappingTarget TeamQuotaResponseVO vo) {
        long totalQuota = team.getTotalQuota();
        long remaining = Math.max(totalQuota - team.getUsedSpace(), 0);
        vo.setRemainingSpace(remaining);
        vo.setTotalQuotaFormatted(totalQuota == Long.MAX_VALUE ? "不限制" : FileUtils.formatFileSize(totalQuota));
        vo.setUsedSpaceFormatted(FileUtils.formatFileSize(team.getUsedSpace()));
        vo.setRemainingSpaceFormatted(FileUtils.formatFileSize(remaining));
    }

    /**
     * 补充 TeamResponseVO 的 quota（嵌套配额对象）。
     */
    @AfterMapping
    default void toEnrichQuota(TeamSpace team, @MappingTarget TeamResponseVO vo) {
        vo.setQuota(toQuotaVO(team));
    }

    // ==================== VO -> Entity ====================

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "ownerAccountId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "totalQuota", ignore = true)
    @Mapping(target = "usedSpace", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    TeamSpace toEntity(TeamCreateRequestVO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "ownerAccountId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "totalQuota", ignore = true)
    @Mapping(target = "usedSpace", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    void toUpdatedEntity(TeamUpdateRequestVO request, @MappingTarget TeamSpace team);

    /**
     * 创建团队时初始化服务端确定的字段。
     */
    default void toInitTeam(TeamSpace team, Long ownerId, Long ownerAccountId, Long totalQuota) {
        team.setOwnerId(ownerId);
        team.setOwnerAccountId(ownerAccountId);
        team.setStatus(TeamStatus.ACTIVE.getValue());
        team.setTotalQuota(totalQuota);
        team.setUsedSpace(0L);
    }
}
