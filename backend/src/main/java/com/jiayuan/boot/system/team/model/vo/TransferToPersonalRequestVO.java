package com.jiayuan.boot.system.team.model.vo;

import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 团队文件转存个人空间请求VO。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Data
@Schema(description = "团队文件转存个人空间请求")
public class TransferToPersonalRequestVO {

    @Schema(description = "目标个人目录ID（0表示根目录）", example = "0")
    private Long targetDirectoryId;

    @Schema(description = "兼容字段；B14d2 首版始终自动重命名，不执行覆盖")
    private ConflictPolicy conflictPolicy;
}
