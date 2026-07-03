package com.jiayuan.boot.system.share.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分享下载响应 VO
 * <p>
 * 后端返回受控下载 URL，前端打开该 URL 后由后端中转并执行限速。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分享下载响应")
public class ShareDownloadResponseVO {

    @Schema(description = "后端受控下载 URL")
    private String downloadUrl;

    @Schema(description = "文件名（便于前端设置下载文件名）")
    private String fileName;

}
