package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 团队文件查找与目录校验服务。
 *
 * @author charleslam
 * @since 2026/05/20
 */
@Component
@RequiredArgsConstructor
class TeamFileLookupService {

    private static final long ROOT_ID = 0L;

    private final SysFileMapper sysFileMapper;

    Long resolveParentId(Long parentId) {
        return parentId == null ? ROOT_ID : parentId;
    }

    String validateTeamTargetDirectory(Long teamId, Long directoryId) {
        if (ROOT_ID == directoryId) {
            return "";
        }
        SysFile directory = requireActiveTeamFile(teamId, directoryId);
        if (!Integer.valueOf(1).equals(directory.getIsDirectory())) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队目录不存在");
        }
        return directory.getFullPath();
    }

    SysFile requireActiveTeamFile(Long teamId, Long fileId) {
        SysFile file = sysFileMapper.selectTeamFile(teamId, fileId);
        if (file == null || Integer.valueOf(1).equals(file.getInRecycleBin())) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "团队文件不存在");
        }
        return file;
    }
}
