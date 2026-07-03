package com.jiayuan.boot.system.user.converter;

import com.jiayuan.boot.system.quota.model.vo.QuotaResponseVO;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import com.jiayuan.boot.system.user.model.bo.UserVipProfileBO;
import com.jiayuan.boot.system.user.model.bo.UserVipProfileBuildBO;
import com.jiayuan.boot.system.user.model.enums.VipState;
import com.jiayuan.boot.system.user.model.vo.CurrentUserResponseVO;
import com.jiayuan.boot.system.user.model.vo.UserBriefResponseVO;
import com.jiayuan.boot.system.user.model.vo.UserVipResponseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 用户对象转换器
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    /**
     * 转换当前用户响应对象。
     *
     * @param brief                用户基础信息
     * @param vipState             VIP 状态
     * @param personalQuota        个人配额
     * @param privateSpaceReminder 私密空间提醒
     * @return 当前用户响应对象
     */
    @Mapping(target = "userId", source = "brief.userId")
    @Mapping(target = "accountId", source = "brief.accountId")
    @Mapping(target = "accountName", source = "brief.accountName")
    @Mapping(target = "nickname", source = "brief.nickname")
    @Mapping(target = "email", source = "brief.email")
    CurrentUserResponseVO toCurrentUserResponseVO(UserBriefBO brief,
                                                  VipState vipState,
                                                  QuotaResponseVO personalQuota,
                                                  String privateSpaceReminder);

    /**
     * 转换用户基础信息响应对象。
     *
     * @param brief 用户基础信息
     * @return 用户基础信息响应对象
     */
    UserBriefResponseVO toUserBriefResponseVO(UserBriefBO brief);

    /**
     * 转换用户 VIP 响应对象。
     *
     * @param profile 用户 VIP 限制信息
     * @return 用户 VIP 响应对象
     */
    UserVipResponseVO toUserVipResponseVO(UserVipProfileBO profile);

    /**
     * 转换用户 VIP 限制信息。
     *
     * @param build 用户 VIP 限制信息构造参数
     * @return 用户 VIP 限制信息
     */
    @Mapping(target = "downloadLimited", expression = "java(build.getVipState() == VipState.NORMAL)")
    UserVipProfileBO toUserVipProfileBO(UserVipProfileBuildBO build);
}
