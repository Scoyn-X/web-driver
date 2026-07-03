package com.jiayuan.boot.system.quota.converter;

import com.jiayuan.boot.common.util.FileUtils;
import com.jiayuan.boot.system.quota.model.entity.UserQuota;
import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * 配额对象转换器（MapStruct）
 *
 * @author didongchen
 * @since 2026/04/13
 */
@Mapper(componentModel = "spring")
public interface UserQuotaConverter {

    @Mapping(target = "remainingSpace", ignore = true)
    @Mapping(target = "totalQuotaFormatted", ignore = true)
    @Mapping(target = "usedSpaceFormatted", ignore = true)
    @Mapping(target = "remainingSpaceFormatted", ignore = true)
    QuotaResponseVO toQuotaVO(UserQuota quota);

    @AfterMapping
    default void toDerivedFields(UserQuota quota, @MappingTarget QuotaResponseVO vo) {
        if (Long.MAX_VALUE == quota.getTotalQuota()) {
            vo.setRemainingSpace(Long.MAX_VALUE);
            vo.setTotalQuotaFormatted("不限容量");
            vo.setUsedSpaceFormatted(FileUtils.formatFileSize(quota.getUsedSpace()));
            vo.setRemainingSpaceFormatted("不限容量");
            return;
        }
        long remaining = Math.max(quota.getTotalQuota() - quota.getUsedSpace(), 0);
        vo.setRemainingSpace(remaining);
        vo.setTotalQuotaFormatted(FileUtils.formatFileSize(quota.getTotalQuota()));
        vo.setUsedSpaceFormatted(FileUtils.formatFileSize(quota.getUsedSpace()));
        vo.setRemainingSpaceFormatted(FileUtils.formatFileSize(remaining));
    }

}
