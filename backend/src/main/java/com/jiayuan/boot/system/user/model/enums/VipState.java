package com.jiayuan.boot.system.user.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 用户 VIP 状态枚举
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Getter
@RequiredArgsConstructor
public enum VipState implements BaseEnum<String> {

    NORMAL("NORMAL", "普通用户"),
    VIP("VIP", "VIP 用户");

    private final String value;
    private final String label;
}
