package com.jiayuan.boot.system.oss.service;

import com.jiayuan.boot.system.oss.model.entity.FileInfo;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileTreeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 对象存储服务接口层
 *
 * @author jiayuan
 * @since 2026/03/09
 */
public interface FileService {

    /**
     * 上传文件到指定父目录。
     *
     * @param file     表单文件对象
     * @param parentId 父目录ID（0 表示根目录）；需属于当前用户且未在回收站
     * @return 文件信息
     */
    FileInfo uploadFile(MultipartFile file, Long parentId);

    /**
     * 删除文件
     *
     * @param filePath 文件完整URL
     * @return 删除结果
     */
    boolean deleteFile(String filePath);

    /**
     * 获取当前目录下的文件和子目录列表，连同面包屑路径一并返回。
     *
     * @param parentId 父目录ID（0表示根目录）
     * @return 含 breadcrumb + items 的信封
     */
    default FileListResponseVO listFiles(Long parentId) {
        throw new UnsupportedOperationException("当前存储类型不支持文件列表查询");
    }

    /**
     * 获取当前用户的文件树（包含文件和目录，一次性返回完整树形结构）。
     *
     * @return 文件树列表（根级节点为顶层）
     */
    default List<FileTreeResponseVO> listFileTree() {
        throw new UnsupportedOperationException("当前存储类型不支持文件树查询");
    }

    /**
     * 下载文件
     *
     * @param fileId   文件ID
     * @param response HTTP响应对象
     */
    default void downloadFile(Long fileId, HttpServletResponse response) {
        throw new UnsupportedOperationException("当前存储类型不支持文件下载");
    }

    /**
     * 根据文件路径下载文件
     *
     * @param filePath 文件路径
     * @param response HTTP响应对象
     */
    default void downloadFile(String filePath, HttpServletResponse response) {
        throw new UnsupportedOperationException("当前存储类型不支持按路径下载文件");
    }

    /**
     * 根据ID删除文件
     *
     * @param fileId 文件ID
     * @return 删除结果
     */
    default boolean deleteFileById(Long fileId) {
        throw new UnsupportedOperationException("当前存储类型不支持按ID删除");
    }

    /**
     * 复制文件
     *
     * @param fileId            源文件ID
     * @param targetDirectoryId 目标目录ID
     * @return 复制后的文件信息
     */
    default FileInfoResponseVO copyFile(Long fileId, Long targetDirectoryId) {
        throw new UnsupportedOperationException("当前存储类型不支持文件复制");
    }

    /**
     * 移动文件/目录
     *
     * @param fileId            文件/目录ID
     * @param targetDirectoryId 目标目录ID
     * @return 移动后的文件信息
     */
    default FileInfoResponseVO moveFile(Long fileId, Long targetDirectoryId) {
        throw new UnsupportedOperationException("当前存储类型不支持文件移动");
    }

    /**
     * 生成文件的预签名下载 URL（短期有效，前端直接从对象存储下载）。
     * <p>
     * 注意：此方法不做用户归属校验，调用方（如分享服务、文件下载接口）需自行处理权限。
     *
     * @param fileId 文件ID
     * @return 预签名 URL
     */
    default String getPresignedDownloadUrl(Long fileId) {
        throw new UnsupportedOperationException("当前存储类型不支持预签名 URL");
    }

    /**
     * 按关键词搜索当前登录用户的文件（跨目录，仅搜文件不含目录）。
     *
     * @param keyword 搜索关键词（对 originalName 做 LIKE 匹配）
     * @return 匹配的文件列表
     */
    default List<FileInfoResponseVO> searchFiles(String keyword) {
        throw new UnsupportedOperationException("当前存储类型不支持文件搜索");
    }

    /**
     * （Bonus 4.3）列出当前用户回收站中的文件/目录。
     *
     * @return 回收站项列表
     */
    default List<RecycleBinItemResponseVO> listRecycleBin() {
        throw new UnsupportedOperationException("当前存储类型不支持回收站");
    }

    /**
     * （Bonus 4.3）从回收站恢复文件/目录；目录将递归恢复所有后代。
     *
     * @param fileId 要恢复的文件/目录ID
     */
    default void restoreFromRecycleBin(Long fileId) {
        throw new UnsupportedOperationException("当前存储类型不支持回收站");
    }

    /**
     * （Bonus 4.3）永久删除回收站中的文件/目录。
     * <p>
     * 执行真正的 MinIO 对象清理（配合 ref_count）、sys_file 软删除、配额归还。
     *
     * @param fileId 要永久删除的文件/目录ID
     */
    default void permanentlyDelete(Long fileId) {
        throw new UnsupportedOperationException("当前存储类型不支持回收站");
    }

}
