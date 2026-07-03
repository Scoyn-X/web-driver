package com.jiayuan.boot.system.oss.model.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 私密空间文件或目录构造参数。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrivateFileBuildBO {

    /**
     * 物理文件对象，目录为空。
     */
    private StoredFileObjectBO object;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 父目录ID。
     */
    private Long parentId;

    /**
     * 文件或目录原始名称。
     */
    private String originalName;
}
