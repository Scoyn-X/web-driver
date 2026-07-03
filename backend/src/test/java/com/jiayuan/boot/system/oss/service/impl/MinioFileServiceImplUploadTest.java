package com.jiayuan.boot.system.oss.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.mapper.SysFileObjectMapper;
import com.jiayuan.boot.system.oss.model.entity.FileInfo;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;
import com.jiayuan.boot.system.oss.utils.DownloadThrottleSupport;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinioFileServiceImpl 上传相关单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MinioFileServiceImpl 上传单元测试")
class MinioFileServiceImplUploadTest {

    private static final Long CURRENT_USER_ID = 1L;

    @Mock private MinioClient minioClient;
    @Mock private QuotaService quotaService;
    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private SysFileObjectMapper sysFileObjectMapper;
    @Mock private SystemConfigProperties configProperties;
    @Mock private DownloadThrottleSupport downloadThrottleSupport;

    @InjectMocks
    private MinioFileServiceImpl service;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        service.setBucketName("test-bucket");
        service.setEndpoint("http://localhost:9000");
        service.setMinioClient(minioClient);
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(CURRENT_USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    @DisplayName("普通用户上传超过单文件限制时拒绝且不继续扣配额")
    void upload_exceedsSingleFileLimit_throwsBeforeQuotaReservation() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.txt", "text/plain", "xx".getBytes());
        doThrow(new BusinessException(ResultCode.USER_QUOTA_EXHAUSTED, "单文件大小超过限制"))
                .when(quotaService).checkSingleFileLimit(CURRENT_USER_ID, 2L);

        assertThatThrownBy(() -> service.uploadFile(file, 0L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_QUOTA_EXHAUSTED);

        verify(quotaService).checkSingleFileLimit(CURRENT_USER_ID, 2L);
        verify(quotaService, never()).checkQuota(CURRENT_USER_ID, 2L);
    }

    @Test
    @DisplayName("上传命中已有指纹：跳过 MinIO 上传，仅增加引用并写逻辑文件")
    void upload_existingHash_reusesObjectAndStoresMetadata() throws Exception {
        MockMultipartFile file = uploadFile("same.txt", "same-content");
        String hash = DigestUtil.sha256Hex(file.getBytes());
        SysFileObject existing = fileObject(hash, "20260605/existing.txt", 2);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(sysFileObjectMapper.selectOne(any())).thenReturn(existing);
        when(sysFileMapper.insert(any(SysFile.class))).thenAnswer(invocation -> {
            SysFile inserted = invocation.getArgument(0);
            inserted.setId(10L);
            return 1;
        });

        FileInfo result = service.uploadFile(file, null);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getName()).isEqualTo("same.txt");
        assertThat(result.getUrl()).isEqualTo("http://localhost:9000/test-bucket/20260605/existing.txt");
        ArgumentCaptor<SysFile> inserted = ArgumentCaptor.forClass(SysFile.class);
        verify(sysFileMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getFileHash()).isEqualTo(hash);
        assertThat(inserted.getValue().getStoredName()).isEqualTo("existing.txt");
        assertThat(inserted.getValue().getParentId()).isZero();
        assertThat(inserted.getValue().getSpaceType()).isEqualTo("PERSONAL");
        verify(sysFileObjectMapper).update(any(), any());
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
        verify(quotaService).increaseUsedSpace(CURRENT_USER_ID, file.getSize());
    }

    @Test
    @DisplayName("上传新指纹：写入 MinIO、物理对象和嵌套目录 fullPath")
    void upload_newHash_uploadsAndBuildsNestedFullPath() throws Exception {
        MockMultipartFile file = uploadFile("nested.txt", "new-content");
        SysFile parent = directory(5L, "1,5");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 5L)).thenReturn(parent);
        when(sysFileObjectMapper.selectOne(any())).thenReturn(null);
        when(sysFileObjectMapper.insert(any(SysFileObject.class))).thenReturn(1);
        when(sysFileMapper.insert(any(SysFile.class))).thenAnswer(invocation -> {
            SysFile inserted = invocation.getArgument(0);
            inserted.setId(11L);
            return 1;
        });

        FileInfo result = service.uploadFile(file, 5L);

        assertThat(result.getId()).isEqualTo(11L);
        verify(minioClient).putObject(any(PutObjectArgs.class));
        ArgumentCaptor<SysFileObject> objectCaptor = ArgumentCaptor.forClass(SysFileObject.class);
        verify(sysFileObjectMapper).insert(objectCaptor.capture());
        assertThat(objectCaptor.getValue().getFileHash()).isEqualTo(DigestUtil.sha256Hex(file.getBytes()));
        assertThat(objectCaptor.getValue().getRefCount()).isEqualTo(1);
        ArgumentCaptor<SysFile> updated = ArgumentCaptor.forClass(SysFile.class);
        verify(sysFileMapper).updateById(updated.capture());
        assertThat(updated.getValue().getFullPath()).isEqualTo("1,5,11");
        assertThat(updated.getValue().getParentId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("上传并发指纹冲突：删除本次孤儿对象并复用胜出物理对象")
    void upload_duplicateHashRace_removesUploadedObjectAndUsesWinner() throws Exception {
        MockMultipartFile file = uploadFile("race.txt", "race-content");
        String hash = DigestUtil.sha256Hex(file.getBytes());
        SysFileObject winner = fileObject(hash, "20260605/winner.txt", 1);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(sysFileObjectMapper.selectOne(any())).thenReturn(null, winner);
        when(sysFileObjectMapper.insert(any(SysFileObject.class)))
                .thenThrow(new DuplicateKeyException("duplicate hash"));
        when(sysFileMapper.insert(any(SysFile.class))).thenAnswer(invocation -> {
            SysFile inserted = invocation.getArgument(0);
            inserted.setId(12L);
            return 1;
        });

        FileInfo result = service.uploadFile(file, 0L);

        assertThat(result.getId()).isEqualTo(12L);
        assertThat(result.getUrl()).isEqualTo("http://localhost:9000/test-bucket/20260605/winner.txt");
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(sysFileObjectMapper).update(any(), any());
    }

    @Test
    @DisplayName("上传读取文件失败：转换为上传业务异常")
    void upload_readFailureWrapsUploadException() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(file.getSize()).thenReturn(4L);
        when(file.getOriginalFilename()).thenReturn("broken.txt");
        when(file.getContentType()).thenReturn("text/plain");
        when(file.getBytes()).thenThrow(new IOException("broken file"));

        assertThatThrownBy(() -> service.uploadFile(file, 0L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.UPLOAD_FILE_EXCEPTION);
    }

    private static MockMultipartFile uploadFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes());
    }

    private static SysFileObject fileObject(String hash, String objectPath, int refCount) {
        SysFileObject object = new SysFileObject();
        object.setFileHash(hash);
        object.setObjectPath(objectPath);
        object.setFileSize(12L);
        object.setRefCount(refCount);
        return object;
    }

    private static SysFile directory(Long id, String fullPath) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setOriginalName("parent");
        file.setUserId(CURRENT_USER_ID);
        file.setSpaceType("PERSONAL");
        file.setSpaceId(CURRENT_USER_ID);
        file.setParentId(0L);
        file.setIsDirectory(1);
        file.setInRecycleBin(0);
        file.setFullPath(fullPath);
        return file;
    }
}
