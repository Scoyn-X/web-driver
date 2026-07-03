package com.jiayuan.boot.system.auth.converter;

import com.jiayuan.boot.system.auth.model.entity.SysUser;
import com.jiayuan.boot.system.auth.model.vo.RegisterRequestVO;
import com.jiayuan.boot.system.security.model.entity.SysAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 认证对象转换器（MapStruct）
 *
 * @author didongchen
 * @since 2026/05/07
 */
@Mapper(componentModel = "spring")
public interface AuthConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "status", constant = "1")
    SysUser toSysUser(RegisterRequestVO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "accountName", source = "request.accountName")
    @Mapping(target = "password", source = "encodedPassword")
    @Mapping(target = "accountType", source = "request.accountType")
    @Mapping(target = "status", constant = "1")
    @Mapping(target = "description", constant = "初始账户")
    SysAccount toSysAccount(RegisterRequestVO request, Long userId, String encodedPassword);
}
