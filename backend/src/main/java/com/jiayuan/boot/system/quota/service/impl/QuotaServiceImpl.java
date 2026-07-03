package com.jiayuan.boot.system.quota.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.common.util.FileUtils;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.quota.converter.UserQuotaConverter;
import com.jiayuan.boot.system.quota.mapper.UserQuotaMapper;
import com.jiayuan.boot.system.quota.model.entity.UserQuota;
import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配额管理服务实现
 *
 * @author didongchen
 * @since 2026/04/13
 */
@Service
@RequiredArgsConstructor
public class QuotaServiceImpl implements QuotaService {

    /**
     * 普通用户总配额：100MB（默认值，运行时可通过 SystemConfig 覆盖）
     */
    public static final long NORMAL_TOTAL_QUOTA = 104857600L;

    /**
     * 普通用户单文件限制：100MB（默认值，运行时可通过 SystemConfig 覆盖）
     */
    public static final long NORMAL_SINGLE_FILE_LIMIT = 104857600L;

    /**
     * VIP 无限配额哨兵值
     */
    public static final long UNLIMITED_TOTAL_QUOTA = Long.MAX_VALUE;

    private final UserQuotaMapper userQuotaMapper;
    private final UserQuotaConverter userQuotaConverter;
    private final SystemConfigProperties configProperties;

    /**
     * 初始化普通用户配额。
     */
    @Override
    @Transactional
    public void initQuota(Long userId) {
        UserQuota quota = new UserQuota();
        quota.setUserId(userId);
        quota.setTotalQuota(configProperties.getNormalTotalQuota());
        quota.setUsedSpace(0L);
        userQuotaMapper.insert(quota);
    }

    /**
     * 查询当前用户配额。
     */
    @Override
    public QuotaResponseVO getQuotaInfo() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return getQuotaInfo(currentUserId);
    }

    /**
     * 查询指定用户配额。
     */
    @Override
    public QuotaResponseVO getQuotaInfo(Long userId) {
        UserQuota quota = getByUserId(userId);
        return userQuotaConverter.toQuotaVO(quota);
    }

    /**
     * 检查用户剩余配额。
     */
    @Override
    public void checkQuota(Long userId, long fileSize) {
        UserQuota quota = getByUserId(userId);
        if (isUnlimited(quota)) {
            return;
        }
        long remaining = quota.getTotalQuota() - quota.getUsedSpace();
        if (remaining < fileSize) {
            throw new BusinessException(ResultCode.USER_QUOTA_EXHAUSTED,
                    "配额不足，剩余 " + FileUtils.formatFileSize(remaining)
                            + "，需要 " + FileUtils.formatFileSize(fileSize));
        }
    }

    /**
     * 检查普通用户单文件大小限制。
     */
    @Override
    public void checkSingleFileLimit(Long userId, long fileSize) {
        UserQuota quota = getByUserId(userId);
        if (isUnlimited(quota)) {
            return;
        }
        if (fileSize > configProperties.getNormalSingleFileLimit()) {
            throw new BusinessException(ResultCode.UPLOAD_FILE_TOO_LARGE,
                    "普通用户单文件大小不能超过 "
                            + FileUtils.formatFileSize(configProperties.getNormalSingleFileLimit()));
        }
    }

    /**
     * 设置用户 VIP 状态。
     */
    @Override
    @Transactional
    public void setVipState(Long userId, boolean vip) {
        UserQuota quota = getByUserId(userId);
        quota.setTotalQuota(vip ? UNLIMITED_TOTAL_QUOTA : configProperties.getNormalTotalQuota());
        userQuotaMapper.updateById(quota);
    }

    /**
     * 判断用户是否为 VIP。
     */
    @Override
    public boolean isVip(Long userId) {
        return isUnlimited(getByUserId(userId));
    }

    /**
     * 增加用户已用空间。
     */
    @Override
    @Transactional
    public void increaseUsedSpace(Long userId, long fileSize) {
        int updated = userQuotaMapper.increaseUsedSpace(userId, fileSize);
        if (updated > 0) {
            return;
        }
        UserQuota quota = getByUserId(userId);
        long remaining = quota.getTotalQuota() - quota.getUsedSpace();
        throw new BusinessException(ResultCode.USER_QUOTA_EXHAUSTED,
                "配额不足，剩余 " + FileUtils.formatFileSize(Math.max(remaining, 0))
                        + "，需要 " + FileUtils.formatFileSize(fileSize));
    }

    /**
     * 减少用户已用空间。
     */
    @Override
    @Transactional
    public void decreaseUsedSpace(Long userId, long fileSize) {
        userQuotaMapper.decreaseUsedSpace(userId, fileSize);
    }

    /**
     * 根据用户ID获取配额记录
     */
    private UserQuota getByUserId(Long userId) {
        UserQuota quota = userQuotaMapper.selectOne(
                new LambdaQueryWrapper<UserQuota>().eq(UserQuota::getUserId, userId)
        );
        if (quota == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "用户配额信息不存在");
        }
        return quota;
    }

    /**
     * 判断配额记录是否代表 VIP 无限容量。
     */
    private boolean isUnlimited(UserQuota quota) {
        return quota.getTotalQuota() != null && quota.getTotalQuota() == UNLIMITED_TOTAL_QUOTA;
    }

}
