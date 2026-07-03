package com.jiayuan.boot.system.oss.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站列表项视图对象（Bonus 4.3）。
 * <p>
 * 相对于 {@link FileInfoResponseVO} 做了瘦身：去掉前端无意义字段
 * （fileUrl/mimeType/parentId/fullPath ID 链），新增人类可读的 {@code path}
 * 与「放入回收站时间」{@code deletedAt}。
 *
 * @author charleslam
 * @since 2026/04/16
 */
@Data
@Schema(description = "回收站列表项视图对象")
public class RecycleBinItemResponseVO {

    @Schema(description = "文件/目录ID（用于恢复或永久删除）", example = "1")
    private Long id;

    @Schema(description = "原始名称", example = "报告.pdf")
    private String originalName;

    @Schema(description = "完整人类可读路径，含自身名称", example = "/A/B/report.pdf")
    private String path;

    @Schema(description = "文件大小（字节），目录为0", example = "1024")
    private Long fileSize;

    @Schema(description = "是否为目录（0=文件 1=目录）", example = "0")
    private Integer isDirectory;

    @Schema(description = "删除者用户ID")
    private Long deletedByUserId;

    @Schema(description = "删除者主账户ID")
    private Long deletedByAccountId;

    @Schema(description = "删除者主账户名")
    private String deletedByAccountName;

    @Schema(description = "删除者名称")
    private String deletedByName;

    @Schema(description = "放入回收站的时间", example = "2026-05-15 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deletedAt;

    @Schema(description = "回收站到期时间", example = "2026-05-18 10:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireAt;

}
