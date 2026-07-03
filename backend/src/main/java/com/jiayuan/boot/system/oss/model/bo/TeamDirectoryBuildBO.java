package com.jiayuan.boot.system.oss.model.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 团队目录实体构造参数。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamDirectoryBuildBO {

    /**
     * 团队ID。
     */
    private Long teamId;

    /**
     * 上传者用户ID。
     */
    private Long uploaderId;

    /**
     * 父目录ID。
     */
    private Long parentId;

    /**
     * 目录名称。
     */
    private String originalName;
}
