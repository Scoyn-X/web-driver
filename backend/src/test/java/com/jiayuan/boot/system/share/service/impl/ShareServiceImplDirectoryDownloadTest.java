package com.jiayuan.boot.system.share.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.oss.utils.DownloadThrottleSupport;
import com.jiayuan.boot.system.share.converter.ShareFileConverter;
import com.jiayuan.boot.system.share.converter.SysShareConverter;
import com.jiayuan.boot.system.share.mapper.SysShareMapper;
import com.jiayuan.boot.system.share.model.entity.SysShare;
import com.jiayuan.boot.system.share.model.vo.ShareDownloadResponseVO;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShareServiceImpl 目录分享下载单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShareServiceImpl 目录分享下载")
class ShareServiceImplDirectoryDownloadTest {

    private static final String SHARE_TOKEN = "share-token";
    private static final Long USER_ID = 7L;
    private static final Long ACCOUNT_ID = 77L;
    private static final Long TEAM_ID = 17L;
    private static final Long ROOT_ID = 100L;
    private static final Long CHILD_ID = 101L;

    @Mock private SysShareMapper sysShareMapper;
    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysShareConverter sysShareConverter;
    @Mock private ShareFileConverter shareFileConverter;
    @Mock private FileObjectService fileObjectService;
    @Mock private TeamPermissionService teamPermissionService;
    @Mock private DownloadThrottleSupport downloadThrottleSupport;
    @InjectMocks private ShareServiceImpl shareService;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/s/" + SHARE_TOKEN + "/download");
        request.setScheme("https");
        request.setServerName("cloud.example.test");
        request.setServerPort(443);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("公开文件分享：返回后端受控下载 URL")
    void downloadPublicFile_returnsControlledUrl() {
        SysFile root = file(ROOT_ID, 0L, 0, String.valueOf(ROOT_ID), "public.txt");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(personalShare());
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);

        ShareDownloadResponseVO result = shareService.getDownloadUrl(SHARE_TOKEN, null);

        assertThat(result.getDownloadUrl())
                .isEqualTo("https://cloud.example.test/api/v1/s/" + SHARE_TOKEN + "/download/file");
        assertThat(result.getFileName()).isEqualTo("public.txt");
        verify(fileObjectService, never()).getPresignedDownloadUrl(root);
    }

    @Test
    @DisplayName("分享码文件分享：提取码正确时保留 code 参数")
    void downloadCodeFile_returnsControlledUrlWithCode() {
        SysShare share = personalShare();
        share.setAccessType(1);
        share.setExtractCode("ABCD");
        SysFile root = file(ROOT_ID, 0L, 0, String.valueOf(ROOT_ID), "protected.txt");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(share);
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);

        ShareDownloadResponseVO result = shareService.getDownloadUrl(SHARE_TOKEN, "ABCD");

        assertThat(result.getDownloadUrl())
                .isEqualTo("https://cloud.example.test/api/v1/s/"
                        + SHARE_TOKEN + "/download/file?code=ABCD");
        verify(fileObjectService, never()).getPresignedDownloadUrl(root);
    }

    @Test
    @DisplayName("分享码错误：拒绝生成下载 URL")
    void downloadWrongCode_throws() {
        SysShare share = personalShare();
        share.setAccessType(1);
        share.setExtractCode("ABCD");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(share);

        assertThatThrownBy(() -> shareService.getDownloadUrl(SHARE_TOKEN, "WRONG"))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXTRACT_CODE_WRONG);
    }

    @Test
    @DisplayName("分享源文件不存在：抛 SHARE_FILE_DELETED")
    void downloadMissingFile_throws() {
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(personalShare());
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(null);

        assertThatThrownBy(() -> shareService.getDownloadUrl(SHARE_TOKEN, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode").isEqualTo(ResultCode.SHARE_FILE_DELETED);
    }

    @Test
    @DisplayName("目录根节点下载：拒绝直接下载目录")
    void downloadDirectoryRoot_throws() {
        SysFile root = file(ROOT_ID, 0L, 1, String.valueOf(ROOT_ID), "shared-dir");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(personalShare());
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);

        assertThatThrownBy(() -> shareService.getDownloadUrl(SHARE_TOKEN, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXCEPTION);
    }

    @Test
    @DisplayName("团队文件分享：返回后端受控下载 URL")
    void downloadTeamFile_returnsControlledUrl() {
        SysShare share = teamShare();
        SysFile root = file(ROOT_ID, 0L, 0, String.valueOf(ROOT_ID), "team.txt");
        root.setSpaceType("TEAM");
        root.setSpaceId(TEAM_ID);
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(share);
        when(sysFileMapper.selectTeamFile(TEAM_ID, ROOT_ID)).thenReturn(root);

        ShareDownloadResponseVO result = shareService.getDownloadUrl(SHARE_TOKEN, null);

        assertThat(result.getDownloadUrl())
                .isEqualTo("https://cloud.example.test/api/v1/s/" + SHARE_TOKEN + "/download/file");
        assertThat(result.getFileName()).isEqualTo("team.txt");
        verify(fileObjectService, never()).getPresignedDownloadUrl(root);
    }

    @Test
    @DisplayName("后端受控下载：校验分享后委托对象存储按分享策略写出")
    void downloadFile_delegatesToSharedResponseWriter() {
        SysShare share = personalShare();
        share.setAccessType(1);
        share.setExtractCode("ABCD");
        SysFile root = file(ROOT_ID, 0L, 0, String.valueOf(ROOT_ID), "protected.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(share);
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);

        shareService.downloadFile(SHARE_TOKEN, "ABCD", null, response);

        verify(fileObjectService).writeSharedToResponse(eq(root), any(HttpServletResponse.class));
    }

    @Test
    @DisplayName("后端受控下载：目录根节点拒绝直接下载")
    void downloadFile_directoryRootThrows() {
        SysFile root = file(ROOT_ID, 0L, 1, String.valueOf(ROOT_ID), "shared-dir");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(personalShare());
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);

        assertThatThrownBy(() -> shareService.downloadFile(SHARE_TOKEN, null, null, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXCEPTION);

        verify(fileObjectService, never()).writeSharedToResponse(any(), any());
    }

    @Test
    @DisplayName("后端受控下载：分享源文件缺失时拒绝")
    void downloadFile_missingRootThrows() {
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(personalShare());
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(null);

        assertThatThrownBy(() -> shareService.downloadFile(SHARE_TOKEN, null, null, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode").isEqualTo(ResultCode.SHARE_FILE_DELETED);
    }

    @Test
    @DisplayName("文件分享下载其他文件：非目录根节点不能越权下载")
    void downloadSharedChildFromFileShareThrows() {
        SysFile root = file(ROOT_ID, 0L, 0, String.valueOf(ROOT_ID), "root.txt");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(personalShare());
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);

        assertThatThrownBy(() -> shareService.getDownloadUrl(SHARE_TOKEN, null, CHILD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXCEPTION);
    }

    @Test
    @DisplayName("已取消分享：访问时抛 SHARE_CANCELLED")
    void getShareByToken_cancelledShareThrows() {
        SysShare share = personalShare();
        share.setStatus(1);
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(share);

        assertThatThrownBy(() -> shareService.getShareByToken(SHARE_TOKEN))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode").isEqualTo(ResultCode.SHARE_CANCELLED);
    }

    @Test
    @DisplayName("SSE 进度下载：读取文件流、发送分片并执行分享限速")
    void downloadWithProgress_successStreamsAndThrottles() throws Exception {
        SysFile root = file(ROOT_ID, 0L, 0, String.valueOf(ROOT_ID), "stream.txt");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(personalShare());
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);
        when(downloadThrottleSupport.shouldThrottleSharedDownload(12L)).thenReturn(false);
        when(fileObjectService.getFileStream(root))
                .thenReturn(new ByteArrayInputStream("hello".getBytes()));

        assertThat(shareService.downloadWithProgress(SHARE_TOKEN, null, null)).isNotNull();

        verify(fileObjectService, timeout(1000)).getFileStream(root);
        verify(downloadThrottleSupport, timeout(1000)).throttleIfNecessary(false, 5);
    }

    @Test
    @DisplayName("SSE 进度下载：文件流异常时进入错误完成分支")
    void downloadWithProgress_streamFailureCompletesWithError() throws Exception {
        SysFile root = file(ROOT_ID, 0L, 0, String.valueOf(ROOT_ID), "broken.txt");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(personalShare());
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);
        when(downloadThrottleSupport.shouldThrottleSharedDownload(12L)).thenReturn(true);
        when(fileObjectService.getFileStream(root)).thenThrow(new IllegalStateException("stream broken"));

        assertThat(shareService.downloadWithProgress(SHARE_TOKEN, null, null)).isNotNull();

        verify(fileObjectService, timeout(1000)).getFileStream(root);
    }

    @Test
    @DisplayName("SSE 进度下载：目录根节点拒绝")
    void downloadWithProgress_directoryRootThrows() {
        SysFile root = file(ROOT_ID, 0L, 1, String.valueOf(ROOT_ID), "shared-dir");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(personalShare());
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);

        assertThatThrownBy(() -> shareService.downloadWithProgress(SHARE_TOKEN, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXCEPTION);
    }

    @Test
    @DisplayName("目录分享子文件下载：校验分享范围后返回后端受控下载 URL")
    void downloadSharedChild_success() {
        SysShare share = personalShare();
        SysFile root = file(ROOT_ID, 0L, 1, String.valueOf(ROOT_ID), "shared-dir");
        SysFile child = file(CHILD_ID, ROOT_ID, 0, String.valueOf(ROOT_ID), "child.txt");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(share);
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);
        when(sysFileMapper.selectSpaceFile("PERSONAL", USER_ID, CHILD_ID)).thenReturn(child);

        ShareDownloadResponseVO result = shareService.getDownloadUrl(SHARE_TOKEN, null, CHILD_ID);

        assertThat(result.getDownloadUrl())
                .isEqualTo("https://cloud.example.test/api/v1/s/"
                        + SHARE_TOKEN + "/download/file?fileId=" + CHILD_ID);
        assertThat(result.getFileName()).isEqualTo("child.txt");
        verify(fileObjectService, never()).getPresignedDownloadUrl(child);
    }

    @Test
    @DisplayName("目录分享子文件下载：越出分享范围时拒绝")
    void downloadSharedChildOutsideRoot_throws() {
        SysShare share = personalShare();
        SysFile root = file(ROOT_ID, 0L, 1, String.valueOf(ROOT_ID), "shared-dir");
        SysFile outside = file(CHILD_ID, 999L, 0, "999", "outside.txt");
        when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(share);
        when(sysFileMapper.selectPersonalShareFile(USER_ID, ROOT_ID)).thenReturn(root);
        when(sysFileMapper.selectSpaceFile("PERSONAL", USER_ID, CHILD_ID)).thenReturn(outside);

        assertThatThrownBy(() -> shareService.getDownloadUrl(SHARE_TOKEN, null, CHILD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXCEPTION);
        verify(fileObjectService, never()).getPresignedDownloadUrl(outside);
    }

    private SysShare personalShare() {
        SysShare share = new SysShare();
        share.setUserId(USER_ID);
        share.setFileId(ROOT_ID);
        share.setShareToken(SHARE_TOKEN);
        share.setAccessType(0);
        share.setStatus(0);
        return share;
    }

    private SysShare teamShare() {
        SysShare share = personalShare();
        share.setSpaceType("TEAM");
        share.setTeamId(TEAM_ID);
        share.setCreatorAccountId(ACCOUNT_ID);
        return share;
    }

    private SysFile file(Long id, Long parentId, Integer isDirectory, String fullPath, String name) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setUserId(USER_ID);
        file.setSpaceType("PERSONAL");
        file.setSpaceId(USER_ID);
        file.setParentId(parentId);
        file.setIsDirectory(isDirectory);
        file.setFullPath(fullPath);
        file.setOriginalName(name);
        file.setFileSize(12L);
        file.setMimeType("text/plain");
        return file;
    }
}
