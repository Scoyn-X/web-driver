package com.jiayuan.boot.system.quota.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户配额实体
 *
 * @author didongchen
 * @since 2026/04/13
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_quota")
public class UserQuota extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 总配额（字节），默认1GB
     */
    private Long totalQuota;

    /**
     * 已使用空间（字节）
     */
    private Long usedSpace;

}
