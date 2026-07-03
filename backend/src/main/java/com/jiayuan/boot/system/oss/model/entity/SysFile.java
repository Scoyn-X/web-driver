package com.jiayuan.boot.system.oss.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件元数据实体
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class SysFile extends BaseEntity {

    /**
     * 迁移前回收站记录的默认兼容保留天数。
     */
    private static final long DEFAULT_TRASH_RETENTION_DAYS = 3L;

    /**
     * 文件原始名称
     */
    private String originalName;

    /**
     * 存储文件名(UUID)
     */
    private String storedName;

    /**
     * 文件在MinIO中的对象路径
     */
    private String filePath;

    /**
     * 文件访问URL
     */
    private String fileUrl;

    /**
     * 文件大小(字节)
     */
    private Long fileSize;

    /**
     * 文件MIME类型
     */
    private String mimeType;

    /**
     * 文件所有者ID
     */
    private Long userId;

    /**
     * 空间类型（PERSONAL/TEAM/PRIVATE）
     */
    private String spaceType;

    /**
     * 空间ID（个人为用户ID，团队为团队ID）
     */
    private Long spaceId;

    /**
     * 上传者用户ID
     */
    private Long uploaderId;

    /**
     * 父目录ID（0表示根目录）
     */
    private Long parentId;

    /**
     * 是否为目录（0=文件 1=目录）
     */
    private Integer isDirectory;

    /**
     * 祖先ID路径（逗号分隔，如 "0,1,2,3"，从根到当前节点的父级ID链路）
     */
    private String fullPath;

    /**
     * 文件内容 SHA-256（hex 64字符），指向 sys_file_object.file_hash；目录为 null（Bonus 4.2）
     */
    private String fileHash;

    /**
     * 是否在回收站（0=正常 1=在回收站）（Bonus 4.3）
     */
    private Integer inRecycleBin;

    /**
     * 是否为用户主动删除的回收站根节点（0=否 1=是）。
     * 删除目录时仅该目录自身置 1，子节点保持 0；恢复/永久删除以根节点为单位进行，
     * 避免单独恢复子节点导致父目录仍在回收站的孤儿问题（Bonus 4.3）
     */
    private Integer recycleRoot;

    /**
     * 放入回收站时间。
     */
    private LocalDateTime deletedAt;

    /**
     * 删除操作人用户ID。
     */
    private Long deletedBy;

    /**
     * 回收站到期时间。
     */
    private LocalDateTime expireAt;

    public LocalDateTime getDeletedAt() {
        if (deletedAt != null) {
            return deletedAt;
        }
        if (Integer.valueOf(1).equals(inRecycleBin)) {
            return getUpdateTime();
        }
        return null;
    }

    public LocalDateTime getExpireAt() {
        if (expireAt != null) {
            return expireAt;
        }
        if (deletedAt != null) {
            return deletedAt.plusDays(DEFAULT_TRASH_RETENTION_DAYS);
        }
        if (Integer.valueOf(1).equals(inRecycleBin)) {
            return LocalDateTime.now().plusDays(DEFAULT_TRASH_RETENTION_DAYS);
        }
        return null;
    }

}
