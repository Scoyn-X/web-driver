package com.jiayuan.boot.system.oss.utils;

import com.jiayuan.boot.system.oss.mapper.SysFileObjectMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;

import java.util.function.Consumer;

/**
 * 文件物理对象引用计数辅助工具。
 *
 * @author charleslam
 * @since 2026/05/16
 */
public final class FileObjectReferenceSupport {

    private FileObjectReferenceSupport() {
    }

    /**
     * 条件扣减引用计数，最后一个引用先删除数据库记录再删除 MinIO 对象。
     *
     * @param fileHash      文件指纹
     * @param object        当前物理对象记录
     * @param mapper        物理对象 Mapper
     * @param objectRemover MinIO 对象删除函数
     */
    public static void decreaseOrRemove(String fileHash, SysFileObject object,
                                        SysFileObjectMapper mapper, Consumer<String> objectRemover) {
        SysFileObject current = object;
        for (int i = 0; i < 3; i++) {
            if (mapper.decreaseReferenceIfShared(fileHash) > 0) {
                return;
            }
            if (mapper.deleteIfLastReference(fileHash) > 0) {
                objectRemover.accept(current.getObjectPath());
                return;
            }
            current = mapper.selectByHash(fileHash);
            if (current == null) {
                return;
            }
        }
    }
}
