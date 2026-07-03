package com.jiayuan.boot.system.oss.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.mapper.SysFileObjectMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileTreeResponseVO;
import com.jiayuan.boot.system.oss.utils.DownloadThrottleSupport;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinioFileServiceImpl 下载、搜索、树形列表等非上传路径单元测试。
 *
 * @author charleslam
 * @since 2026/06/05
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MinioFileServiceImpl 下载与搜索单元测试")
class MinioFileServiceImplDownloadAndSearchTest {

    private static final Long CURRENT_USER_ID = 1L;

    @Mock private MinioClient minioClient;
    @Mock private QuotaService quotaService;
    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private SysFileObjectMapper sysFileObjectMapper;
    @Mock private SystemConfigProperties configProperties;
    @Mock private DownloadThrottleSupport downloadThrottleSupport;

    private MinioFileServiceImpl service;
    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeAll
    static void initMybatisPlusLambda() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysFile.class);
        TableInfoHelper.initTableInfo(assistant, SysFileObject.class);
    }

    @BeforeEach
    void setUp() {
        service = new MinioFileServiceImpl(
                sysFileMapper, sysFileConverter, quotaService, sysFileObjectMapper,
                sysUserMapper, configProperties, downloadThrottleSupport);
        service.setBucketName("test-bucket");
        service.setEndpoint("http://localhost:9000");
        service.setAccessKey("minioadmin");
        service.setSecretKey("minioadmin");
        service.setMinioClient(minioClient);
        when(configProperties.getTrashRetentionSeconds()).thenReturn(259200L);

        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(CURRENT_USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    @DisplayName("初始化：根据配置构造 MinIO 客户端")
    void init_buildsMinioClientFromConfiguration() {
        service.init();

        assertThat(service.getMinioClient()).isNotNull();
    }

    @Test
    @DisplayName("按完整 URL 删除：规范化对象路径后委托回收站删除")
    void deleteFile_byUrlNormalizesPathAndMovesRootToTrash() {
        SysFile file = activeFile(20L, "a.txt", "20260605/a.txt", 5L);
        when(sysFileMapper.selectPersonalActiveByPath(CURRENT_USER_ID, "20260605/a.txt")).thenReturn(file);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 20L)).thenReturn(file);
        when(sysFileMapper.moveRootToTrashInSpace("PERSONAL", CURRENT_USER_ID, 20L, CURRENT_USER_ID, 259200L))
                .thenReturn(1);

        assertThat(service.deleteFile("http://localhost:9000/test-bucket/20260605/a.txt")).isTrue();

        verify(sysFileMapper).selectPersonalActiveByPath(CURRENT_USER_ID, "20260605/a.txt");
        verify(sysFileMapper).moveRootToTrashInSpace(
                "PERSONAL", CURRENT_USER_ID, 20L, CURRENT_USER_ID, 259200L);
        verify(quotaService).decreaseUsedSpace(CURRENT_USER_ID, 5L);
    }

    @Test
    @DisplayName("按对象路径删除：文件不存在时抛资源不存在")
    void deleteFile_missingPathThrowsBusinessException() {
        when(sysFileMapper.selectPersonalActiveByPath(CURRENT_USER_ID, "missing.txt")).thenReturn(null);

        assertThatThrownBy(() -> service.deleteFile("/test-bucket/missing.txt"))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("按对象路径删除：Mapper 异常转换为删除业务异常")
    void deleteFile_mapperFailureWrapsDeleteException() {
        when(sysFileMapper.selectPersonalActiveByPath(CURRENT_USER_ID, "broken.txt"))
                .thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> service.deleteFile("broken.txt"))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DELETE_FILE_EXCEPTION);
    }

    @Test
    @DisplayName("列表：根目录返回根面包屑并透传文件项")
    void listFiles_rootParentReturnsRootBreadcrumbAndItems() {
        SysFile file = activeFile(21L, "root.txt", "20260605/root.txt", 3L);
        FileInfoResponseVO vo = new FileInfoResponseVO();
        vo.setId(21L);
        when(sysFileMapper.selectPersonalChildren(CURRENT_USER_ID, 0L)).thenReturn(List.of(file));
        when(sysFileConverter.toFileInfoVOList(List.of(file))).thenReturn(List.of(vo));

        FileListResponseVO result = service.listFiles(null);

        assertThat(result.getItems()).containsExactly(vo);
        assertThat(result.getBreadcrumb()).hasSize(1);
        assertThat(result.getBreadcrumb().get(0).getId()).isZero();
        assertThat(result.getBreadcrumb().get(0).getName()).isEqualTo("根目录");
    }

    @Test
    @DisplayName("列表：嵌套目录按 fullPath 批量映射面包屑")
    void listFiles_nestedParentBuildsBreadcrumbFromFullPath() {
        SysFile ancestor = directory(22L, 0L, "A");
        SysFile parent = directory(23L, 22L, "B");
        parent.setFullPath("22,23");
        SysFile file = activeFile(24L, "inside.txt", "20260605/inside.txt", 3L);
        file.setParentId(23L);
        FileInfoResponseVO vo = new FileInfoResponseVO();
        vo.setId(24L);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 23L)).thenReturn(parent);
        when(sysFileMapper.selectPersonalChildren(CURRENT_USER_ID, 23L)).thenReturn(List.of(file));
        when(sysFileMapper.selectFilesInSpaceByIds(eq("PERSONAL"), eq(CURRENT_USER_ID), any()))
                .thenReturn(List.of(ancestor, parent));
        when(sysFileConverter.toFileInfoVOList(List.of(file))).thenReturn(List.of(vo));

        FileListResponseVO result = service.listFiles(23L);

        assertThat(result.getItems()).containsExactly(vo);
        assertThat(result.getBreadcrumb()).extracting("name")
                .containsExactly("根目录", "A", "B");
    }

    @Test
    @DisplayName("列表：父目录缺少 fullPath 时仅返回根面包屑")
    void listFiles_parentWithoutFullPathFallsBackToRootBreadcrumb() {
        SysFile parent = directory(23L, 0L, "B");
        parent.setFullPath("");
        SysFile file = activeFile(24L, "inside.txt", "20260605/inside.txt", 3L);
        FileInfoResponseVO vo = new FileInfoResponseVO();
        vo.setId(24L);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 23L)).thenReturn(parent);
        when(sysFileMapper.selectPersonalChildren(CURRENT_USER_ID, 23L)).thenReturn(List.of(file));
        when(sysFileConverter.toFileInfoVOList(List.of(file))).thenReturn(List.of(vo));

        FileListResponseVO result = service.listFiles(23L);

        assertThat(result.getItems()).containsExactly(vo);
        assertThat(result.getBreadcrumb()).hasSize(1);
        assertThat(result.getBreadcrumb().get(0).getName()).isEqualTo("根目录");
        verify(sysFileMapper, never()).selectFilesInSpaceByIds(eq("PERSONAL"), eq(CURRENT_USER_ID), any());
    }

    @Test
    @DisplayName("文件树：目录递归填充 children，文件 children 为空")
    void listFileTree_buildsRecursiveTree() {
        SysFile dir = directory(10L, 0L, "dir");
        SysFile childFile = activeFile(11L, "child.txt", "20260605/child.txt", 4L);
        childFile.setParentId(10L);
        when(sysFileMapper.selectPersonalActiveTree(CURRENT_USER_ID)).thenReturn(List.of(dir, childFile));
        when(sysFileConverter.toFileTreeResponseVO(dir)).thenReturn(treeNode(10L, 0L, 1, "dir"));
        when(sysFileConverter.toFileTreeResponseVO(childFile)).thenReturn(treeNode(11L, 10L, 0, "child.txt"));

        List<FileTreeResponseVO> result = service.listFileTree();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getChildren()).hasSize(1);
        assertThat(result.get(0).getChildren().get(0).getId()).isEqualTo(11L);
        assertThat(result.get(0).getChildren().get(0).getChildren()).isEmpty();
    }

    @Test
    @DisplayName("按 ID 下载：写响应头并按当前用户限速复制")
    void downloadFile_successWritesHeadersAndDelegatesThrottle() throws Exception {
        SysFile file = activeFile(30L, "demo file.txt", "20260605/demo.txt", 4L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 30L)).thenReturn(file);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObject("demo"));

        service.downloadFile(30L, response);

        assertThat(response.getContentType()).isEqualTo("text/plain");
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"demo%20file.txt\"");
        assertThat(response.getContentLengthLong()).isEqualTo(4L);
        verify(downloadThrottleSupport).throttledCopy(
                any(), any(OutputStream.class), eq(4L), eq(CURRENT_USER_ID), eq(quotaService));
    }

    @Test
    @DisplayName("按 ID 下载：MinIO 异常转换为系统错误")
    void downloadFile_minioFailureWrapsSystemError() throws Exception {
        SysFile file = activeFile(32L, "broken.txt", "20260605/broken.txt", 4L);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 32L)).thenReturn(file);
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new IllegalStateException("minio down"));

        assertThatThrownBy(() -> service.downloadFile(32L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.SYSTEM_ERROR);
    }

    @Test
    @DisplayName("下载目录：拒绝目录下载")
    void downloadFile_directoryThrowsBusinessException() throws Exception {
        SysFile dir = directory(31L, 0L, "dir");
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 31L)).thenReturn(dir);

        assertThatThrownBy(() -> service.downloadFile(31L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DOWNLOAD_FILE_EXCEPTION);

        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    @DisplayName("按路径下载：路径不存在时拒绝")
    void downloadFile_byPathMissingThrowsBusinessException() {
        when(sysFileMapper.selectPersonalActiveByPath(CURRENT_USER_ID, "missing.txt")).thenReturn(null);

        assertThatThrownBy(() -> service.downloadFile("/missing.txt", new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("按路径下载：去除 bucket 前缀后委托 ID 下载")
    void downloadFile_byPathWithBucketPrefixDelegatesToIdDownload() throws Exception {
        SysFile file = activeFile(33L, "path.txt", "20260605/path.txt", 4L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(sysFileMapper.selectPersonalActiveByPath(CURRENT_USER_ID, "20260605/path.txt")).thenReturn(file);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 33L)).thenReturn(file);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObject("path"));

        service.downloadFile("test-bucket/20260605/path.txt", response);

        assertThat(response.getContentType()).isEqualTo("text/plain");
        assertThat(response.getContentLengthLong()).isEqualTo(4L);
        verify(sysFileMapper).selectPersonalActiveByPath(CURRENT_USER_ID, "20260605/path.txt");
        verify(downloadThrottleSupport).throttledCopy(
                any(), any(OutputStream.class), eq(4L), eq(CURRENT_USER_ID), eq(quotaService));
    }

    @Test
    @DisplayName("预签名下载：目录不能生成下载链接")
    void getPresignedDownloadUrl_directoryThrowsBusinessException() {
        SysFile dir = directory(40L, 0L, "dir");
        when(sysFileMapper.selectById(40L)).thenReturn(dir);

        assertThatThrownBy(() -> service.getPresignedDownloadUrl(40L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DOWNLOAD_FILE_EXCEPTION);
    }

    @Test
    @DisplayName("预签名下载：普通文件返回 MinIO URL")
    void getPresignedDownloadUrl_successReturnsUrl() throws Exception {
        SysFile file = activeFile(42L, "signed.txt", "20260605/signed.txt", 1L);
        when(sysFileMapper.selectById(42L)).thenReturn(file);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/test-bucket/20260605/signed.txt?X-Amz-Signature=ok");

        assertThat(service.getPresignedDownloadUrl(42L))
                .contains("X-Amz-Signature=ok");
    }

    @Test
    @DisplayName("预签名下载：已删除或回收站文件按不存在处理")
    void getPresignedDownloadUrl_deletedOrRecycledThrowsNotFound() {
        SysFile file = activeFile(43L, "old.txt", "20260605/old.txt", 1L);
        file.setInRecycleBin(1);
        when(sysFileMapper.selectById(43L)).thenReturn(file);

        assertThatThrownBy(() -> service.getPresignedDownloadUrl(43L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("预签名下载：MinIO 异常转换为下载业务异常")
    void getPresignedDownloadUrl_minioFailureWrapsBusinessException() throws Exception {
        SysFile file = activeFile(41L, "signed.txt", "20260605/signed.txt", 1L);
        when(sysFileMapper.selectById(41L)).thenReturn(file);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new IllegalStateException("minio unavailable"));

        assertThatThrownBy(() -> service.getPresignedDownloadUrl(41L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DOWNLOAD_FILE_EXCEPTION);
    }

    @Test
    @DisplayName("搜索：空关键词直接返回空列表")
    void searchFiles_blankKeywordReturnsEmptyList() {
        assertThat(service.searchFiles("   ")).isEmpty();

        verify(sysFileMapper, never()).searchPersonalFiles(eq(CURRENT_USER_ID), any());
    }

    @Test
    @DisplayName("搜索：裁剪关键词后查询并透传 converter")
    void searchFiles_trimsKeywordAndConvertsResult() {
        SysFile file = activeFile(50L, "report.txt", "20260605/report.txt", 8L);
        FileInfoResponseVO vo = new FileInfoResponseVO();
        vo.setId(50L);
        when(sysFileMapper.searchPersonalFiles(CURRENT_USER_ID, "report")).thenReturn(List.of(file));
        when(sysFileConverter.toFileInfoVOList(List.of(file))).thenReturn(List.of(vo));

        List<FileInfoResponseVO> result = service.searchFiles("  report  ");

        assertThat(result).containsExactly(vo);
        verify(sysFileMapper).searchPersonalFiles(CURRENT_USER_ID, "report");
    }

    @Test
    @DisplayName("复制：嵌套目标目录会拼接新 fullPath 并增加引用计数")
    void copyFile_nestedTargetBuildsFullPathAndIncrementsReference() {
        SysFile source = activeFile(60L, "copy.txt", "20260605/copy.txt", 5L);
        source.setFileHash("hash-copy");
        SysFile target = directory(61L, 0L, "target");
        target.setFullPath("61");
        FileInfoResponseVO vo = new FileInfoResponseVO();
        vo.setId(62L);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 60L)).thenReturn(source);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 61L)).thenReturn(target);
        doAnswer(inv -> {
            SysFile inserted = inv.getArgument(0);
            inserted.setId(62L);
            return 1;
        }).when(sysFileMapper).insert(any(SysFile.class));
        when(sysFileConverter.toFileInfoVO(any(SysFile.class))).thenReturn(vo);

        FileInfoResponseVO result = service.copyFile(60L, 61L);

        assertThat(result).isSameAs(vo);
        ArgumentCaptor<SysFile> captor = ArgumentCaptor.forClass(SysFile.class);
        verify(sysFileMapper).insert(captor.capture());
        SysFile copied = captor.getValue();
        assertThat(copied.getParentId()).isEqualTo(61L);
        assertThat(copied.getFullPath()).isEqualTo("61,62");
        verify(sysFileObjectMapper).update(any(), any());
        verify(quotaService).increaseUsedSpace(CURRENT_USER_ID, 5L);
    }

    @Test
    @DisplayName("移动：同父目录直接拒绝，不校验目标目录")
    void moveFile_sameParentThrowsBeforeTargetValidation() {
        SysFile source = activeFile(70L, "same.txt", "20260605/same.txt", 1L);
        source.setParentId(9L);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 70L)).thenReturn(source);

        assertThatThrownBy(() -> service.moveFile(70L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(sysFileMapper, never()).updateById(any(SysFile.class));
    }

    @Test
    @DisplayName("移动：目录不能移动到自身子目录")
    void moveFile_directoryToDescendantThrows() {
        SysFile source = directory(80L, 0L, "docs");
        source.setFullPath("80");
        SysFile target = directory(81L, 80L, "child");
        target.setFullPath("80,81");
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 80L)).thenReturn(source);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 81L)).thenReturn(target);

        assertThatThrownBy(() -> service.moveFile(80L, 81L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

        verify(sysFileMapper, never()).updateById(any(SysFile.class));
    }

    @Test
    @DisplayName("移动：目录移动到嵌套目标后同步后代 fullPath")
    void moveFile_directoryToNestedTargetUpdatesDescendantsFullPath() {
        SysFile source = directory(90L, 0L, "docs");
        source.setFullPath("90");
        SysFile target = directory(91L, 0L, "target");
        target.setFullPath("91");
        FileInfoResponseVO vo = new FileInfoResponseVO();
        vo.setId(90L);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 90L)).thenReturn(source);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 91L)).thenReturn(target);
        when(sysFileConverter.toFileInfoVO(source)).thenReturn(vo);

        FileInfoResponseVO result = service.moveFile(90L, 91L);

        assertThat(result).isSameAs(vo);
        assertThat(source.getParentId()).isEqualTo(91L);
        assertThat(source.getFullPath()).isEqualTo("91,90");
        verify(sysFileMapper).updateById(source);
        verify(sysFileMapper).updateDescendantsFullPathInSpace("PERSONAL", CURRENT_USER_ID, 90L, "91,90");
    }

    private static SysFile activeFile(Long id, String name, String path, Long size) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setOriginalName(name);
        file.setStoredName(name);
        file.setFilePath(path);
        file.setFileUrl("http://localhost:9000/test-bucket/" + path);
        file.setFileSize(size);
        file.setMimeType("text/plain");
        file.setUserId(CURRENT_USER_ID);
        file.setSpaceType("PERSONAL");
        file.setSpaceId(CURRENT_USER_ID);
        file.setUploaderId(CURRENT_USER_ID);
        file.setParentId(0L);
        file.setIsDirectory(0);
        file.setInRecycleBin(0);
        file.setRecycleRoot(0);
        file.setFullPath(String.valueOf(id));
        return file;
    }

    private static SysFile directory(Long id, Long parentId, String name) {
        SysFile file = activeFile(id, name, "", 0L);
        file.setParentId(parentId);
        file.setIsDirectory(1);
        return file;
    }

    private static FileTreeResponseVO treeNode(Long id, Long parentId, Integer isDirectory, String name) {
        FileTreeResponseVO vo = new FileTreeResponseVO();
        vo.setId(id);
        vo.setParentId(parentId);
        vo.setIsDirectory(isDirectory);
        vo.setOriginalName(name);
        return vo;
    }

    private static GetObjectResponse getObject(String content) {
        return new GetObjectResponse(
                null, "test-bucket", null, "object", new ByteArrayInputStream(content.getBytes()));
    }
}
