package com.jiayuan.boot.system.oss.model.entity;

import lombok.Data;

/**
 * 文件信息对象
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Data
public class FileInfo {

    /**
     * 文件ID。
     */
    private Long id;

    /**
     * 文件名称。
     */
    private String name;

    /**
     * 文件URL。
     */
    private String url;

}
