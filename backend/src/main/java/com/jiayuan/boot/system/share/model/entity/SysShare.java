package com.jiayuan.boot.system.share.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件分享记录实体
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_share")
public class SysShare extends BaseEntity {

    /**
     * 分享创建者用户ID
     */
    private Long userId;

    /**
     * 分享创建者账户ID
     */
    private Long creatorAccountId;

    /**
     * 分享空间类型（PERSONAL/TEAM）
     */
    private String spaceType;

    /**
     * 团队ID，个人分享为 null
     */
    private Long teamId;

    /**
     * 被分享的文件/目录ID（指向 sys_file.id）
     */
    private Long fileId;

    /**
     * 唯一分享标识（随机生成）
     */
    private String shareToken;

    /**
     * 访问方式(0-全公开 1-分享码访问)
     */
    private Integer accessType;

    /**
     * 提取码，4-6位字母数字（仅 accessType=1 时有值）
     */
    private String extractCode;

    /**
     * 过期时间，null 表示永久有效
     */
    private LocalDateTime expireTime;

    /**
     * 分享状态(0-有效 1-已取消)
     */
    private Integer status;

}
