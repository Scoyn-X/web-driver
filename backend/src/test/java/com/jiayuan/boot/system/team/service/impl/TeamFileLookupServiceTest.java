package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 团队文件查找辅助服务单元测试。
 *
 * @author charleslam
 * @since 2026/06/06
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamFileLookupService 单元测试")
class TeamFileLookupServiceTest {

    @Mock
    private SysFileMapper sysFileMapper;

    @Test
    @DisplayName("父目录解析：null 归一到根目录")
    void resolveParentId_nullUsesRoot() {
        TeamFileLookupService service = new TeamFileLookupService(sysFileMapper);

        assertThat(service.resolveParentId(null)).isZero();
        assertThat(service.resolveParentId(8L)).isEqualTo(8L);
    }

    @Test
    @DisplayName("目标目录校验：根目录直接返回空 fullPath")
    void validateTeamTargetDirectory_rootReturnsBlankFullPath() {
        TeamFileLookupService service = new TeamFileLookupService(sysFileMapper);

        assertThat(service.validateTeamTargetDirectory(1L, 0L)).isEmpty();
        verify(sysFileMapper, never()).selectTeamFile(1L, 0L);
    }

    @Test
    @DisplayName("目标目录校验：有效团队目录返回 fullPath")
    void validateTeamTargetDirectory_validDirectoryReturnsFullPath() {
        TeamFileLookupService service = new TeamFileLookupService(sysFileMapper);
        SysFile directory = teamFile(7L, 1, 0, "3,7");
        when(sysFileMapper.selectTeamFile(1L, 7L)).thenReturn(directory);

        assertThat(service.validateTeamTargetDirectory(1L, 7L)).isEqualTo("3,7");
    }

    @Test
    @DisplayName("目标目录校验：文件不是目录时抛资源不存在")
    void validateTeamTargetDirectory_fileThrowsNotFound() {
        TeamFileLookupService service = new TeamFileLookupService(sysFileMapper);
        when(sysFileMapper.selectTeamFile(1L, 7L)).thenReturn(teamFile(7L, 0, 0, "7"));

        assertThatThrownBy(() -> service.validateTeamTargetDirectory(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("团队文件查找：回收站文件按不存在处理")
    void requireActiveTeamFile_recycledThrowsNotFound() {
        TeamFileLookupService service = new TeamFileLookupService(sysFileMapper);
        when(sysFileMapper.selectTeamFile(1L, 7L)).thenReturn(teamFile(7L, 0, 1, "7"));

        assertThatThrownBy(() -> service.requireActiveTeamFile(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    private SysFile teamFile(Long id, Integer isDirectory, Integer inRecycleBin, String fullPath) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setIsDirectory(isDirectory);
        file.setInRecycleBin(inRecycleBin);
        file.setFullPath(fullPath);
        return file;
    }
}
