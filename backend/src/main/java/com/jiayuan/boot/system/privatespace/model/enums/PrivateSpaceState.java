package com.jiayuan.boot.system.privatespace.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 私密空间状态枚举。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Getter
@RequiredArgsConstructor
public enum PrivateSpaceState implements BaseEnum<String> {

    DISABLED("DISABLED", "未开启"),
    ACTIVE("ACTIVE", "已解锁"),
    GRACE_PERIOD("GRACE_PERIOD", "降级宽限期"),
    LOCKED("LOCKED", "已锁定"),
    EXPIRED("EXPIRED", "宽限期结束");

    private final String value;
    private final String label;
}
