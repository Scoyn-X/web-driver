package com.jiayuan.boot.system.counter.service.impl;

import com.jiayuan.boot.system.counter.service.VisitService;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 访问计数服务实现类
 * <p>
 * 使用 AtomicInteger 存储计数，保证线程安全
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Service
public class VisitServiceImpl implements VisitService {

    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 递增并获取计数
     */
    @Override
    public int incrementAndGet() {
        return counter.incrementAndGet();
    }

    /**
     * 获取当前计数
     */
    @Override
    public int getCount() {
        return counter.get();
    }

    /**
     * 重置计数
     */
    @Override
    public void reset() {
        counter.set(0);
    }

}
