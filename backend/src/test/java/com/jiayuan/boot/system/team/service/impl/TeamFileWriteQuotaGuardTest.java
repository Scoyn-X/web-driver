package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.mapper.TeamSpaceMapper;
import com.jiayuan.boot.system.team.service.TeamQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 团队文件写入配额守卫测试。
 *
 * @author charleslam
 * @since 2026/05/24
 */
@DisplayName("TeamFileWriteService 配额守卫测试")
class TeamFileWriteQuotaGuardTest {

    private static final Long TEAM_ID = 9L;
    private static final Long USER_ID = 7L;

    private SysFileMapper sysFileMapper;
    private SysFileConverter sysFileConverter;
    private FileObjectService fileObjectService;
    private TeamQuotaService teamQuotaService;
    private QuotaService quotaService;
    private TeamSpaceMapper teamSpaceMapper;

    private TeamFileWriteService writeService;

    @BeforeEach
    void setUp() {
        sysFileMapper = mock(SysFileMapper.class);
        sysFileConverter = mock(SysFileConverter.class);
        fileObjectService = mock(FileObjectService.class);
        teamQuotaService = mock(TeamQuotaService.class);
        quotaService = mock(QuotaService.class);
        teamSpaceMapper = mock(TeamSpaceMapper.class);
        TeamFileLookupService lookupService = new TeamFileLookupService(sysFileMapper);
        lenient().when(teamSpaceMapper.lockActiveTeamSpace(TEAM_ID)).thenReturn(TEAM_ID);
        SystemConfigProperties configProperties = new SystemConfigProperties();
        writeService = new TeamFileWriteService(
                sysFileMapper, sysFileConverter, fileObjectService,
                teamQuotaService, quotaService, teamSpaceMapper, configProperties, lookupService);
    }

    @Test
    @DisplayName("上传团队文件：团队超额后新增容量被配额校验拦截")
    void uploadFile_quotaExceededStopsBeforeObjectSave() {
        MultipartFile multipartFile = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", new byte[] {1, 2, 3});
        BusinessException failure = quotaExceeded();
        doThrow(failure).when(teamQuotaService).checkQuota(TEAM_ID, 3L);

        assertThatThrownBy(() -> withCurrentUser(() -> writeService.uploadFile(TEAM_ID, multipartFile, null)))
                .isSameAs(failure);

        verify(teamQuotaService).checkQuota(TEAM_ID, 3L);
        verify(teamQuotaService, never()).increaseUsedSpace(any(), anyLong());
        verify(fileObjectService, never()).saveOrReuse(any());
        verify(sysFileMapper, never()).insert(any());
    }

    @Test
    @DisplayName("复制团队文件：团队超额后新增容量被配额校验拦截")
    void copyFile_quotaExceededStopsBeforeClone() {
        SysFile source = teamFile(10L, "report.pdf", 8L);
        source.setFileHash("hash-report");
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(source);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 2L))
                .thenReturn(TeamFileTestFixtures.teamDirectory(TEAM_ID, USER_ID, 2L, 0L, "2"));
        BusinessException failure = quotaExceeded();
        doThrow(failure).when(teamQuotaService).checkQuota(TEAM_ID, 8L);

        assertThatThrownBy(() -> withCurrentUser(() -> writeService.copyFile(TEAM_ID, 10L, 2L)))
                .isSameAs(failure);

        verify(teamQuotaService).checkQuota(TEAM_ID, 8L);
        verify(sysFileConverter, never()).toClonedFile(any());
        verify(fileObjectService, never()).increaseReference(any());
        verify(teamQuotaService, never()).increaseUsedSpace(any(), anyLong());
    }

    @Test
    @DisplayName("恢复团队回收站文件：团队超额后新增容量被配额校验拦截")
    void restoreFromTrash_quotaExceededStopsBeforeRestore() {
        SysFile root = teamFile(10L, "report.pdf", 8L);
        root.setInRecycleBin(1);
        root.setRecycleRoot(1);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 10L)).thenReturn(root);
        BusinessException failure = quotaExceeded();
        doThrow(failure).when(teamQuotaService).checkQuota(TEAM_ID, 8L);

        assertThatThrownBy(() -> writeService.restoreFromTrash(TEAM_ID, 10L, null))
                .isSameAs(failure);

        verify(teamQuotaService).checkQuota(TEAM_ID, 8L);
        verify(sysFileMapper, never()).restoreTrashTreeInSpace(any(), any(), any(), any(), any(), any());
        verify(teamQuotaService, never()).increaseUsedSpace(any(), anyLong());
    }

    private SysFile teamFile(Long id, String originalName, Long fileSize) {
        SysFile file = TeamFileTestFixtures.teamFile(TEAM_ID, USER_ID, id, 0);
        file.setOriginalName(originalName);
        file.setFileSize(fileSize);
        return file;
    }

    private BusinessException quotaExceeded() {
        return new BusinessException(ResultCode.USER_QUOTA_EXHAUSTED, "团队配额不足");
    }

    private <T> T withCurrentUser(java.util.function.Supplier<T> action) {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            return action.get();
        }
    }
}
