package com.jiayuan.boot.system.oss.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文件/目录移动请求VO
 *
 * @author didongchen
 * @since 2026/04/11
 */
@Data
@Schema(description = "文件/目录移动请求")
public class FileMoveRequestVO {

    @Schema(description = "目标目录ID（0表示根目录）", example = "0")
    @NotNull(message = "目标目录ID不能为空")
    private Long targetDirectoryId;

}
