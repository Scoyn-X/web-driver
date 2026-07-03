package com.jiayuan.boot.system.team.service;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileTreeResponseVO;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import com.jiayuan.boot.system.team.model.vo.TeamFileResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamTrashItemResponseVO;
import com.jiayuan.boot.system.team.model.vo.TransferFromPersonalRequestVO;
import com.jiayuan.boot.system.team.model.vo.TransferToPersonalRequestVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 团队文件服务接口。
 *
 * @author charleslam
 * @since 2026/05/18
 */
public interface TeamFileService {

    /**
     * 团队回收站保留天数。
     */
    long TRASH_RETENTION_DAYS = 3L;

    /**
     * 团队回收站恢复同名冲突异常。
     */
    @Getter
    class RestoreConflictException extends BusinessException {

        private final TeamFileResponseVO conflictFile;

        public RestoreConflictException(TeamFileResponseVO conflictFile) {
            super(ResultCode.USER_REQUEST_PARAMETER_ERROR, "目标目录下已存在同名文件或目录");
            this.conflictFile = conflictFile;
        }
    }

    /**
     * 列出团队目录。
     *
     * @param teamId   团队ID
     * @param parentId 父目录ID
     * @return 团队目录节点列表
     */
    List<DirectoryNodeResponseVO> listDirectories(Long teamId, Long parentId);

    /**
     * 创建团队目录。
     *
     * @param teamId  团队ID
     * @param request 创建目录请求
     * @return 团队文件响应
     */
    TeamFileResponseVO createDirectory(Long teamId, DirectoryCreateRequestVO request);

    /**
     * 重命名团队目录。
     *
     * @param teamId      团队ID
     * @param directoryId 目录ID
     * @param request     目录重命名请求
     * @return 团队文件响应
     */
    TeamFileResponseVO renameDirectory(Long teamId, Long directoryId, DirectoryRenameRequestVO request);

    /**
     * 上传团队文件。
     *
     * @param teamId   团队ID
     * @param file     上传文件
     * @param parentId 父目录ID
     * @return 团队文件响应
     */
    TeamFileResponseVO uploadFile(Long teamId, MultipartFile file, Long parentId);

    /**
     * 列出团队文件。
     *
     * @param teamId   团队ID
     * @param parentId 父目录ID
     * @return 团队文件列表
     */
    FileListResponseVO listFiles(Long teamId, Long parentId);

    /**
     * 获取团队文件详情。
     *
     * @param teamId 团队ID
     * @param fileId 文件或目录ID
     * @return 团队文件详情
     */
    TeamFileResponseVO getFile(Long teamId, Long fileId);

    /**
     * 下载团队文件。
     *
     * @param teamId   团队ID
     * @param fileId   文件ID
     * @param response HTTP响应
     */
    void downloadFile(Long teamId, Long fileId, HttpServletResponse response);

    /**
     * 移动团队文件或目录。
     *
     * @param teamId            团队ID
     * @param fileId            文件或目录ID
     * @param targetDirectoryId 目标目录ID
     */
    void moveFile(Long teamId, Long fileId, Long targetDirectoryId);

    /**
     * 复制团队文件或目录。
     *
     * @param teamId            团队ID
     * @param fileId            文件或目录ID
     * @param targetDirectoryId 目标目录ID
     * @return 复制后的团队文件响应
     */
    TeamFileResponseVO copyFile(Long teamId, Long fileId, Long targetDirectoryId);

    /**
     * 转存个人文件或目录到团队。
     *
     * @param teamId  团队ID
     * @param request 转存请求
     * @return 转存后的团队文件响应
     */
    TeamFileResponseVO transferFromPersonal(Long teamId, TransferFromPersonalRequestVO request);

    /**
     * 转存团队文件或目录到个人空间。
     *
     * @param teamId 团队ID
     * @param fileId 团队文件或目录ID
     * @param request 转存请求
     * @return 转存后的个人文件响应
     */
    FileInfoResponseVO transferToPersonal(Long teamId, Long fileId, TransferToPersonalRequestVO request);

    /**
     * 删除团队文件或目录到回收站。
     *
     * @param teamId 团队ID
     * @param fileId 文件或目录ID
     */
    void deleteToTrash(Long teamId, Long fileId);

    /**
     * 列出团队回收站根节点。
     *
     * @param teamId 团队ID
     * @return 团队回收站文件列表
     */
    List<TeamTrashItemResponseVO> listTrash(Long teamId);

    /**
     * 恢复团队回收站根节点。
     *
     * @param teamId         团队ID
     * @param trashId        回收站根节点ID
     * @param conflictPolicy 同名冲突处理策略
     * @return 恢复后的团队文件响应
     */
    TeamFileResponseVO restoreTrash(Long teamId, Long trashId, ConflictPolicy conflictPolicy);

    /**
     * 永久删除团队回收站根节点。
     *
     * @param teamId  团队ID
     * @param trashId 回收站根节点ID
     */
    void permanentlyDeleteTrash(Long teamId, Long trashId);

    /**
     * 搜索团队文件。
     *
     * @param teamId  团队ID
     * @param keyword 搜索关键词
     * @return 团队文件列表
     */
    List<TeamFileResponseVO> searchFiles(Long teamId, String keyword);

    /**
     * 列出团队文件树。
     *
     * @param teamId 团队ID
     * @return 团队文件树
     */
    List<FileTreeResponseVO> listFileTree(Long teamId);
}
