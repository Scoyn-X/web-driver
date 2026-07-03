package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.mapper.TeamSpaceMapper;
import com.jiayuan.boot.system.team.service.TeamQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;

/**
 * TeamFileWriteService 单元测试共用 fixture 与 mock 初始化。
 *
 * @author charleslam
 * @since 2026/06/06
 */
abstract class TeamFileWriteServiceTestSupport {

    protected static final Long TEAM_ID = 9L;
    protected static final Long USER_ID = 7L;

    @Mock protected SysFileMapper sysFileMapper;
    @Mock protected SysFileConverter sysFileConverter;
    @Mock protected FileObjectService fileObjectService;
    @Mock protected TeamQuotaService teamQuotaService;
    @Mock protected QuotaService quotaService;
    @Mock protected TeamSpaceMapper teamSpaceMapper;

    protected TeamFileWriteService writeService;

    @BeforeEach
    void setUpTeamFileWriteService() {
        TeamFileLookupService lookupService = new TeamFileLookupService(sysFileMapper);
        lenient().when(teamSpaceMapper.lockActiveTeamSpace(TEAM_ID)).thenReturn(TEAM_ID);
        SystemConfigProperties configProperties = new SystemConfigProperties();
        writeService = new TeamFileWriteService(
                sysFileMapper, sysFileConverter, fileObjectService,
                teamQuotaService, quotaService, teamSpaceMapper, configProperties, lookupService);
    }

    protected SysFile recycledTeamFile(Long id, String originalName, Long fileSize) {
        SysFile file = teamFile(id, 0);
        file.setOriginalName(originalName);
        file.setFileSize(fileSize);
        file.setParentId(0L);
        file.setFullPath(String.valueOf(id));
        file.setInRecycleBin(1);
        file.setRecycleRoot(1);
        file.setExpireAt(LocalDateTime.now().plusDays(3));
        return file;
    }

    protected SysFile activeTeamFile(Long id, String originalName, Long fileSize) {
        SysFile file = teamFile(id, 0);
        file.setOriginalName(originalName);
        file.setFileSize(fileSize);
        return file;
    }

    protected SysFile personalDirectory(Long id, Long parentId, String fullPath) {
        SysFile directory = personalFile(id, "dir", 0L, null);
        directory.setIsDirectory(1);
        directory.setParentId(parentId);
        directory.setFullPath(fullPath);
        return directory;
    }

    protected SysFile personalFile(Long id, String originalName, Long fileSize, String fileHash) {
        SysFile file = TeamFileTestFixtures.teamFile(USER_ID, USER_ID, id, 0);
        file.setSpaceType("PERSONAL");
        file.setSpaceId(USER_ID);
        file.setUserId(USER_ID);
        file.setOriginalName(originalName);
        file.setFileSize(fileSize);
        file.setFileHash(fileHash);
        file.setFullPath(id == null ? null : String.valueOf(id));
        return file;
    }

    protected MultipartFile uploadFile() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[] {1, 2, 3});
    }

    protected SysFile teamFile(Long id, Integer isDirectory) {
        return TeamFileTestFixtures.teamFile(TEAM_ID, USER_ID, id, isDirectory);
    }

    protected SysFile teamDirectory(Long id, Long parentId, String fullPath) {
        return TeamFileTestFixtures.teamDirectory(TEAM_ID, USER_ID, id, parentId, fullPath);
    }

    protected StoredFileObjectBO storedObject(Long size, String fileHash) {
        StoredFileObjectBO object = new StoredFileObjectBO();
        object.setStoredName("stored.pdf");
        object.setObjectPath("202605/report.pdf");
        object.setFileUrl("http://example/report.pdf");
        object.setFileSize(size);
        object.setMimeType("application/pdf");
        object.setFileHash(fileHash);
        return object;
    }

    protected void assignInsertedIds(Long... ids) {
        ArgumentCaptor<SysFile> captor = ArgumentCaptor.forClass(SysFile.class);
        final int[] index = {0};
        doAnswer(invocation -> {
            SysFile file = invocation.getArgument(0);
            file.setId(ids[index[0]++]);
            return 1;
        }).when(sysFileMapper).insert(captor.capture());
    }

    protected <T> T withCurrentUser(java.util.function.Supplier<T> action) {
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            return action.get();
        }
    }
}
