package com.jiayuan.boot.system.security.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import com.jiayuan.boot.system.security.model.enums.AccountTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账户实体（一个用户可拥有多个账户）
 *
 * @author didongchen
 * @since 2026/04/10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_account")
public class SysAccount extends BaseEntity {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 账户名称（用于登录）
     */
    private String accountName;

    /**
     * 密码（BCrypt加密）
     */
    private String password;

    /**
     * 账户类型
     */
    private String accountType;

    /**
     * 账户状态（0=禁用, 1=启用）
     */
    private Integer status;

    /**
     * 账户描述
     */
    private String description;

    /**
     * 获取账户类型枚举
     */
    public AccountTypeEnum getAccountTypeEnum() {
        for (AccountTypeEnum type : AccountTypeEnum.values()) {
            if (type.getValue().equals(this.accountType)) {
                return type;
            }
        }
        return null;
    }

}
