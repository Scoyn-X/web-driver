package com.jiayuan.boot.system.oss.model.enums;

import com.jiayuan.boot.common.base.model.enums.BaseEnum;
import lombok.Getter;

/**
 * 对象存储类型枚举
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Getter
public enum OssTypeEnum implements BaseEnum<String> {

    MINIO("minio", "MinIO 对象存储"),
    ALIYUN("aliyun", "阿里云 OSS"),
    LOCAL("local", "本地存储");

    private final String value;

    private final String label;

    OssTypeEnum(String value, String label) {
        this.value = value;
        this.label = label;
    }

}
