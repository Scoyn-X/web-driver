package com.jiayuan.boot.system.privatespace.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私密空间解锁响应。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
@Schema(description = "私密空间解锁响应")
public class PrivateSessionResponseVO {

    @Schema(description = "解锁截止时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime unlockedUntil;
}
