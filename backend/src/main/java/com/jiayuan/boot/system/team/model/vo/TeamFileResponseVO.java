package com.jiayuan.boot.system.team.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 团队文件响应对象。
 *
 * @author charleslam
 * @since 2026/05/18
 */
@Data
public class TeamFileResponseVO {

    @Schema(description = "文件或目录ID")
    private Long id;

    @Schema(description = "团队ID")
    private Long teamId;

    @Schema(description = "上传者ID")
    private Long uploaderId;

    @Schema(description = "上传者名称")
    private String uploaderName;

    @Schema(description = "文件原始名称")
    private String originalName;

    @Schema(description = "文件大小")
    private Long fileSize;

    @Schema(description = "MIME 类型")
    private String mimeType;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "文件访问 URL")
    private String fileUrl;

    @Schema(description = "是否为目录")
    private Integer isDirectory;

    @Schema(description = "父目录ID")
    private Long parentId;

    @Schema(description = "祖先ID路径")
    private List<Long> fullPath;
}
