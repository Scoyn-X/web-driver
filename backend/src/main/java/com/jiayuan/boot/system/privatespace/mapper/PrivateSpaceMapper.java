package com.jiayuan.boot.system.privatespace.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiayuan.boot.system.privatespace.model.entity.PrivateSpace;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 私密空间配置 Mapper。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Mapper
public interface PrivateSpaceMapper extends BaseMapper<PrivateSpace> {

    /**
     * 根据用户ID查询私密空间配置。
     *
     * @param userId 用户ID
     * @return 私密空间配置
     */
    default PrivateSpace selectByUserId(Long userId) {
        return selectOne(new QueryWrapper<PrivateSpace>().eq("user_id", userId));
    }

    /**
     * 更新用户私密空间宽限期截止时间。
     *
     * @param userId        用户ID
     * @param graceExpireAt 宽限期截止时间
     * @return 更新行数
     */
    default int updateGraceExpireAt(Long userId, LocalDateTime graceExpireAt) {
        return update(null, new UpdateWrapper<PrivateSpace>()
                .eq("user_id", userId)
                .eq("is_deleted", 0)
                .set("grace_expire_at", graceExpireAt));
    }
}
