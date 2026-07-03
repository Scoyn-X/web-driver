package com.jiayuan.boot.system.share.model.bo;

import java.time.LocalDateTime;

/**
 * 分享实体构造上下文。
 *
 * @param userId           分享创建者用户ID
 * @param creatorAccountId 分享创建者账户ID
 * @param shareToken       分享令牌
 * @param teamId           团队ID，个人分享为空
 * @param extractCode      提取码
 * @param expireTime       过期时间
 * @author charleslam
 * @since 2026/05/21
 */
public record ShareBuildContextBO(Long userId, Long creatorAccountId, String shareToken, Long teamId,
                                  String extractCode, LocalDateTime expireTime) {
}
