package com.jiayuan.boot.system.team.model.vo;

import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 个人文件转存团队请求VO。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Data
@Schema(description = "个人文件转存团队请求")
public class TransferFromPersonalRequestVO {

    @Schema(description = "来源个人文件或目录ID", example = "1")
    @NotNull(message = "来源文件ID不能为空")
    private Long sourceFileId;

    @Schema(description = "目标团队目录ID（0表示根目录）", example = "0")
    private Long targetDirectoryId;

    @Schema(description = "兼容字段；B14d1 首版始终自动重命名，不执行覆盖")
    private ConflictPolicy conflictPolicy;
}
