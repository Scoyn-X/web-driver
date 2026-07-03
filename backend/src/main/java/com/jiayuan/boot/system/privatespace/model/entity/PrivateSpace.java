package com.jiayuan.boot.system.privatespace.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 私密空间配置实体。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("private_space")
public class PrivateSpace extends BaseEntity {

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 私密空间密码哈希。
     */
    private String passwordHash;

    /**
     * 宽限期截止时间。
     */
    private LocalDateTime graceExpireAt;
}
