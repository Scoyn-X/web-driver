package com.jiayuan.boot.system.privatespace.converter;

import com.jiayuan.boot.system.privatespace.model.entity.PrivateSpace;
import com.jiayuan.boot.system.privatespace.model.enums.PrivateSpaceState;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSessionResponseVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSpaceStatusResponseVO;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;

/**
 * 私密空间对象转换器。
 *
 * @author charleslam
 * @since 2026/05/18
 */
@Mapper(componentModel = "spring")
public interface PrivateSpaceConverter {

    /**
     * 构造私密空间配置实体。
     *
     * @param userId       用户ID
     * @param passwordHash 私密空间密码哈希
     * @return 私密空间配置实体
     */
    PrivateSpace toPrivateSpace(Long userId, String passwordHash);

    /**
     * 转换私密空间状态响应。
     *
     * @param state           私密空间状态
     * @param unlockedUntil   解锁截止时间
     * @param graceExpireAt   宽限期截止时间
     * @param reminderMessage 提醒文案
     * @return 私密空间状态响应
     */
    PrivateSpaceStatusResponseVO toStatusResponseVO(PrivateSpaceState state,
                                                    LocalDateTime unlockedUntil,
                                                    LocalDateTime graceExpireAt,
                                                    String reminderMessage);

    /**
     * 转换私密空间解锁响应。
     *
     * @param unlockedUntil 解锁截止时间
     * @return 私密空间解锁响应
     */
    PrivateSessionResponseVO toSessionResponseVO(LocalDateTime unlockedUntil);
}
