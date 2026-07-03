package com.jiayuan.boot.system.oss.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件树节点响应对象（用于文件列表树形展示，包含文件和文件夹）
 *
 * @author didongchen
 * @since 2026/04/16
 */
@Data
@Schema(description = "文件树节点响应对象")
public class FileTreeResponseVO {

    @Schema(description = "文件/目录ID", example = "1")
    private Long id;

    @Schema(description = "文件原始名称", example = "报告.pdf")
    private String originalName;

    @Schema(description = "文件大小（字节）", example = "1024")
    private Long fileSize;

    @Schema(description = "文件MIME类型", example = "application/pdf")
    private String mimeType;

    @Schema(description = "上传时间", example = "2026-05-15 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "文件访问URL", example = "https://minio.example.com/bucket/file.pdf")
    private String fileUrl;

    @Schema(description = "是否为目录（0=文件 1=目录）", example = "0")
    private Integer isDirectory;

    @Schema(description = "父目录ID（0表示根目录）", example = "0")
    private Long parentId;

    @Schema(description = "祖先ID路径（从根到当前节点的父级ID链路）")
    @ArraySchema(schema = @Schema(type = "integer", format = "int64"))
    private List<Long> fullPath;

    @Schema(description = "子节点列表")
    private List<FileTreeResponseVO> children;

}
