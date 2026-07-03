package com.jiayuan.boot.system.oss.service;

import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件物理对象服务接口。
 *
 * @author charleslam
 * @since 2026/05/16
 */
public interface FileObjectService {

    /**
     * 保存上传文件或复用已有物理对象。
     *
     * @param file 上传文件
     * @return 物理对象信息
     */
    StoredFileObjectBO saveOrReuse(MultipartFile file);

    /**
     * 增加物理对象引用计数。
     *
     * @param fileHash 文件指纹
     */
    void increaseReference(String fileHash);

    /**
     * 减少物理对象引用计数，最后一个引用会删除 MinIO 对象。
     *
     * @param file 文件元数据
     */
    void decreaseReferenceOrRemove(SysFile file);

    /**
     * 按物理对象信息减少引用计数，用于元数据尚未落库时的上传回滚。
     *
     * @param object 已保存的物理对象信息
     */
    void decreaseReferenceOrRemove(StoredFileObjectBO object);

    /**
     * 将文件流写入响应。
     *
     * @param file     文件元数据
     * @param response HTTP 响应
     */
    void writeToResponse(SysFile file, HttpServletResponse response);

    /**
     * 将匿名分享文件流按普通用户限速策略写入响应。
     *
     * @param file     文件元数据
     * @param response HTTP 响应
     */
    void writeSharedToResponse(SysFile file, HttpServletResponse response);

    /**
     * 生成预签名下载 URL。
     *
     * @param file 文件元数据
     * @return 预签名 URL
     */
    String getPresignedDownloadUrl(SysFile file);

    /**
     * 获取文件的 MinIO 输入流。
     *
     * @param file 文件元数据
     * @return 文件输入流
     */
    InputStream getFileStream(SysFile file);
}
