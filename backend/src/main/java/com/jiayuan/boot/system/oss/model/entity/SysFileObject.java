package com.jiayuan.boot.system.oss.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jiayuan.boot.common.base.model.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件物理对象去重实体（Bonus 4.2）
 * <p>
 * 用户每次上传 / 复制文件在 {@code sys_file} 中都产生一条逻辑记录；若内容的 SHA-256
 * 已存在，则多条 sys_file 共享同一条 sys_file_object 记录（MinIO 中只有一份物理对象）。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file_object")
public class SysFileObject extends BaseEntity {

    /** 文件内容 SHA-256（hex 编码，长度 64） */
    private String fileHash;

    /** MinIO 对象 key（yyyyMMdd/UUID.ext） */
    private String objectPath;

    /** 文件字节大小 */
    private Long fileSize;

    /** 引用计数（几个 sys_file 指向此物理对象） */
    private Integer refCount;

}
