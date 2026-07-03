package com.jiayuan.boot.system.share.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 匿名访问分享页返回 VO
 * <p>
 * 仅暴露必要的展示信息，不返回分享创建者、原始 fileId 等敏感/内部字段。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Data
@Schema(description = "分享访问页响应")
public class ShareAccessResponseVO {

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "文件大小易读展示，如 3.45 MB")
    private String fileSizeFormatted;

    @Schema(description = "文件 MIME 类型")
    private String mimeType;

    @Schema(description = "是否为目录")
    private Boolean isDirectory;

    @Schema(description = "是否需要输入提取码")
    private Boolean requireExtractCode;

    @Schema(description = "文件上传时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime fileUploadTime;

}
