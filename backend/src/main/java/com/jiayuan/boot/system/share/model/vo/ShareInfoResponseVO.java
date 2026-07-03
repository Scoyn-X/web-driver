package com.jiayuan.boot.system.share.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享信息 VO（列表返回、创建返回）
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Data
@Schema(description = "分享信息")
public class ShareInfoResponseVO {

    @Schema(description = "分享记录ID")
    private Long id;

    @Schema(description = "被分享的文件/目录ID")
    private Long fileId;

    @Schema(description = "文件名；若文件已删除，显示 [已删除]")
    private String fileName;

    @Schema(description = "是否为目录；文件记录缺失时为 null")
    private Boolean isDirectory;

    @Schema(description = "分享 token，前端拼接为 /s/{token}")
    private String shareToken;

    @Schema(description = "访问方式(0-全公开 1-分享码访问)")
    private Integer accessType;

    @Schema(description = "提取码（仅 accessType=1 时返回给创建者）")
    private String extractCode;

    @Schema(description = "过期时间，null 表示永久有效")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @Schema(description = "当前状态文案：有效 / 已过期 / 已取消")
    private String statusDesc;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

}
