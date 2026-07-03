package com.jiayuan.boot.system.share.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.common.util.StringUtils;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.oss.utils.DownloadThrottleSupport;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.share.converter.ShareFileConverter;
import com.jiayuan.boot.system.share.converter.SysShareConverter;
import com.jiayuan.boot.system.share.model.bo.ShareBuildContextBO;
import com.jiayuan.boot.system.share.mapper.SysShareMapper;
import com.jiayuan.boot.system.share.model.entity.SysShare;
import com.jiayuan.boot.system.share.model.vo.ShareAccessResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareCreateRequestVO;
import com.jiayuan.boot.system.share.model.vo.ShareDownloadResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareInfoResponseVO;
import com.jiayuan.boot.system.share.service.ShareService;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 文件分享服务实现
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShareServiceImpl implements ShareService {

    /** 提取码候选字符集：去除易混淆字符 0/O、1/I/L，提取码为 4 位 */
    private static final String EXTRACT_CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int EXTRACT_CODE_LENGTH = 4;
    private static final long ROOT_ID = 0L;
    private static final String SPACE_TYPE_PERSONAL = "PERSONAL";
    private static final String SPACE_TYPE_TEAM = "TEAM";

    /** 访问方式枚举 */
    private static final int ACCESS_TYPE_PUBLIC = 0;
    private static final int ACCESS_TYPE_EXTRACT_CODE = 1;

    /** 分享状态枚举：主动取消使用逻辑删除，团队生命周期联动使用 status 失效。 */
    private static final int STATUS_VALID = 0;
    private static final int STATUS_CANCELLED = 1;
    private static final String TEAM_SHARE_MANAGE_PERMISSION = "share:manage";

    /** SSE 进度下载配置 */
    private static final int SSE_CHUNK_SIZE = 262144; // 256KB
    private static final long SSE_PROGRESS_INTERVAL_MS = 500;
    private static final long SSE_TIMEOUT_MS = 3600000; // 1小时

    private final SysShareMapper sysShareMapper;
    private final SysFileMapper sysFileMapper;
    private final SysShareConverter sysShareConverter;
    private final ShareFileConverter shareFileConverter;
    private final FileObjectService fileObjectService;
    private final TeamPermissionService teamPermissionService;
    private final DownloadThrottleSupport downloadThrottleSupport;

    /**
     * 创建文件分享
     */
    @Override
    @Transactional
    public ShareInfoResponseVO createShare(ShareCreateRequestVO request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        SysFile file = sysFileMapper.selectPersonalShareFile(currentUserId, request.getFileId());
        if (file == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "被分享的文件不存在");
        }
        if (!currentUserId.equals(file.getUserId())) {
            throw new BusinessException(ResultCode.SHARE_NOT_OWNER, "只能分享自己的文件");
        }
        if (isInRecycleBin(file)) {
            throw new BusinessException(ResultCode.SHARE_FILE_DELETED);
        }

        SysShare share = buildPersonalShare(request, currentUserId);

        sysShareMapper.insert(share);

        return toShareInfoVO(share, file);
    }

    /**
     * 创建团队文件分享
     */
    @Override
    @Transactional
    public ShareInfoResponseVO createTeamShare(Long teamId, ShareCreateRequestVO request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long currentAccountId = SecurityUtils.getCurrentAccountId();

        SysFile file = sysFileMapper.selectTeamFile(teamId, request.getFileId());
        if (file == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "被分享的团队文件不存在");
        }
        if (isInRecycleBin(file)) {
            throw new BusinessException(ResultCode.SHARE_FILE_DELETED);
        }

        SysShare share = buildTeamShare(request, currentUserId, currentAccountId, teamId);
        sysShareMapper.insert(share);

        return toShareInfoVO(share, file);
    }

    /**
     * 列出当前用户的分享
     */
    @Override
    public List<ShareInfoResponseVO> listMyShares() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<SysShare> shares = sysShareMapper.selectPersonalShares(currentUserId);
        if (shares.isEmpty()) {
            return Collections.emptyList();
        }

        // 同时带回 SysFile 以便判断「被分享文件是否已删除/入回收站」，用于 statusDesc 降级。
        Set<Long> fileIds = shares.stream().map(SysShare::getFileId).collect(Collectors.toSet());
        Map<Long, SysFile> fileMap = sysFileMapper.selectPersonalShareFilesByIds(currentUserId, fileIds).stream()
                .collect(Collectors.toMap(SysFile::getId, f -> f, (a, b) -> a));

        return shares.stream()
                .map(s -> toShareInfoVO(s, fileMap.get(s.getFileId())))
                .toList();
    }

    /**
     * 列出团队全部分享
     */
    @Override
    public List<ShareInfoResponseVO> listTeamShares(Long teamId) {
        List<SysShare> shares = sysShareMapper.selectTeamShares(teamId);
        if (shares.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> fileIds = shares.stream().map(SysShare::getFileId).collect(Collectors.toSet());
        Map<Long, SysFile> fileMap = sysFileMapper.selectFilesInSpaceByIds(SPACE_TYPE_TEAM, teamId, fileIds).stream()
                .collect(Collectors.toMap(SysFile::getId, f -> f, (a, b) -> a));

        return shares.stream()
                .map(share -> toShareInfoVO(share, fileMap.get(share.getFileId())))
                .toList();
    }

    /**
     * 获取团队分享详情
     */
    @Override
    public ShareInfoResponseVO getTeamShare(Long teamId, Long shareId) {
        SysShare share = loadTeamShare(teamId, shareId);
        SysFile file = sysFileMapper.selectTeamFile(teamId, share.getFileId());
        return toShareInfoVO(share, file);
    }

    /**
     * 取消分享
     */
    @Override
    @Transactional
    public void cancelShare(Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        SysShare share = sysShareMapper.selectPersonalShare(currentUserId, id);
        if (share == null) {
            // @TableLogic 已自动过滤已删记录，此分支也覆盖「重复取消」场景。
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
        if (!currentUserId.equals(share.getUserId())) {
            throw new BusinessException(ResultCode.SHARE_NOT_OWNER);
        }
        // 取消即逻辑删除（BaseEntity.@TableLogic）。后续 listMyShares / findValidShare
        // 将不会再返回该记录，前端不再看到「已取消」状态。
        sysShareMapper.deleteById(id);
    }

    /**
     * 取消团队分享
     */
    @Override
    @Transactional
    public void cancelTeamShare(Long teamId, Long shareId) {
        SysShare share = loadTeamShare(teamId, shareId);
        Long currentAccountId = SecurityUtils.getCurrentAccountId();
        if (!currentAccountId.equals(share.getCreatorAccountId())) {
            teamPermissionService.checkPermission(teamId, currentAccountId, TEAM_SHARE_MANAGE_PERMISSION);
        }
        int affectedRows = sysShareMapper.deleteTeamShare(teamId, shareId);
        if (affectedRows == 0) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
    }

    /**
     * 失效团队全部分享。
     */
    @Override
    @Transactional
    public void invalidateTeamShares(Long teamId) {
        sysShareMapper.invalidateTeamShares(teamId);
    }

    /**
     * 失效指定成员创建的团队分享。
     */
    @Override
    @Transactional
    public void invalidateTeamSharesByCreator(Long teamId, Long accountId) {
        sysShareMapper.invalidateTeamSharesByCreator(teamId, accountId);
    }

    /**
     * 根据分享标识获取分享页信息
     */
    @Override
    public ShareAccessResponseVO getShareByToken(String shareToken) {
        SysShare share = findValidShare(shareToken);
        SysFile file = loadSharedFile(share);
        if (file == null || isInRecycleBin(file)) {
            // Bonus 4.3：文件在回收站对分享者而言等同已删除
            throw new BusinessException(ResultCode.SHARE_FILE_DELETED);
        }

        file = loadSharedAccessFile(share, file);
        boolean requireExtractCode = ACCESS_TYPE_EXTRACT_CODE == share.getAccessType();
        return sysShareConverter.toShareAccessResponseVO(file, requireExtractCode);
    }

    /**
     * 列出分享目录内容
     */
    @Override
    public FileListResponseVO listSharedChildren(String shareToken, Long parentId, String extractCode) {
        SysShare share = findValidShare(shareToken);
        requireExtractCodeIfNeeded(share, extractCode);

        SysFile root = loadSharedFile(share);
        if (root == null || isInRecycleBin(root)) {
            throw new BusinessException(ResultCode.SHARE_FILE_DELETED);
        }
        if (!isDirectory(root)) {
            throw new BusinessException(ResultCode.SHARE_EXCEPTION, "文件分享不支持目录浏览");
        }

        String spaceType = resolveSpaceType(share);
        Long spaceId = resolveSpaceId(share);
        Long resolvedParentId = resolveSharedParentId(parentId, root.getId());
        SysFile currentDirectory = resolveSharedDirectory(spaceType, spaceId, root, resolvedParentId);
        List<SysFile> children = sysFileMapper.selectChildrenInSpace(spaceType, spaceId, currentDirectory.getId());
        List<FileInfoResponseVO> items = shareFileConverter.toSharedFileInfoVOList(children, root.getId());
        List<BreadcrumbItemResponseVO> breadcrumb = buildSharedBreadcrumb(
                spaceType, spaceId, root, currentDirectory);
        return shareFileConverter.toFileListResponseVO(items, breadcrumb);
    }

    /**
     * 校验分享提取码
     */
    @Override
    public void verifyExtractCode(String shareToken, String extractCode) {
        SysShare share = findValidShare(shareToken);
        if (ACCESS_TYPE_EXTRACT_CODE != share.getAccessType()) {
            return; // 全公开分享无需校验，直接通过
        }
        if (extractCode == null || !extractCode.equalsIgnoreCase(share.getExtractCode())) {
            throw new BusinessException(ResultCode.SHARE_EXTRACT_CODE_WRONG);
        }
    }

    /**
     * 获取分享文件下载链接
     */
    @Override
    public ShareDownloadResponseVO getDownloadUrl(String shareToken, String extractCode) {
        return getDownloadUrl(shareToken, extractCode, null);
    }

    /**
     * 获取分享范围内文件下载链接。
     */
    @Override
    public ShareDownloadResponseVO getDownloadUrl(String shareToken, String extractCode, Long fileId) {
        SysShare share = findValidShare(shareToken);

        requireExtractCodeIfNeeded(share, extractCode);

        SysFile file = loadSharedFile(share);
        if (file == null || isInRecycleBin(file)) {
            throw new BusinessException(ResultCode.SHARE_FILE_DELETED);
        }
        file = resolveSharedDownloadFile(share, file, fileId);
        if (isDirectory(file)) {
            throw new BusinessException(ResultCode.SHARE_EXCEPTION, "暂不支持通过分享链接下载目录");
        }

        String url = buildControlledDownloadUrl(shareToken, extractCode, fileId);
        return buildShareDownloadVO(url, file.getOriginalName());
    }

    /**
     * 后端中转下载分享文件（含限速）。
     */
    @Override
    public void downloadFile(String shareToken, String extractCode, Long fileId, HttpServletResponse response) {
        SysShare share = findValidShare(shareToken);
        requireExtractCodeIfNeeded(share, extractCode);

        SysFile root = loadSharedFile(share);
        if (root == null || isInRecycleBin(root)) {
            throw new BusinessException(ResultCode.SHARE_FILE_DELETED);
        }
        SysFile file = resolveSharedDownloadFile(share, root, fileId);
        if (isDirectory(file)) {
            throw new BusinessException(ResultCode.SHARE_EXCEPTION, "暂不支持通过分享链接下载目录");
        }

        fileObjectService.writeSharedToResponse(file, response);
    }

    /**
     * SSE 进度下载分享文件。
     */
    @Override
    public SseEmitter downloadWithProgress(String shareToken, String extractCode, Long fileId) {
        SysShare share = findValidShare(shareToken);
        requireExtractCodeIfNeeded(share, extractCode);

        SysFile root = loadSharedFile(share);
        if (root == null || isInRecycleBin(root)) {
            throw new BusinessException(ResultCode.SHARE_FILE_DELETED);
        }
        SysFile file = resolveSharedDownloadFile(share, root, fileId);
        if (isDirectory(file)) {
            throw new BusinessException(ResultCode.SHARE_EXCEPTION, "暂不支持通过分享链接下载目录");
        }

        String fileName = file.getOriginalName();
        Long totalSize = file.getFileSize() != null ? file.getFileSize() : 0L;
        boolean throttled = downloadThrottleSupport.shouldThrottleSharedDownload(totalSize);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        CompletableFuture.runAsync(() -> {
            try (InputStream stream = fileObjectService.getFileStream(file)) {
                sendSseEvent(emitter, "start", String.format(
                        "{\"fileName\":\"%s\",\"totalSize\":%d}", fileName, totalSize));

                byte[] buffer = new byte[SSE_CHUNK_SIZE];
                long totalRead = 0;
                long lastProgressTime = System.currentTimeMillis();
                long startTime = lastProgressTime;
                int bytesRead;

                while ((bytesRead = stream.read(buffer)) != -1) {
                    totalRead += bytesRead;
                    long now = System.currentTimeMillis();

                    if (now - lastProgressTime >= SSE_PROGRESS_INTERVAL_MS) {
                        long elapsedMs = Math.max(1, now - startTime);
                        long speed = totalRead * 1000L / elapsedMs;
                        int percent = totalSize > 0 ? (int) (totalRead * 100 / totalSize) : 0;
                        sendSseEvent(emitter, "progress", String.format(
                                "{\"loaded\":%d,\"total\":%d,\"percent\":%d,\"speed\":%d}",
                                totalRead, totalSize, percent, speed));
                        lastProgressTime = now;
                    }

                    byte[] chunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, chunk, 0, bytesRead);
                    sendSseEvent(emitter, "chunk",
                            Base64.getEncoder().encodeToString(chunk));
                    downloadThrottleSupport.throttleIfNecessary(throttled, bytesRead);
                }

                long elapsedMs = Math.max(1, System.currentTimeMillis() - startTime);
                long speed = totalRead * 1000L / elapsedMs;
                int percent = 100;
                sendSseEvent(emitter, "progress", String.format(
                        "{\"loaded\":%d,\"total\":%d,\"percent\":%d,\"speed\":%d}",
                        totalRead, totalSize, percent, speed));
                sendSseEvent(emitter, "complete", String.format(
                        "{\"fileName\":\"%s\",\"totalSize\":%d}", fileName, totalSize));
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 进度下载失败: {}", fileName, e);
                sendSseEvent(emitter, "error",
                        "{\"message\":\"下载失败: " + e.getMessage() + "\"}");
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sendSseEvent(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.debug("SSE 事件发送失败，客户端可能已断开");
        }
    }

    /**
     * 根据 token 查出分享记录，并校验状态（未过期）。
     * <p>已取消 = 已逻辑删除，由 MyBatis-Plus @TableLogic 自动过滤，selectOne 直接返回 null。
     */
    private SysShare findValidShare(String shareToken) {
        SysShare share = sysShareMapper.selectShareByToken(shareToken);
        if (share == null) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
        if (share.getStatus() != null && STATUS_VALID != share.getStatus()) {
            throw new BusinessException(ResultCode.SHARE_CANCELLED);
        }
        if (share.getExpireTime() != null && LocalDateTime.now().isAfter(share.getExpireTime())) {
            throw new BusinessException(ResultCode.SHARE_EXPIRED);
        }
        return share;
    }

    private SysFile loadSharedFile(SysShare share) {
        if (SPACE_TYPE_TEAM.equals(share.getSpaceType())) {
            return sysFileMapper.selectTeamFile(share.getTeamId(), share.getFileId());
        }
        return sysFileMapper.selectPersonalShareFile(share.getUserId(), share.getFileId());
    }

    private SysFile loadSharedAccessFile(SysShare share, SysFile file) {
        if (!isDirectory(file)) {
            return file;
        }
        SysFile sizedFile = sysFileMapper.selectSpaceFileWithRecursiveSize(
                resolveSpaceType(share), resolveSpaceId(share), file.getId());
        return sizedFile == null ? file : sizedFile;
    }

    private SysFile resolveSharedDownloadFile(SysShare share, SysFile root, Long fileId) {
        if (fileId == null || root.getId().equals(fileId)) {
            return root;
        }
        if (!isDirectory(root)) {
            throw new BusinessException(ResultCode.SHARE_EXCEPTION, "文件分享不支持下载其他文件");
        }
        String spaceType = resolveSpaceType(share);
        Long spaceId = resolveSpaceId(share);
        SysFile file = sysFileMapper.selectSpaceFile(spaceType, spaceId, fileId);
        if (file == null || isInRecycleBin(file) || !isInSharedTree(root, file)) {
            throw new BusinessException(ResultCode.SHARE_EXCEPTION, "文件不在分享范围内");
        }
        return file;
    }

    private boolean isInSharedTree(SysFile root, SysFile file) {
        if (root.getId().equals(file.getId())) {
            return true;
        }
        return StringUtils.parseIdList(file.getFullPath()).contains(root.getId());
    }

    private void requireExtractCodeIfNeeded(SysShare share, String extractCode) {
        if (ACCESS_TYPE_EXTRACT_CODE == share.getAccessType()
                && (extractCode == null || !extractCode.equalsIgnoreCase(share.getExtractCode()))) {
            throw new BusinessException(ResultCode.SHARE_EXTRACT_CODE_WRONG);
        }
    }

    private Long resolveSpaceId(SysShare share) {
        return SPACE_TYPE_TEAM.equals(share.getSpaceType()) ? share.getTeamId() : share.getUserId();
    }

    private String resolveSpaceType(SysShare share) {
        return SPACE_TYPE_TEAM.equals(share.getSpaceType()) ? SPACE_TYPE_TEAM : SPACE_TYPE_PERSONAL;
    }

    private Long resolveSharedParentId(Long parentId, Long rootId) {
        return parentId == null || ROOT_ID == parentId ? rootId : parentId;
    }

    private SysFile resolveSharedDirectory(String spaceType, Long spaceId, SysFile root, Long parentId) {
        if (root.getId().equals(parentId)) {
            return root;
        }
        SysFile directory = sysFileMapper.selectActiveDirectoryInSharedTree(spaceType, spaceId, root.getId(), parentId);
        if (directory == null) {
            throw new BusinessException(ResultCode.SHARE_EXCEPTION, "目录不在分享范围内");
        }
        return directory;
    }

    private List<BreadcrumbItemResponseVO> buildSharedBreadcrumb(String spaceType, Long spaceId,
                                                                 SysFile root, SysFile currentDirectory) {
        List<Long> ids = StringUtils.parseIdList(currentDirectory.getFullPath());
        int rootIndex = ids.indexOf(root.getId());
        if (rootIndex < 0) {
            throw new BusinessException(ResultCode.SHARE_EXCEPTION, "目录不在分享范围内");
        }
        List<Long> sharedIds = ids.subList(rootIndex, ids.size());
        Map<Long, String> idToName = loadSharedPathNames(spaceType, spaceId, root, sharedIds);
        List<BreadcrumbItemResponseVO> crumbs = new ArrayList<>(sharedIds.size());
        sharedIds.forEach(id -> crumbs.add(new BreadcrumbItemResponseVO(id, idToName.getOrDefault(id, "?"))));
        return crumbs;
    }

    private Map<Long, String> loadSharedPathNames(String spaceType, Long spaceId, SysFile root, List<Long> sharedIds) {
        if (sharedIds.size() == 1) {
            return Map.of(root.getId(), root.getOriginalName());
        }
        return sysFileMapper.selectFilesInSpaceByIds(spaceType, spaceId, sharedIds).stream()
                .collect(Collectors.toMap(SysFile::getId, SysFile::getOriginalName, (a, b) -> a));
    }

    private SysShare loadTeamShare(Long teamId, Long shareId) {
        SysShare share = sysShareMapper.selectTeamShare(teamId, shareId);
        if (share == null) {
            throw new BusinessException(ResultCode.SHARE_NOT_FOUND);
        }
        return share;
    }

    private SysShare buildPersonalShare(ShareCreateRequestVO request, Long currentUserId) {
        String shareToken = IdUtil.simpleUUID();
        return sysShareConverter.toSysShare(request, buildShareContext(currentUserId, null, shareToken, null, request));
    }

    private SysShare buildTeamShare(ShareCreateRequestVO request, Long currentUserId,
                                    Long currentAccountId, Long teamId) {
        String shareToken = IdUtil.simpleUUID();
        return sysShareConverter.toTeamSysShare(
                request, buildShareContext(currentUserId, currentAccountId, shareToken, teamId, request));
    }

    private ShareBuildContextBO buildShareContext(Long userId, Long creatorAccountId, String shareToken,
                                                  Long teamId, ShareCreateRequestVO request) {
        return new ShareBuildContextBO(
                userId, creatorAccountId, shareToken, teamId, resolveExtractCode(request), resolveExpireTime(request));
    }

    private String resolveExtractCode(ShareCreateRequestVO request) {
        return ACCESS_TYPE_EXTRACT_CODE == request.getAccessType()
                ? RandomUtil.randomString(EXTRACT_CODE_CHARS, EXTRACT_CODE_LENGTH)
                : null;
    }

    private LocalDateTime resolveExpireTime(ShareCreateRequestVO request) {
        return request.getExpireDays() == null ? null : LocalDateTime.now().plusDays(request.getExpireDays());
    }

    /**
     * 组装 ShareInfoVO：基础字段走 MapStruct，fileName / statusDesc / isDirectory 由本方法补充。
     */
    private ShareInfoResponseVO toShareInfoVO(SysShare share, SysFile file) {
        String fileName = file == null ? "[已删除]" : file.getOriginalName();
        boolean fileAvailable = file != null && !isInRecycleBin(file);
        return sysShareConverter.toShareInfoVO(
                share, fileName, resolveStatusDesc(share, fileAvailable), resolveIsDirectory(file));
    }

    /**
     * 判断 sys_file 是否已被放入回收站（Bonus 4.3）。
     */
    private boolean isInRecycleBin(SysFile file) {
        return file.getInRecycleBin() != null && file.getInRecycleBin() == 1;
    }

    private boolean isDirectory(SysFile file) {
        return file.getIsDirectory() != null && file.getIsDirectory() == 1;
    }

    private Boolean resolveIsDirectory(SysFile file) {
        return file == null || file.getIsDirectory() == null ? null : file.getIsDirectory() == 1;
    }

    /**
     * 构建分享下载响应VO
     */
    private ShareDownloadResponseVO buildShareDownloadVO(String downloadUrl, String fileName) {
        return new ShareDownloadResponseVO(downloadUrl, fileName);
    }

    private String buildControlledDownloadUrl(String shareToken, String extractCode, Long fileId) {
        var builder = ServletUriComponentsBuilder.fromCurrentContextPath()
                .pathSegment("api", "v1", "s", shareToken, "download", "file");
        if (extractCode != null && !extractCode.isBlank()) {
            builder.queryParam("code", extractCode);
        }
        if (fileId != null) {
            builder.queryParam("fileId", fileId);
        }
        return builder.build().encode().toUriString();
    }

    private String resolveStatusDesc(SysShare share, boolean fileAvailable) {
        // 已取消 = 已逻辑删除，listMyShares 已查不到，故此处不再有「已取消」分支。
        // 过期优先级高于「文件被删」：给分享者明确是过期（不可恢复）还是文件异常（恢复文件即可再分享）。
        if (share.getStatus() != null && STATUS_CANCELLED == share.getStatus()) {
            return "已取消";
        }
        if (share.getExpireTime() != null && LocalDateTime.now().isAfter(share.getExpireTime())) {
            return "已过期";
        }
        if (!fileAvailable) {
            return "文件已删除";
        }
        return "有效";
    }

}
