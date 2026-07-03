package com.jiayuan.boot.system.oss.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件物理对象去重 Mapper（Bonus 4.2）
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Mapper
public interface SysFileObjectMapper extends BaseMapper<SysFileObject> {

    /**
     * 根据文件指纹查询物理对象。
     *
     * @param fileHash 文件指纹
     * @return 物理对象
     */
    default SysFileObject selectByHash(String fileHash) {
        return selectOne(new QueryWrapper<SysFileObject>().eq("file_hash", fileHash));
    }

    /**
     * 引用计数加一。
     *
     * @param fileHash 文件指纹
     */
    int increaseReference(String fileHash);

    /**
     * 共享引用计数减一，仅处理引用数大于 1 的物理对象。
     *
     * @param fileHash 文件指纹
     * @return 影响行数
     */
    int decreaseReferenceIfShared(String fileHash);

    /**
     * 删除最后一个引用对应的物理对象记录。
     *
     * @param fileHash 文件指纹
     * @return 影响行数
     */
    int deleteIfLastReference(String fileHash);

}
