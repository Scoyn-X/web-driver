package com.jiayuan.boot.system.oss.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import com.jiayuan.boot.common.util.StringUtils;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.auth.model.entity.SysUser;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.mapper.SysFileObjectMapper;
import com.jiayuan.boot.system.oss.model.entity.FileInfo;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileTreeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import com.jiayuan.boot.system.oss.service.FileService;
import com.jiayuan.boot.system.oss.utils.DownloadThrottleSupport;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * MinIO文件上传服务类
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Component
@ConditionalOnProperty(value = "oss.type", havingValue = "minio")
@ConfigurationProperties(prefix = "oss.minio")
@RequiredArgsConstructor
@Data
@Slf4j
public class MinioFileServiceImpl implements FileService {

    private static final String SPACE_TYPE_PERSONAL = "PERSONAL";
    private static final long ROOT_ID = 0L;

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String customDomain;

    private MinioClient minioClient;

    private final SysFileMapper sysFileMapper;
    private final SysFileConverter sysFileConverter;
    private final QuotaService quotaService;
    /** 文件指纹去重 mapper（Bonus 4.2） */
    private final SysFileObjectMapper sysFileObjectMapper;
    private final SysUserMapper sysUserMapper;
    private final SystemConfigProperties configProperties;
    private final DownloadThrottleSupport downloadThrottleSupport;
    /**
     * 初始化MinIO存储桶
     */
    @PostConstruct
    public void init() {
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    // ========================= 私有辅助方法 =========================

    /**
     * 构造文件实体
     */
    private SysFile buildSysFile(String originalName, String storedName, String filePath,
                                  String fileUrl, Long fileSize, String mimeType,
                                  Long userId, Long parentId, Integer isDirectory,
                                  String fileHash) {
        SysFile sysFile = new SysFile();
        sysFile.setOriginalName(originalName);
        sysFile.setStoredName(storedName);
        sysFile.setFilePath(filePath);
        sysFile.setFileUrl(fileUrl);
        sysFile.setFileSize(fileSize);
        sysFile.setMimeType(mimeType);
        sysFile.setUserId(userId);
        sysFile.setSpaceType(SPACE_TYPE_PERSONAL);
        sysFile.setSpaceId(userId);
        sysFile.setUploaderId(userId);
        sysFile.setParentId(parentId);
        sysFile.setIsDirectory(isDirectory);
        sysFile.setFileHash(fileHash);
        return sysFile;
    }

    // ========================= 公开接口方法 =========================

    @Override
    @Transactional
    public FileInfo uploadFile(MultipartFile file, Long parentId) {
        createBucketIfAbsent(bucketName);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        quotaService.checkSingleFileLimit(currentUserId, file.getSize());
        quotaService.checkQuota(currentUserId, file.getSize());

        if (parentId == null) {
            parentId = ROOT_ID;
        }
        // 校验目标目录合法性（存在 / 属于当前用户 / 是目录 / 不在回收站），返回祖先 fullPath
        String parentFullPath = parentId == 0L ? "" : validateTargetDirectory(parentId, currentUserId);

        String originalFilename = file.getOriginalFilename();

        // 同名自动追加序号（如 a.txt → a(1).txt），与复制/移动保持一致的用户体验
        originalFilename = generateUniqueFileName(originalFilename, currentUserId, parentId);

        String suffix = FileUtil.getSuffix(originalFilename);

        try {
            // 读入内存并计算 SHA-256（Bonus 4.2 文件指纹去重）
            byte[] bytes = file.getBytes();
            String fileHash = DigestUtil.sha256Hex(bytes);

            // 命中已有物理对象 → 跳过 MinIO 上传，仅引用计数 ++
            SysFileObject existing = sysFileObjectMapper.selectOne(
                    new LambdaQueryWrapper<SysFileObject>()
                            .eq(SysFileObject::getFileHash, fileHash));

            String objectPath;
            String storedName;
            if (existing != null) {
                objectPath = existing.getObjectPath();
                storedName = objectPath.substring(objectPath.lastIndexOf("/") + 1);
                incrementRefCount(fileHash);
                log.info("文件指纹命中，跳过 MinIO 上传: hash={} path={}", fileHash, objectPath);
            } else {
                // 未命中 → 上传到 MinIO，新建物理对象记录
                storedName = IdUtil.simpleUUID() + (StrUtil.isNotBlank(suffix) ? "." + suffix : "");
                objectPath = buildObjectPath(suffix);
                try (InputStream is = new ByteArrayInputStream(bytes)) {
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectPath)
                            .contentType(file.getContentType())
                            .stream(is, bytes.length, -1)
                            .build());
                }
                SysFileObject newObj = new SysFileObject();
                newObj.setFileHash(fileHash);
                newObj.setObjectPath(objectPath);
                newObj.setFileSize((long) bytes.length);
                newObj.setRefCount(1);
                try {
                    sysFileObjectMapper.insert(newObj);
                } catch (org.springframework.dao.DuplicateKeyException race) {
                    // 极少数情况下两个并发上传相同哈希 → 回退为 increment，并清理刚上传的孤儿对象
                    log.warn("并发上传相同哈希，回退为引用计数: {}", fileHash);
                    removeMinioObject(objectPath);
                    SysFileObject winner = sysFileObjectMapper.selectOne(
                            new LambdaQueryWrapper<SysFileObject>()
                                    .eq(SysFileObject::getFileHash, fileHash));
                    objectPath = winner.getObjectPath();
                    storedName = objectPath.substring(objectPath.lastIndexOf("/") + 1);
                    incrementRefCount(fileHash);
                }
            }

            String fileUrl = buildFileUrl(objectPath);

            // 保存文件元数据
            SysFile sysFile = buildSysFile(originalFilename, storedName, objectPath,
                    fileUrl, (long) bytes.length, file.getContentType(), currentUserId,
                    parentId, 0, fileHash);
            sysFileMapper.insert(sysFile);

            // insert 后拿到自增 ID，拼接 fullPath：根级=自身ID，嵌套=父.fullPath + "," + 自身ID
            sysFile.setFullPath(parentFullPath.isEmpty()
                    ? String.valueOf(sysFile.getId())
                    : parentFullPath + "," + sysFile.getId());
            sysFileMapper.updateById(sysFile);

            // 上传成功后增加已使用空间（按用户逻辑占用）
            quotaService.increaseUsedSpace(currentUserId, (long) bytes.length);

            FileInfo fileInfo = new FileInfo();
            fileInfo.setId(sysFile.getId());
            fileInfo.setName(originalFilename);
            fileInfo.setUrl(fileUrl);
            return fileInfo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传文件失败", e);
            throw new BusinessException(ResultCode.UPLOAD_FILE_EXCEPTION, e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    @Override
    public boolean deleteFile(String filePath) {
        Assert.notBlank(filePath, "删除文件路径不能为空");
        try {
            Long currentUserId = SecurityUtils.getCurrentUserId();
            SysFile file = sysFileMapper.selectPersonalActiveByPath(currentUserId, normalizeObjectPath(filePath));
            if (file == null) {
                throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "个人文件不存在");
            }
            return deleteFileById(file.getId());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除文件失败", e);
            throw new BusinessException(ResultCode.DELETE_FILE_EXCEPTION, e.getMessage());
        }
    }

    /**
     * 列出文件列表
     */
    @Override
    public FileListResponseVO listFiles(Long parentId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long effectiveParentId = parentId == null ? 0L : parentId;
        validateTargetDirectory(effectiveParentId, currentUserId);

        List<SysFile> sysFiles = sysFileMapper.selectPersonalChildren(currentUserId, effectiveParentId);

        FileListResponseVO result = new FileListResponseVO();
        result.setItems(sysFileConverter.toFileInfoVOList(sysFiles));
        result.setBreadcrumb(buildBreadcrumb(effectiveParentId, currentUserId));
        return result;
    }

    /**
     * 由父目录 ID 构造面包屑：根目录 → … → 当前目录。
     * 根级（{@code parentId=0}）只含一个根项；嵌套目录通过解析父目录的 {@code fullPath}
     * ID 链 + 一次批量查名拼装，总往返 ≤ 2。
     */
    private List<BreadcrumbItemResponseVO> buildBreadcrumb(Long parentId, Long userId) {
        List<BreadcrumbItemResponseVO> crumbs = new ArrayList<>();
        crumbs.add(new BreadcrumbItemResponseVO(0L, "根目录"));
        if (parentId == null || parentId == 0L) {
            return crumbs;
        }

        SysFile parent = sysFileMapper.selectPersonalFile(userId, parentId);
        if (parent == null || parent.getFullPath() == null || parent.getFullPath().isBlank()) {
            // 父目录不存在或没有路径链：仅返回根面包屑，避免抛错影响列表渲染
            return crumbs;
        }

        List<Long> chain = StringUtils.parseIdList(parent.getFullPath());
        Map<Long, String> idToName = sysFileMapper.selectFilesInSpaceByIds(
                        SPACE_TYPE_PERSONAL, userId, chain).stream()
                .collect(Collectors.toMap(SysFile::getId, SysFile::getOriginalName, (a, b) -> a));
        for (Long id : chain) {
            crumbs.add(new BreadcrumbItemResponseVO(id, idToName.getOrDefault(id, "?")));
        }
        return crumbs;
    }

    /**
     * 列出文件树
     */
    @Override
    public List<FileTreeResponseVO> listFileTree() {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 一次性查出当前用户所有未回收的文件和目录
        List<SysFile> allFiles = sysFileMapper.selectPersonalActiveTree(currentUserId);

        // 转为 VO 并按 parentId 分组
        Map<Long, List<FileTreeResponseVO>> childrenMap = allFiles.stream()
                .map(sysFileConverter::toFileTreeResponseVO)
                .collect(Collectors.groupingBy(FileTreeResponseVO::getParentId));

        // 递归构建树，根节点 parentId = 0
        return buildFileTree(childrenMap, 0L);
    }

    /**
     * 递归构建文件树
     */
    private List<FileTreeResponseVO> buildFileTree(Map<Long, List<FileTreeResponseVO>> childrenMap, Long parentId) {
        List<FileTreeResponseVO> children = childrenMap.getOrDefault(parentId, Collections.emptyList());
        for (FileTreeResponseVO child : children) {
            if (child.getIsDirectory() == 1) {
                child.setChildren(buildFileTree(childrenMap, child.getId()));
            } else {
                child.setChildren(Collections.emptyList());
            }
        }
        return children;
    }

    @Override
    /**
     * 下载文件
     */
    public void downloadFile(Long fileId, HttpServletResponse response) {
        SysFile sysFile = requireActivePersonalFile(SecurityUtils.getCurrentUserId(), fileId);
        if (sysFile.getIsDirectory() != null && sysFile.getIsDirectory() == 1) {
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "不支持下载目录");
        }

        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(sysFile.getFilePath())
                        .build())) {
            writeFileToResponse(inputStream, sysFile.getOriginalName(),
                    sysFile.getMimeType(), sysFile.getFileSize(), response);
        } catch (Exception e) {
            log.error("下载文件失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "下载文件失败: " + e.getMessage());
        }
    }

    /**
     * 按对象路径下载个人空间文件。
     */
    @Override
    public void downloadFile(String filePath, HttpServletResponse response) {
        Assert.notBlank(filePath, "下载文件路径不能为空");
        Long currentUserId = SecurityUtils.getCurrentUserId();
        SysFile sysFile = sysFileMapper.selectPersonalActiveByPath(currentUserId, normalizeObjectPath(filePath));
        if (sysFile == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "个人文件不存在");
        }
        downloadFile(sysFile.getId(), response);
    }

    /** 删除文件到回收站。 */
    @Override
    @Transactional
    public boolean deleteFileById(Long fileId) {
        SysFile sysFile = validateFileOwnership(fileId);

        long totalFileSize;
        if (sysFile.getIsDirectory() == 1) {
            List<SysFile> activeDescendants = activePersonalDescendants(sysFile);
            totalFileSize = activeDescendants.stream()
                    .filter(f -> f.getIsDirectory() == 0)
                    .mapToLong(SysFile::getFileSize)
                    .sum();
        } else {
            totalFileSize = sysFile.getFileSize() != null ? sysFile.getFileSize() : 0;
        }

        int moved = sysFileMapper.moveRootToTrashInSpace(
                SPACE_TYPE_PERSONAL, sysFile.getUserId(), sysFile.getId(),
                SecurityUtils.getCurrentUserId(), configProperties.getTrashRetentionSeconds());
        if (moved == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "个人文件状态已变化");
        }
        if (sysFile.getIsDirectory() == 1) {
            sysFileMapper.updateDescendantsRecycleStateInSpace(
                    SPACE_TYPE_PERSONAL, sysFile.getUserId(), sysFile.getId(),
                    configProperties.getTrashRetentionSeconds());
        }
        if (totalFileSize > 0) {
            quotaService.decreaseUsedSpace(sysFile.getUserId(), totalFileSize);
        }
        return true;
    }

    private List<SysFile> activePersonalDescendants(SysFile sysFile) {
        return sysFileMapper.selectDescendantsInSpace(
                        SPACE_TYPE_PERSONAL, sysFile.getUserId(), sysFile.getId()).stream()
                .filter(file -> !Integer.valueOf(1).equals(file.getInRecycleBin()))
                .toList();
    }

    private List<SysFile> personalTrashTreeDescendants(SysFile sysFile) {
        return sysFileMapper.selectDescendantsInSpace(
                SPACE_TYPE_PERSONAL, sysFile.getUserId(), sysFile.getId());
    }

    @Override
    @Transactional
    /**
     * 复制文件
     */
    public FileInfoResponseVO copyFile(Long fileId, Long targetDirectoryId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long resolvedTargetDirectoryId = targetDirectoryId == null ? ROOT_ID : targetDirectoryId;

        SysFile sourceFile = requireActivePersonalFile(currentUserId, fileId);
        if (sourceFile == null || sourceFile.getIsDirectory() == 1) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "文件不存在");
        }

        quotaService.checkQuota(currentUserId, sourceFile.getFileSize());

        String targetFullPath = validateTargetDirectory(resolvedTargetDirectoryId, currentUserId);

        String originalName = generateUniqueFileName(
                sourceFile.getOriginalName(), currentUserId, resolvedTargetDirectoryId);

        if (sourceFile.getFileHash() != null) {
            incrementRefCount(sourceFile.getFileHash());
        }

        SysFile newFile = buildSysFile(originalName, sourceFile.getStoredName(),
                sourceFile.getFilePath(), sourceFile.getFileUrl(), sourceFile.getFileSize(),
                sourceFile.getMimeType(), currentUserId, resolvedTargetDirectoryId, 0,
                sourceFile.getFileHash());
        sysFileMapper.insert(newFile);

        newFile.setFullPath(targetFullPath.isEmpty()
                ? String.valueOf(newFile.getId())
                : targetFullPath + "," + newFile.getId());
        sysFileMapper.updateById(newFile);

        quotaService.increaseUsedSpace(currentUserId, sourceFile.getFileSize());

        return sysFileConverter.toFileInfoVO(newFile);
    }

    @Override
    @Transactional
    /**
     * 移动文件
     */
    public FileInfoResponseVO moveFile(Long fileId, Long targetDirectoryId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long resolvedTargetDirectoryId = targetDirectoryId == null ? ROOT_ID : targetDirectoryId;

        SysFile sourceFile = validateFileOwnership(fileId);

        if (sourceFile.getParentId().equals(resolvedTargetDirectoryId)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "文件已在该目录下");
        }

        String targetFullPath = validateTargetDirectory(resolvedTargetDirectoryId, currentUserId);

        if (sourceFile.getIsDirectory() == 1 && isDescendant(currentUserId, fileId, resolvedTargetDirectoryId)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "不能将目录移动到其子目录下");
        }

        if (sysFileMapper.existsNameInSpaceDirectory(
                SPACE_TYPE_PERSONAL, currentUserId, resolvedTargetDirectoryId, sourceFile.getOriginalName())) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "目标目录下已存在同名文件或目录");
        }

        String newFullPath = targetFullPath.isEmpty()
                ? String.valueOf(fileId)
                : targetFullPath + "," + fileId;

        sourceFile.setParentId(resolvedTargetDirectoryId);
        sourceFile.setFullPath(newFullPath);
        sysFileMapper.updateById(sourceFile);

        if (sourceFile.getIsDirectory() == 1) {
            sysFileMapper.updateDescendantsFullPathInSpace(
                    SPACE_TYPE_PERSONAL, currentUserId, fileId, newFullPath);
        }

        return sysFileConverter.toFileInfoVO(sourceFile);
    }

    /**
     * 生成文件的预签名下载 URL（5 分钟有效）。
     * 调用方负责权限校验；此处仅生成纯净 MinIO URL，避免附加响应头参数导致签名不一致。
     */
    @Override
    public String getPresignedDownloadUrl(Long fileId) {
        SysFile sysFile = sysFileMapper.selectById(fileId);
        if (sysFile == null || sysFile.getIsDeleted() != null && sysFile.getIsDeleted() == 1
                || sysFile.getInRecycleBin() != null && sysFile.getInRecycleBin() == 1) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "文件不存在");
        }
        if (sysFile.getIsDirectory() != null && sysFile.getIsDirectory() == 1) {
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "不支持下载目录");
        }
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(sysFile.getFilePath())
                            .method(Method.GET)
                            .expiry(5, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("生成预签名URL失败", e);
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "生成下载链接失败: " + e.getMessage());
        }
    }

    /**
     * 按关键词搜索当前登录用户的文件（跨目录；仅文件，不含目录）。
     * <p>
     * 安全：仅返回 {@code userId = currentUserId} 的记录，避免越权访问他人文件。
     */
    @Override
    public List<FileInfoResponseVO> searchFiles(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<SysFile> files = sysFileMapper.searchPersonalFiles(currentUserId, keyword.trim());
        return sysFileConverter.toFileInfoVOList(files);
    }

    // ========================= Bonus 4.3 回收站 =========================

    @Override
    public List<RecycleBinItemResponseVO> listRecycleBin() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<SysFile> files = sysFileMapper.selectPersonalTrashRoots(currentUserId);
        if (files.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> ancestorIds = new HashSet<>();
        for (SysFile f : files) {
            List<Long> chain = StringUtils.parseIdList(f.getFullPath());
            if (chain.size() > 1) {
                ancestorIds.addAll(chain.subList(0, chain.size() - 1));
            }
        }
        Map<Long, String> idToName = ancestorIds.isEmpty()
                ? Collections.emptyMap()
                : sysFileMapper.selectFilesInSpaceByIds(
                                SPACE_TYPE_PERSONAL, currentUserId, ancestorIds).stream()
                        .collect(Collectors.toMap(SysFile::getId, SysFile::getOriginalName, (a, b) -> a));

        Set<Long> deleterIds = files.stream()
                .map(SysFile::getDeletedBy)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, UserBriefBO> deleterMap = deleterIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectUserBriefByIds(new ArrayList<>(deleterIds)).stream()
                        .collect(Collectors.toMap(UserBriefBO::getUserId, u -> u, (a, b) -> a));

        return files.stream().map(f -> toRecycleBinItem(f, idToName, deleterMap)).toList();
    }

    private RecycleBinItemResponseVO toRecycleBinItem(SysFile f, Map<Long, String> idToName,
                                                       Map<Long, UserBriefBO> deleterMap) {
        List<Long> chain = StringUtils.parseIdList(f.getFullPath());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chain.size() - 1; i++) {
            sb.append('/').append(idToName.getOrDefault(chain.get(i), "?"));
        }
        sb.append('/').append(f.getOriginalName());
        RecycleBinItemResponseVO vo = sysFileConverter.toRecycleBinItemVO(f, sb.toString());
        if (f.getDeletedBy() != null) {
            vo.setDeletedByUserId(f.getDeletedBy());
            UserBriefBO deleter = deleterMap.get(f.getDeletedBy());
            if (deleter != null) {
                vo.setDeletedByAccountId(deleter.getAccountId());
                vo.setDeletedByAccountName(deleter.getAccountName());
                vo.setDeletedByName(deleter.getNickname());
            } else {
                vo.setDeletedByName("未知");
            }
        }
        vo.setExpireAt(f.getExpireAt());
        return vo;
    }

    @Override
    @Transactional
    /** 从回收站恢复文件。 */
    public void restoreFromRecycleBin(Long fileId) {
        SysFile sysFile = loadRecycledFileOwnedByCurrentUser(fileId);
        validateRecycleBinUnexpired(sysFile);

        long totalFileSize = 0;
        if (sysFile.getIsDirectory() == 1) {
            List<SysFile> descendants = personalTrashTreeDescendants(sysFile);
            totalFileSize = descendants.stream()
                    .filter(f -> f.getIsDirectory() == 0)
                    .mapToLong(SysFile::getFileSize)
                    .sum();
        } else {
            totalFileSize = (sysFile.getFileSize() != null) ? sysFile.getFileSize() : 0;
        }

        if (totalFileSize > 0) {
            quotaService.checkQuota(sysFile.getUserId(), totalFileSize);
        }

        PersonalRestoreTarget target = resolvePersonalRestoreTarget(sysFile);
        int restored = sysFileMapper.restoreTrashTreeInSpace(
                SPACE_TYPE_PERSONAL,
                sysFile.getUserId(),
                sysFile.getId(),
                target.parentId(),
                target.parentFullPath(),
                sysFile.getOriginalName());
        if (restored == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "回收站文件状态已变化");
        }

        if (totalFileSize > 0) {
            quotaService.increaseUsedSpace(sysFile.getUserId(), totalFileSize);
        }
    }

    /** 永久删除回收站中的文件/目录。 */
    @Override
    @Transactional
    public void permanentlyDelete(Long fileId) {
        SysFile sysFile = loadRecycledFileOwnedByCurrentUser(fileId);
        List<SysFile> descendants = sysFile.getIsDirectory() == 1
                ? personalTrashTreeDescendants(sysFile)
                : List.of();

        int deleted = sysFileMapper.permanentlyDeleteTrashTreeInSpace(
                SPACE_TYPE_PERSONAL, sysFile.getUserId(), sysFile.getId());
        if (deleted == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "回收站文件状态已变化");
        }
        if (sysFile.getIsDirectory() == 0) {
            decrementRefOrRemove(sysFile);
        }
        for (SysFile file : descendants) {
            if (file.getIsDirectory() == 0) {
                decrementRefOrRemove(file);
            }
        }
    }

    /** 回收站专用校验：文件存在、属于当前用户、在回收站中、且为回收站根节点。 */
    private SysFile loadRecycledFileOwnedByCurrentUser(Long fileId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        SysFile sysFile = sysFileMapper.selectPersonalFile(currentUserId, fileId);
        if (sysFile == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "文件或目录不存在");
        }
        if (!isPersonalSpace(sysFile, currentUserId)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED, "只能操作自己的文件或目录");
        }
        if (sysFile.getInRecycleBin() == null || sysFile.getInRecycleBin() != 1) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "仅回收站中的文件可执行此操作");
        }
        if (sysFile.getRecycleRoot() == null || sysFile.getRecycleRoot() != 1) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR,
                    "该项是随父目录一同删除的，请对父目录执行恢复/永久删除");
        }
        return sysFile;
    }

    /** 校验个人回收站项尚未过期。 */
    private void validateRecycleBinUnexpired(SysFile sysFile) {
        LocalDateTime expireAt = sysFile.getExpireAt();
        if (expireAt == null || !expireAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "回收站文件已过期");
        }
    }

    /** 解析个人回收站恢复位置；原父目录不可用时恢复到根目录。 */
    private PersonalRestoreTarget resolvePersonalRestoreTarget(SysFile sysFile) {
        Long parentId = sysFile.getParentId() == null ? ROOT_ID : sysFile.getParentId();
        if (ROOT_ID == parentId) {
            return new PersonalRestoreTarget(ROOT_ID, "");
        }
        SysFile parent = sysFileMapper.selectPersonalFile(sysFile.getUserId(), parentId);
        if (parent == null
                || parent.getIsDirectory() == null
                || parent.getIsDirectory() != 1
                || parent.getInRecycleBin() != null && parent.getInRecycleBin() == 1) {
            return new PersonalRestoreTarget(ROOT_ID, "");
        }
        return new PersonalRestoreTarget(parent.getId(), parent.getFullPath());
    }

    // ========================= 通用校验方法 =========================

    /**
     * 校验文件/目录存在且属于当前用户，且不在回收站（Bonus 4.3：回收站文件不允许被正常流程操作）。
     */
    private SysFile validateFileOwnership(Long fileId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        SysFile sysFile = sysFileMapper.selectPersonalFile(currentUserId, fileId);
        if (sysFile == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "文件或目录不存在");
        }
        if (!isPersonalSpace(sysFile, currentUserId)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED, "只能操作自己的文件或目录");
        }
        if (sysFile.getInRecycleBin() != null && sysFile.getInRecycleBin() == 1) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "文件/目录已在回收站，请先恢复或前往回收站操作");
        }
        return sysFile;
    }

    /**
     * 校验目标目录存在且属于当前用户，且不在回收站。
     */
    private String validateTargetDirectory(Long targetDirectoryId, Long userId) {
        if (targetDirectoryId == null || targetDirectoryId == ROOT_ID) {
            return "";
        }
        SysFile targetDir = sysFileMapper.selectPersonalFile(userId, targetDirectoryId);
        if (targetDir == null || targetDir.getIsDirectory() != 1) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "目标目录不存在");
        }
        if (!isPersonalSpace(targetDir, userId)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED, "无权操作该目标目录");
        }
        if (targetDir.getInRecycleBin() != null && targetDir.getInRecycleBin() == 1) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "目标目录在回收站中");
        }
        return targetDir.getFullPath();
    }

    // ========================= MinIO 操作辅助方法 =========================

    /**
     * 构建 MinIO 对象路径：日期文件夹/UUID.后缀
     */
    private String buildObjectPath(String suffix) {
        String dateFolder = DateUtil.format(LocalDateTime.now(), "yyyyMMdd");
        return dateFolder + "/" + IdUtil.simpleUUID() + "." + suffix;
    }

    /**
     * 构建文件访问 URL
     */
    private String buildFileUrl(String objectPath) {
        String base = StrUtil.isBlank(customDomain) ? endpoint : customDomain;
        return base + "/" + bucketName + "/" + objectPath;
    }

    /**
     * 从完整 URL 中提取 MinIO 对象名
     */
    private String extractObjectName(String filePath) {
        String base = StrUtil.isNotBlank(customDomain) ? customDomain : endpoint;
        return filePath.substring(base.length() + 1 + bucketName.length() + 1);
    }

    private String normalizeObjectPath(String filePath) {
        String normalized = filePath;
        String base = StrUtil.isNotBlank(customDomain) ? customDomain : endpoint;
        String urlPrefix = base + "/" + bucketName + "/";
        if (normalized.startsWith(urlPrefix)) {
            normalized = normalized.substring(urlPrefix.length());
        }
        String bucketPrefix = bucketName + "/";
        if (normalized.startsWith(bucketPrefix)) {
            normalized = normalized.substring(bucketPrefix.length());
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    /**
     * MinIO 对象复制
     */
    private void copyMinioObject(String sourceObjectPath, String targetObjectPath) {
        try {
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(bucketName)
                    .object(targetObjectPath)
                    .source(CopySource.builder()
                            .bucket(bucketName)
                            .object(sourceObjectPath)
                            .build())
                    .build());
        } catch (Exception e) {
            log.error("MinIO复制对象失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "复制文件失败: " + e.getMessage());
        }
    }

    /**
     * 删除 MinIO 对象
     */
    private void removeMinioObject(String objectPath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build());
        } catch (Exception e) {
            log.error("删除MinIO对象失败: {}", objectPath, e);
            throw new BusinessException(ResultCode.DELETE_FILE_EXCEPTION, "删除MinIO文件失败: " + e.getMessage());
        }
    }

    /**
     * 流式写入文件到 HTTP 响应
     */
    private void writeFileToResponse(InputStream inputStream, String fileName,
                                     String mimeType, Long fileSize,
                                     HttpServletResponse response) throws Exception {
        response.setContentType(mimeType != null ? mimeType : "application/octet-stream");
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
        response.setContentLengthLong(fileSize);

        downloadThrottleSupport.throttledCopy(
                inputStream, response.getOutputStream(),
                fileSize, SecurityUtils.getCurrentUserId(), quotaService);
    }

    /**
     * 引用计数 + 1（原子 UPDATE）。
     */
    private void incrementRefCount(String fileHash) {
        sysFileObjectMapper.update(null,
                new LambdaUpdateWrapper<SysFileObject>()
                        .eq(SysFileObject::getFileHash, fileHash)
                        .setSql("ref_count = ref_count + 1"));
    }

    /**
     * 引用计数 - 1；归零时真正删除 MinIO 对象与 sys_file_object 记录。
     * <p>
     * 兼容性：若 sysFile 没有 fileHash（Lab1 遗留数据 / 历史文件），回退为直接按 filePath 删 MinIO。
     */
    private void decrementRefOrRemove(SysFile sysFile) {
        String fileHash = sysFile.getFileHash();
        if (StrUtil.isBlank(fileHash)) {
            if (StrUtil.isNotBlank(sysFile.getFilePath())) {
                removeMinioObject(sysFile.getFilePath());
            }
            return;
        }
        SysFileObject obj = sysFileObjectMapper.selectOne(
                new LambdaQueryWrapper<SysFileObject>().eq(SysFileObject::getFileHash, fileHash));
        if (obj == null) {
            log.warn("未找到 sys_file_object，回退为按 filePath 删除: hash={}, path={}",
                    fileHash, sysFile.getFilePath());
            if (StrUtil.isNotBlank(sysFile.getFilePath())) {
                removeMinioObject(sysFile.getFilePath());
            }
            return;
        }
        int newCount = obj.getRefCount() - 1;
        if (newCount > 0) {
            sysFileObjectMapper.update(null,
                    new LambdaUpdateWrapper<SysFileObject>()
                            .eq(SysFileObject::getFileHash, fileHash)
                            .setSql("ref_count = ref_count - 1"));
        } else {
            removeMinioObject(obj.getObjectPath());
            sysFileObjectMapper.deleteById(obj.getId());
        }
    }

    // ========================= 文件名/路径辅助方法 =========================

    /**
     * 校验同一父目录下不允许同名文件或目录
     */
    private void validateNameUnique(Long userId, Long parentId, String name) {
        if (sysFileMapper.existsNameInSpaceDirectory(SPACE_TYPE_PERSONAL, userId, parentId, name)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "同一目录下已存在同名文件或目录");
        }
    }

    /**
     * 生成唯一文件名（同名自动追加序号）。仅考虑同父目录下、未入回收站的项。
     * <p>一次查询获取目录下所有匹配前缀的文件名，在内存中确定下一个可用序号，
     * 避免循环 exists() 导致的多次 DB 往返。
     */
    private String generateUniqueFileName(String originalName, Long userId, Long parentId) {
        if (!sysFileMapper.existsNameInSpaceDirectory(SPACE_TYPE_PERSONAL, userId, parentId, originalName)) {
            return originalName;
        }

        String nameWithoutExt = FileUtil.mainName(originalName);
        String ext = FileUtil.getSuffix(originalName);
        String suffix = StrUtil.isNotBlank(ext) ? "." + ext : "";

        List<String> existingNames = sysFileMapper.selectNamesInSpaceDirectory(
                SPACE_TYPE_PERSONAL, userId, parentId, nameWithoutExt);

        int maxIndex = 0;
        String prefix = nameWithoutExt + "(";
        for (String name : existingNames) {
            if (name.startsWith(prefix) && name.endsWith(suffix)) {
                String numPart = name.substring(prefix.length(), name.length() - suffix.length());
                try {
                    maxIndex = Math.max(maxIndex, Integer.parseInt(numPart));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return nameWithoutExt + "(" + (maxIndex + 1) + ")" + suffix;
    }

    /**
     * 判断 targetId 是否是 directoryId 的子目录
     */
    private boolean isDescendant(Long userId, Long directoryId, Long targetId) {
        Long currentId = targetId;
        while (currentId != null && currentId != 0) {
            if (currentId.equals(directoryId)) {
                return true;
            }
            SysFile parent = sysFileMapper.selectPersonalFile(userId, currentId);
            if (parent == null) {
                break;
            }
            currentId = parent.getParentId();
        }
        return false;
    }

    private SysFile requireActivePersonalFile(Long userId, Long fileId) {
        SysFile sysFile = sysFileMapper.selectPersonalFile(userId, fileId);
        if (sysFile == null) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "文件不存在");
        }
        if (!isPersonalSpace(sysFile, userId)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED, "只能操作个人空间文件或目录");
        }
        if (sysFile.getInRecycleBin() != null && sysFile.getInRecycleBin() == 1) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "文件已在回收站");
        }
        return sysFile;
    }

    private boolean isPersonalSpace(SysFile file, Long userId) {
        return userId.equals(file.getUserId())
                && SPACE_TYPE_PERSONAL.equals(file.getSpaceType())
                && userId.equals(file.getSpaceId());
    }

    @SneakyThrows
    private void createBucketIfAbsent(String bucketName) {
        BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(bucketName).build();
        if (!minioClient.bucketExists(bucketExistsArgs)) {
            MakeBucketArgs makeBucketArgs = MakeBucketArgs.builder().bucket(bucketName).build();
            minioClient.makeBucket(makeBucketArgs);

            SetBucketPolicyArgs setBucketPolicyArgs = SetBucketPolicyArgs
                    .builder()
                    .bucket(bucketName)
                    .config(publicBucketPolicy(bucketName))
                    .build();
            minioClient.setBucketPolicy(setBucketPolicyArgs);
        }
    }

    private static String publicBucketPolicy(String bucketName) {
        return "{\"Version\":\"2012-10-17\","
                + "\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:ListBucketMultipartUploads\",\"s3:GetBucketLocation\",\"s3:ListBucket\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucketName + "\"]},"
                + "{\"Effect\":\"Allow\"," + "\"Principal\":{\"AWS\":[\"*\"]},"
                + "\"Action\":[\"s3:ListMultipartUploadParts\",\"s3:PutObject\",\"s3:AbortMultipartUpload\",\"s3:DeleteObject\",\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucketName + "/*\"]}]}";
    }

    private record PersonalRestoreTarget(Long parentId, String parentFullPath) {
    }

}
