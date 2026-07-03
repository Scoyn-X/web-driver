package com.jiayuan.boot.system.privatespace.model.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jiayuan.boot.system.privatespace.model.enums.PrivateSpaceState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 私密空间状态响应。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Data
@Schema(description = "私密空间状态响应")
public class PrivateSpaceStatusResponseVO {

    @Schema(description = "私密空间状态")
    private PrivateSpaceState state;

    @Schema(description = "解锁截止时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime unlockedUntil;

    @Schema(description = "降级宽限期截止时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime graceExpireAt;

    @Schema(description = "提醒文案")
    private String reminderMessage;
}
