package com.jiayuan.boot.system.team.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队回收站列表项响应对象。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Data
public class TeamTrashItemResponseVO {

    @Schema(description = "文件或目录ID")
    private Long id;

    @Schema(description = "文件原始名称")
    private String originalName;

    @Schema(description = "完整人类可读路径")
    private String path;

    @Schema(description = "删除者用户ID", nullable = true)
    private Long deletedByUserId;

    @Schema(description = "删除者主账户ID", nullable = true)
    private Long deletedByAccountId;

    @Schema(description = "删除者主账户名", nullable = true)
    private String deletedByAccountName;

    @Schema(description = "删除者名称", nullable = true)
    private String deletedByName;

    @Schema(description = "放入回收站时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deletedAt;

    @Schema(description = "回收站到期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireAt;

    @Schema(description = "文件大小")
    private Long fileSize;

    @Schema(description = "是否为目录")
    private Integer isDirectory;

    @Schema(description = "回收站状态")
    private String status;
}
