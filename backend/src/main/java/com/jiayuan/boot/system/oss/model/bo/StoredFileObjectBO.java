package com.jiayuan.boot.system.oss.model.bo;

import lombok.Data;

/**
 * 已保存的物理文件对象信息。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
public class StoredFileObjectBO {

    /**
     * 存储文件名。
     */
    private String storedName;

    /**
     * MinIO 对象路径。
     */
    private String objectPath;

    /**
     * 文件访问 URL。
     */
    private String fileUrl;

    /**
     * 文件大小。
     */
    private Long fileSize;

    /**
     * 文件 MIME 类型。
     */
    private String mimeType;

    /**
     * 文件指纹。
     */
    private String fileHash;
}
