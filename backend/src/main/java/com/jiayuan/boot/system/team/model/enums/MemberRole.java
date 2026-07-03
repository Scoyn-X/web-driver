package com.jiayuan.boot.system.team.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;

import java.util.Arrays;

/**
 * 团队角色枚举。按 authority 从高到低排列：Owner(3) > Admin(2) > Editor(1) > Viewer(0)
 *
 * @author didongchen
 * @since 2026/05/14
 */
@Getter
public enum MemberRole implements BaseEnum<String> {

    Owner("Owner", "拥有者", 3),
    Admin("Admin", "管理员", 2),
    Editor("Editor", "编辑者", 1),
    Viewer("Viewer", "只读者", 0);

    private final String value;

    private final String label;

    /** 权限高低数值，越大权限越高 */
    private final int authority;

    MemberRole(String value, String label, int authority) {
        this.value = value;
        this.label = label;
        this.authority = authority;
    }

    /**
     * 根据角色值查找枚举
     */
    public static MemberRole fromValue(String value) {
        return Arrays.stream(values())
                .filter(r -> r.value.equals(value))
                .findFirst()
                .orElse(null);
    }

    /**
     * 当前角色是否不低于指定角色
     */
    public boolean isAtLeast(MemberRole other) {
        return this.authority >= other.authority;
    }
}
