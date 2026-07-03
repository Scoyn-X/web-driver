package com.jiayuan.boot.common.annotation;

import java.lang.annotation.*;

/**
 * 防止重复提交注解
 * <p>
 * 该注解可用于接口方法上，防止在指定时间内重复提交请求
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface RepeatSubmit {

    /**
     * 锁过期时间(秒)
     */
    int expire() default 5;

}
