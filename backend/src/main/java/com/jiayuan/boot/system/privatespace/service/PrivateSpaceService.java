package com.jiayuan.boot.system.privatespace.service;

import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivatePasswordRequestVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSessionRequestVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSessionResponseVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSpaceStatusResponseVO;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 私密空间管理服务。
 *
 * @author charleslam
 * @since 2026/05/18
 */
public interface PrivateSpaceService {

    /**
     * 查询当前用户私密空间状态。
     *
     * @return 私密空间状态
     */
    PrivateSpaceStatusResponseVO getStatus();

    /**
     * 设置或修改当前用户私密空间密码。
     *
     * @param request 私密空间密码请求
     */
    void updatePassword(PrivatePasswordRequestVO request);

    /**
     * 校验私密空间密码并创建解锁会话。
     *
     * @param request 私密空间解锁请求
     * @return 私密空间解锁响应
     */
    PrivateSessionResponseVO unlock(PrivateSessionRequestVO request);

    /**
     * 列出私密空间子目录。
     *
     * @param parentId 父目录ID
     * @return 子目录列表
     */
    List<DirectoryNodeResponseVO> listDirectories(Long parentId);

    /**
     * 创建私密空间目录。
     *
     * @param request 创建目录请求
     * @return 创建后的目录信息
     */
    FileInfoResponseVO createDirectory(DirectoryCreateRequestVO request);

    /**
     * 列出私密空间文件和目录。
     *
     * @param parentId 父目录ID
     * @return 文件列表
     */
    FileListResponseVO listFiles(Long parentId);

    /**
     * 上传私密空间文件。
     *
     * @param file     上传文件
     * @param parentId 父目录ID
     * @return 上传后的文件信息
     */
    FileInfoResponseVO uploadFile(MultipartFile file, Long parentId);

    /**
     * 获取私密空间文件或目录详情。
     *
     * @param fileId 文件ID
     * @return 文件或目录详情
     */
    FileInfoResponseVO getFile(Long fileId);

    /**
     * 下载私密空间文件。
     *
     * @param fileId   文件ID
     * @param response HTTP响应
     */
    void downloadFile(Long fileId, HttpServletResponse response);

    /**
     * 移动私密空间文件或目录。
     *
     * @param fileId            文件ID
     * @param targetDirectoryId 目标目录ID
     */
    void moveFile(Long fileId, Long targetDirectoryId);

    /**
     * 删除私密空间文件或目录到回收站。
     *
     * @param fileId 文件ID
     */
    void deleteToTrash(Long fileId);

    /**
     * 列出私密空间回收站根节点。
     *
     * @return 回收站列表
     */
    List<RecycleBinItemResponseVO> listTrash();

    /**
     * 恢复私密空间回收站根节点。
     *
     * @param trashId        回收站根节点ID
     * @param conflictPolicy 同名冲突处理策略
     * @return 恢复后的文件信息
     */
    FileInfoResponseVO restoreTrash(Long trashId, ConflictPolicy conflictPolicy);

    /**
     * 永久删除私密空间回收站根节点。
     *
     * @param trashId 回收站根节点ID
     */
    void permanentlyDeleteTrash(Long trashId);

    /**
     * 处理用户 VIP 状态变化后的私密空间生命周期。
     *
     * @param userId 用户ID
     * @param vip    是否为 VIP
     */
    void handleVipStateChanged(Long userId, boolean vip);
}
