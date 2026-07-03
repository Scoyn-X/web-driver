package com.jiayuan.boot.system.share.service;

import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareAccessResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareCreateRequestVO;
import com.jiayuan.boot.system.share.model.vo.ShareDownloadResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareInfoResponseVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 文件分享服务接口
 *
 * @author charleslam
 * @since 2026/04/14
 */
public interface ShareService {

    /**
     * 创建文件分享
     *
     * @param request 创建请求
     * @return 新建的分享信息（含 shareToken、提取码等）
     */
    ShareInfoResponseVO createShare(ShareCreateRequestVO request);

    /**
     * 创建团队文件分享
     *
     * @param teamId  团队ID
     * @param request 创建请求
     * @return 新建的分享信息
     */
    ShareInfoResponseVO createTeamShare(Long teamId, ShareCreateRequestVO request);

    /**
     * 查询当前登录用户创建的所有分享
     *
     * @return 分享列表
     */
    List<ShareInfoResponseVO> listMyShares();

    /**
     * 查询团队全部分享
     *
     * @param teamId 团队ID
     * @return 团队分享列表
     */
    List<ShareInfoResponseVO> listTeamShares(Long teamId);

    /**
     * 查询团队分享详情
     *
     * @param teamId  团队ID
     * @param shareId 分享记录ID
     * @return 分享详情
     */
    ShareInfoResponseVO getTeamShare(Long teamId, Long shareId);

    /**
     * 取消分享（将 status 置为 1）
     *
     * @param id 分享记录 ID
     */
    void cancelShare(Long id);

    /**
     * 取消团队分享
     *
     * @param teamId  团队ID
     * @param shareId 分享记录ID
     */
    void cancelTeamShare(Long teamId, Long shareId);

    /**
     * 失效团队全部分享。
     *
     * @param teamId 团队ID
     */
    void invalidateTeamShares(Long teamId);

    /**
     * 失效指定成员创建的团队分享。
     *
     * @param teamId 团队ID
     * @param accountId 成员账户ID
     */
    void invalidateTeamSharesByCreator(Long teamId, Long accountId);

    /**
     * 匿名访问分享页：返回文件基本信息 + 是否需要提取码
     *
     * @param shareToken 分享标识
     * @return 分享页展示信息
     */
    ShareAccessResponseVO getShareByToken(String shareToken);

    /**
     * 匿名列出分享目录内的子项。
     *
     * @param shareToken 分享标识
     * @param parentId   父目录ID，null/0 表示分享根目录
     * @param extractCode 提取码（全公开时可为 null）
     * @return 分享目录范围内的文件列表
     */
    FileListResponseVO listSharedChildren(String shareToken, Long parentId, String extractCode);

    /**
     * 校验分享提取码（accessType=1 时使用）
     *
     * @param shareToken  分享标识
     * @param extractCode 用户提交的提取码
     */
    void verifyExtractCode(String shareToken, String extractCode);

    /**
     * 生成分享文件的后端受控下载 URL。
     * <p>
     * 若为分享码访问，需要传入正确的提取码；若为全公开，extractCode 可为 null。
     *
     * @param shareToken  分享标识
     * @param extractCode 提取码（全公开时可为 null）
     * @return 下载响应（含后端受控下载 URL 与文件名）
     */
    ShareDownloadResponseVO getDownloadUrl(String shareToken, String extractCode);

    /**
     * 生成分享范围内指定文件的后端受控下载 URL。
     *
     * @param shareToken  分享标识
     * @param extractCode 提取码（全公开时可为 null）
     * @param fileId      分享目录内的文件ID，null 表示分享根节点
     * @return 下载响应（含后端受控下载 URL 与文件名）
     */
    ShareDownloadResponseVO getDownloadUrl(String shareToken, String extractCode, Long fileId);

    /**
     * 后端中转下载分享文件（含限速），流式写入 HTTP 响应。
     *
     * @param shareToken  分享标识
     * @param extractCode 提取码（全公开时可为 null）
     * @param fileId      文件ID，null 表示分享根节点
     * @param response    HTTP 响应
     */
    void downloadFile(String shareToken, String extractCode, Long fileId, HttpServletResponse response);

    /**
     * SSE 进度下载分享文件，同时推送进度事件和文件数据。
     *
     * @param shareToken  分享标识
     * @param extractCode 提取码（全公开时可为 null）
     * @param fileId      文件ID，null 表示分享根节点
     * @return SSE 发射器
     */
    SseEmitter downloadWithProgress(String shareToken, String extractCode, Long fileId);

}
