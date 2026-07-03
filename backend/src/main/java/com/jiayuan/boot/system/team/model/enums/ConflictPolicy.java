package com.jiayuan.boot.system.team.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;

/**
 * 团队回收站恢复同名冲突处理策略。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Getter
public enum ConflictPolicy implements BaseEnum<String> {

    RENAME("RENAME", "自动重命名"),
    OVERWRITE("OVERWRITE", "覆盖现有文件");

    private final String value;

    private final String label;

    ConflictPolicy(String value, String label) {
        this.value = value;
        this.label = label;
    }
}
