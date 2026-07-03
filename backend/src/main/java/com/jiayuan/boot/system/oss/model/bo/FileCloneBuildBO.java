package com.jiayuan.boot.system.oss.model.bo;

import com.jiayuan.boot.system.oss.model.entity.SysFile;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 逻辑文件复制实体构造参数。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileCloneBuildBO {

    /**
     * 来源文件或目录。
     */
    private SysFile source;

    /**
     * 文件所有者ID。
     */
    private Long userId;

    /**
     * 空间类型。
     */
    private String spaceType;

    /**
     * 空间ID。
     */
    private Long spaceId;

    /**
     * 上传者用户ID。
     */
    private Long uploaderId;

    /**
     * 新父目录ID。
     */
    private Long parentId;

    /**
     * 新文件或目录名称。
     */
    private String originalName;
}
