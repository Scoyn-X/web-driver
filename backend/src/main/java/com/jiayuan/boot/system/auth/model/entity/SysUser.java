package com.jiayuan.boot.system.auth.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体（代表用户主信息，一个用户可拥有多个账户）
 *
 * @author didongchen
 * @since 2026/04/10
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 用户状态（0=禁用, 1=启用）
     */
    private Integer status;

}
