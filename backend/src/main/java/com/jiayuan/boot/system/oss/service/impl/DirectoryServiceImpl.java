package com.jiayuan.boot.system.oss.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryTreeResponseVO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileMoveRequestVO;
import com.jiayuan.boot.system.oss.service.DirectoryService;
import com.jiayuan.boot.system.oss.service.FileService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 目录管理服务实现类
 *
 * @author didongchen
 * @since 2026/04/11
 */
@Service
@RequiredArgsConstructor
public class DirectoryServiceImpl implements DirectoryService {

    private static final String SPACE_TYPE_PERSONAL = "PERSONAL";
    private static final long ROOT_ID = 0L;

    private final SysFileMapper sysFileMapper;
    private final SysFileConverter sysFileConverter;
    private final FileService fileService;

    /**
     * 新建目录
     */
    @Override
    public FileInfoResponseVO createDirectory(DirectoryCreateRequestVO request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long parentId = request.getParentId() == null ? ROOT_ID : request.getParentId();

        // 校验父目录
        String parentFullPath = validateParentDirectory(parentId, currentUserId);

        // 校验同名
        validateNameUnique(currentUserId, parentId, request.getName());

        // 构建目录实体
        SysFile directory = sysFileConverter.toSysFile(request);
        directory.setUserId(currentUserId);
        directory.setSpaceType(SPACE_TYPE_PERSONAL);
        directory.setSpaceId(currentUserId);
        directory.setUploaderId(currentUserId);
        directory.setParentId(parentId);
        sysFileMapper.insert(directory);

        // insert 后才能拿到自增 ID，拼接 fullPath
        directory.setFullPath(parentFullPath.isEmpty()
                ? String.valueOf(directory.getId())
                : parentFullPath + "," + directory.getId());
        sysFileMapper.updateById(directory);

        return sysFileConverter.toFileInfoVO(directory);
    }

    /**
     * 重命名目录
     */
    @Override
    public FileInfoResponseVO renameDirectory(Long directoryId, DirectoryRenameRequestVO request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        SysFile directory = requirePersonalDirectory(currentUserId, directoryId);

        // 校验同名
        if (!Objects.equals(request.getName(), directory.getOriginalName())) {
            validateNameUnique(currentUserId, directory.getParentId(), request.getName());
        }

        // 更新目录名称（fullPath 是 ID 路径，重命名无需变更）
        directory.setOriginalName(request.getName());
        sysFileMapper.updateById(directory);

        return sysFileConverter.toFileInfoVO(directory);
    }

    /**
     * 移动目录
     */
    @Override
    public void moveDirectory(Long directoryId, FileMoveRequestVO request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        requirePersonalDirectory(currentUserId, directoryId);
        fileService.moveFile(directoryId, request.getTargetDirectoryId());
    }

    /**
     * 列出子目录
     */
    @Override
    public List<DirectoryNodeResponseVO> listChildDirectories(Long parentId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Long effectiveParentId = parentId == null ? 0L : parentId;

        List<SysFile> children = sysFileMapper.selectPersonalDirectories(currentUserId, effectiveParentId);
        if (children.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> childIds = children.stream().map(SysFile::getId).toList();
        Set<Long> idsHavingChildren = new HashSet<>(
                sysFileMapper.selectParentIdsHavingChildDirectory(currentUserId, childIds));

        return children.stream()
                .map(c -> sysFileConverter.toDirectoryNodeVO(c, idsHavingChildren.contains(c.getId())))
                .toList();
    }

    /**
     * 删除目录
     */
    @Override
    public boolean deleteDirectory(Long directoryId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        SysFile directory = requirePersonalDirectory(currentUserId, directoryId);

        // 委托 FileService 完成递归删除（含存储层文件清理）
        return fileService.deleteFileById(directoryId);
    }

    /**
     * 校验父目录是否存在且属于当前用户，返回父目录的 fullPath
     */
    private String validateParentDirectory(Long parentId, Long userId) {
        if (parentId == null || parentId == ROOT_ID) {
            return "";
        }
        SysFile parentDir = sysFileMapper.selectPersonalFile(userId, parentId);
        if (parentDir == null || parentDir.getIsDirectory() != 1) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "父目录不存在");
        }
        if (!isPersonalSpace(parentDir, userId)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED, "无权在该目录下创建子目录");
        }
        return parentDir.getFullPath();
    }

    /**
     * 校验同一父目录下不允许同名（排除回收站项，不同目录下允许重名）。
     */
    private void validateNameUnique(Long userId, Long parentId, String name) {
        if (sysFileMapper.existsNameInSpaceDirectory(SPACE_TYPE_PERSONAL, userId, parentId, name)) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "同一目录下已存在同名文件或目录");
        }
    }

    /**
     * 校验个人目录存在且属于当前用户。
     */
    private SysFile requirePersonalDirectory(Long userId, Long directoryId) {
        SysFile directory = sysFileMapper.selectPersonalFile(userId, directoryId);
        if (directory == null || directory.getIsDirectory() != 1) {
            throw new BusinessException(ResultCode.USER_REQUEST_PARAMETER_ERROR, "目录不存在");
        }
        if (!isPersonalSpace(directory, userId)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED, "无权操作该目录");
        }
        return directory;
    }

    /**
     * 列出目录树
     */
    @Override
    public List<DirectoryTreeResponseVO> listDirectoryTree() {
        Long currentUserId = SecurityUtils.getCurrentUserId();

        // 一次性查出当前用户所有未回收的目录
        List<SysFile> allDirs = sysFileMapper.selectPersonalDirectoryTree(currentUserId);

        // 按 parentId 分组
        Map<Long, List<DirectoryTreeResponseVO>> childrenMap = allDirs.stream()
                .map(sysFileConverter::toDirectoryTreeResponseVO)
                .collect(Collectors.groupingBy(DirectoryTreeResponseVO::getParentId));

        // 递归构建树，根节点 parentId = 0
        return buildTree(childrenMap, 0L);
    }

    /**
     * 递归构建目录树
     */
    private List<DirectoryTreeResponseVO> buildTree(Map<Long, List<DirectoryTreeResponseVO>> childrenMap, Long parentId) {
        List<DirectoryTreeResponseVO> children = childrenMap.getOrDefault(parentId, Collections.emptyList());
        for (DirectoryTreeResponseVO child : children) {
            child.setChildren(buildTree(childrenMap, child.getId()));
        }
        return children;
    }

    private boolean isPersonalSpace(SysFile file, Long userId) {
        return userId.equals(file.getUserId())
                && SPACE_TYPE_PERSONAL.equals(file.getSpaceType())
                && userId.equals(file.getSpaceId());
    }

}
