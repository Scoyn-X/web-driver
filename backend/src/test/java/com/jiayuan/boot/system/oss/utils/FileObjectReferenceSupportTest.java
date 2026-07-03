package com.jiayuan.boot.system.oss.utils;

import com.jiayuan.boot.system.oss.mapper.SysFileObjectMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文件物理对象引用计数辅助工具单元测试。
 *
 * @author charleslam
 * @since 2026/06/05
 */
@DisplayName("FileObjectReferenceSupport 单元测试")
class FileObjectReferenceSupportTest {

    @Test
    @DisplayName("共享引用：扣减成功后不删除对象")
    void decreaseOrRemove_sharedReferenceOnlyDecrements() {
        SysFileObjectMapper mapper = mock(SysFileObjectMapper.class);
        Consumer<String> remover = remover();
        when(mapper.decreaseReferenceIfShared("hash")).thenReturn(1);

        FileObjectReferenceSupport.decreaseOrRemove("hash", object("old-path"), mapper, remover);

        verify(mapper, never()).deleteIfLastReference("hash");
        verify(remover, never()).accept("old-path");
    }

    @Test
    @DisplayName("最后一个引用：删除数据库记录后删除当前对象")
    void decreaseOrRemove_lastReferenceDeletesObject() {
        SysFileObjectMapper mapper = mock(SysFileObjectMapper.class);
        Consumer<String> remover = remover();
        when(mapper.decreaseReferenceIfShared("hash")).thenReturn(0);
        when(mapper.deleteIfLastReference("hash")).thenReturn(1);

        FileObjectReferenceSupport.decreaseOrRemove("hash", object("last-path"), mapper, remover);

        verify(remover).accept("last-path");
    }

    @Test
    @DisplayName("竞态后记录消失：停止重试且不删除对象")
    void decreaseOrRemove_recordDisappearsAfterRetryStops() {
        SysFileObjectMapper mapper = mock(SysFileObjectMapper.class);
        Consumer<String> remover = remover();
        when(mapper.decreaseReferenceIfShared("hash")).thenReturn(0);
        when(mapper.deleteIfLastReference("hash")).thenReturn(0);
        when(mapper.selectByHash("hash")).thenReturn(null);

        FileObjectReferenceSupport.decreaseOrRemove("hash", object("old-path"), mapper, remover);

        verify(remover, never()).accept("old-path");
    }

    @Test
    @DisplayName("重试后删除：使用重新读取到的对象路径")
    void decreaseOrRemove_eventuallyDeletesReloadedObject() {
        SysFileObjectMapper mapper = mock(SysFileObjectMapper.class);
        Consumer<String> remover = remover();
        when(mapper.decreaseReferenceIfShared("hash")).thenReturn(0, 0);
        when(mapper.deleteIfLastReference("hash")).thenReturn(0, 1);
        when(mapper.selectByHash("hash")).thenReturn(object("reloaded-path"));

        FileObjectReferenceSupport.decreaseOrRemove("hash", object("old-path"), mapper, remover);

        verify(remover).accept("reloaded-path");
    }

    private static SysFileObject object(String path) {
        SysFileObject object = new SysFileObject();
        object.setObjectPath(path);
        return object;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<String> remover() {
        return mock(Consumer.class);
    }
}
