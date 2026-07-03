package com.jiayuan.boot.system.counter.service;

/**
 * 访问计数服务接口
 *
 * @author jiayuan
 * @since 2026/03/09
 */
public interface VisitService {

    /**
     * 增加访问次数并返回新值
     *
     * @return 新的访问次数
     */
    int incrementAndGet();

    /**
     * 获取当前访问次数
     *
     * @return 当前访问次数
     */
    int getCount();

    /**
     * 重置访问次数为0
     */
    void reset();

}
