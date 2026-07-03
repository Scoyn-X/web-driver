package com.jiayuan.boot.system.oss.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileObjectMapper;
import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;
import com.jiayuan.boot.system.oss.utils.DownloadThrottleSupport;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MinIO 物理对象服务单元测试。
 *
 * @author charleslam
 * @since 2026/06/05
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MinioFileObjectServiceImpl 单元测试")
class MinioFileObjectServiceImplTest {

    private static final String BUCKET = "jiayuan-test";
    private static final String ENDPOINT = "http://minio:9000";

    @Mock private SysFileObjectMapper sysFileObjectMapper;
    @Mock private QuotaService quotaService;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private DownloadThrottleSupport downloadThrottleSupport;
    @Mock private MinioClient minioClient;

    private MinioFileObjectServiceImpl fileObjectService;

    @BeforeEach
    void setUp() {
        fileObjectService = new MinioFileObjectServiceImpl(
                sysFileObjectMapper, quotaService, sysFileConverter, downloadThrottleSupport);
        fileObjectService.setEndpoint(ENDPOINT);
        fileObjectService.setAccessKey("minioadmin");
        fileObjectService.setSecretKey("minioadmin");
        fileObjectService.setBucketName(BUCKET);
        fileObjectService.setMinioClient(minioClient);
    }

    @Test
    @DisplayName("初始化：按配置构造 MinIO 客户端")
    void init_buildsMinioClientFromConfiguration() {
        fileObjectService.init();

        assertThat(fileObjectService.getMinioClient()).isNotNull();
    }

    @Test
    @DisplayName("保存文件：相同指纹复用已存在物理对象")
    void saveOrReuse_existingObjectIncreasesReference() throws Exception {
        MockMultipartFile file = uploadFile("same.txt", "same-content");
        String hash = DigestUtil.sha256Hex(file.getBytes());
        SysFileObject existing = fileObject(hash, "20260605/existing.txt", 2);
        StoredFileObjectBO expected = storedObject(hash, existing.getObjectPath());
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(sysFileObjectMapper.selectByHash(hash)).thenReturn(existing);
        when(sysFileObjectMapper.increaseReference(hash)).thenReturn(1);
        when(sysFileConverter.toStoredFileObjectBO(
                eq(file), eq(hash), eq(existing.getObjectPath()),
                eq(ENDPOINT + "/" + BUCKET + "/" + existing.getObjectPath()))).thenReturn(expected);

        StoredFileObjectBO result = fileObjectService.saveOrReuse(file);

        assertThat(result).isSameAs(expected);
        verify(sysFileObjectMapper).increaseReference(hash);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("保存文件：新指纹上传 MinIO 并写入去重对象记录")
    void saveOrReuse_newObjectUploadsAndInsertsReference() throws Exception {
        MockMultipartFile file = uploadFile("report.txt", "new-content");
        String hash = DigestUtil.sha256Hex(file.getBytes());
        StoredFileObjectBO expected = storedObject(hash, "dynamic");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(sysFileObjectMapper.selectByHash(hash)).thenReturn(null);
        when(sysFileObjectMapper.insert(any(SysFileObject.class))).thenReturn(1);
        when(sysFileConverter.toStoredFileObjectBO(
                eq(file), eq(hash), anyString(), anyString())).thenReturn(expected);

        StoredFileObjectBO result = fileObjectService.saveOrReuse(file);

        ArgumentCaptor<SysFileObject> inserted = ArgumentCaptor.forClass(SysFileObject.class);
        assertThat(result).isSameAs(expected);
        verify(minioClient).putObject(any(PutObjectArgs.class));
        verify(sysFileObjectMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getFileHash()).isEqualTo(hash);
        assertThat(inserted.getValue().getObjectPath()).endsWith(".txt");
        assertThat(inserted.getValue().getFileSize()).isEqualTo(file.getSize());
        assertThat(inserted.getValue().getRefCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("保存文件：存储桶缺失时先创建桶和公开策略")
    void saveOrReuse_missingBucketCreatesBucketAndPolicy() throws Exception {
        MockMultipartFile file = uploadFile("bucket.txt", "bucket-content");
        String hash = DigestUtil.sha256Hex(file.getBytes());
        StoredFileObjectBO expected = storedObject(hash, "dynamic");
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        when(sysFileObjectMapper.selectByHash(hash)).thenReturn(null);
        when(sysFileObjectMapper.insert(any(SysFileObject.class))).thenReturn(1);
        when(sysFileConverter.toStoredFileObjectBO(
                eq(file), eq(hash), anyString(), anyString())).thenReturn(expected);

        StoredFileObjectBO result = fileObjectService.saveOrReuse(file);

        assertThat(result).isSameAs(expected);
        verify(minioClient).makeBucket(any());
        verify(minioClient).setBucketPolicy(any());
    }

    @Test
    @DisplayName("保存文件：并发指纹冲突时删除本次上传对象并复用胜出记录")
    void saveOrReuse_duplicateHashRaceRemovesOrphanAndUsesWinner() throws Exception {
        MockMultipartFile file = uploadFile("race.txt", "race-content");
        String hash = DigestUtil.sha256Hex(file.getBytes());
        SysFileObject winner = fileObject(hash, "20260605/winner.txt", 1);
        StoredFileObjectBO expected = storedObject(hash, winner.getObjectPath());
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(sysFileObjectMapper.selectByHash(hash)).thenReturn(null, winner);
        when(sysFileObjectMapper.insert(any(SysFileObject.class)))
                .thenThrow(new DuplicateKeyException("duplicate hash"));
        when(sysFileObjectMapper.increaseReference(hash)).thenReturn(1);
        when(sysFileConverter.toStoredFileObjectBO(
                eq(file), eq(hash), eq(winner.getObjectPath()),
                eq(ENDPOINT + "/" + BUCKET + "/" + winner.getObjectPath()))).thenReturn(expected);

        StoredFileObjectBO result = fileObjectService.saveOrReuse(file);

        assertThat(result).isSameAs(expected);
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(sysFileObjectMapper).increaseReference(hash);
    }

    @Test
    @DisplayName("保存文件：并发指纹冲突后胜出记录缺失时抛上传异常")
    void saveOrReuse_duplicateHashRaceWithoutWinnerThrows() throws Exception {
        MockMultipartFile file = uploadFile("lost.txt", "lost-content");
        String hash = DigestUtil.sha256Hex(file.getBytes());
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(sysFileObjectMapper.selectByHash(hash)).thenReturn(null);
        when(sysFileObjectMapper.insert(any(SysFileObject.class)))
                .thenThrow(new DuplicateKeyException("duplicate hash"));

        assertThatThrownBy(() -> fileObjectService.saveOrReuse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.UPLOAD_FILE_EXCEPTION);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("保存文件：读取上传文件失败时转换为上传业务异常")
    void saveOrReuse_readFailureThrowsUploadException() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(file.getBytes()).thenThrow(new IOException("broken file"));

        assertThatThrownBy(() -> fileObjectService.saveOrReuse(file))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.UPLOAD_FILE_EXCEPTION);
    }

    @Test
    @DisplayName("引用计数：空文件和空指纹不访问 Mapper")
    void referenceOperations_ignoreNullAndBlankInput() {
        fileObjectService.decreaseReferenceOrRemove((SysFile) null);
        fileObjectService.decreaseReferenceOrRemove((StoredFileObjectBO) null);
        fileObjectService.increaseReference(" ");

        verifyNoInteractions(sysFileObjectMapper, minioClient);
    }

    @Test
    @DisplayName("引用计数：非空指纹不存在时抛资源不存在")
    void increaseReference_missingObjectThrows() {
        when(sysFileObjectMapper.increaseReference("missing-hash")).thenReturn(0);

        assertThatThrownBy(() -> fileObjectService.increaseReference("missing-hash"))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("引用计数：最后一个引用删除数据库记录和 MinIO 对象")
    void decreaseReferenceOrRemove_lastReferenceDeletesMinioObject() throws Exception {
        SysFile file = sysFile("20260605/last.txt", "hash-last", 16L, "last.txt");
        SysFileObject object = fileObject("hash-last", file.getFilePath(), 1);
        when(sysFileObjectMapper.selectByHash("hash-last")).thenReturn(object);
        when(sysFileObjectMapper.decreaseReferenceIfShared("hash-last")).thenReturn(0);
        when(sysFileObjectMapper.deleteIfLastReference("hash-last")).thenReturn(1);

        fileObjectService.decreaseReferenceOrRemove(file);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("引用计数：数据库记录缺失时按传入对象路径清理孤儿 MinIO 对象")
    void decreaseReferenceOrRemove_missingRecordRemovesSuppliedObjectPath() throws Exception {
        StoredFileObjectBO object = storedObject("hash-missing", "20260605/orphan.txt");
        when(sysFileObjectMapper.selectByHash("hash-missing")).thenReturn(null);

        fileObjectService.decreaseReferenceOrRemove(object);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("引用计数：删除 MinIO 对象失败时转换为删除业务异常")
    void decreaseReferenceOrRemove_removeMinioFailureThrows() throws Exception {
        StoredFileObjectBO object = storedObject(null, "20260605/remove-fail.txt");
        doThrow(new IllegalStateException("remove failed"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> fileObjectService.decreaseReferenceOrRemove(object))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DELETE_FILE_EXCEPTION);
    }

    @Test
    @DisplayName("下载文件：写响应头并按当前用户限速拷贝")
    void writeToResponse_setsHeadersAndDelegatesThrottle() throws Exception {
        SysFile file = sysFile("20260605/demo.txt", "hash-demo", 4L, "demo file.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObject("demo"));

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(7L);

            fileObjectService.writeToResponse(file, response);
        }

        assertThat(response.getContentType()).isEqualTo("text/plain");
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment; filename=\"demo%20file.txt\"");
        assertThat(response.getContentLengthLong()).isEqualTo(4L);
        verify(downloadThrottleSupport).throttledCopy(
                any(), any(OutputStream.class), eq(4L), eq(7L), eq(quotaService));
    }

    @Test
    @DisplayName("分享下载：按匿名分享限速策略写出响应")
    void writeSharedToResponse_usesSharedDownloadThrottle() throws Exception {
        SysFile file = sysFile("20260605/shared.txt", "hash-shared", 6L, "shared.txt");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObject("shared"));

        fileObjectService.writeSharedToResponse(file, response);

        assertThat(response.getContentType()).isEqualTo("text/plain");
        verify(downloadThrottleSupport).throttledCopyForSharedDownload(
                any(), any(OutputStream.class), eq(6L));
    }

    @Test
    @DisplayName("获取文件流：返回 MinIO 输入流")
    void getFileStream_returnsMinioStream() throws Exception {
        SysFile file = sysFile("20260605/stream.txt", "hash-stream", 6L, "stream.txt");
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObject("stream"));

        byte[] bytes = fileObjectService.getFileStream(file).readAllBytes();

        assertThat(new String(bytes)).isEqualTo("stream");
    }

    @Test
    @DisplayName("获取文件流：MinIO 异常转换为下载业务异常")
    void getFileStream_minioFailureThrowsBusinessException() throws Exception {
        SysFile file = sysFile("20260605/missing.txt", "hash-missing", 1L, "missing.txt");
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new IllegalStateException("missing object"));

        assertThatThrownBy(() -> fileObjectService.getFileStream(file))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DOWNLOAD_FILE_EXCEPTION);
    }

    @Test
    @DisplayName("预签名链接：返回 MinIO 生成的下载地址")
    void getPresignedDownloadUrl_returnsMinioUrl() throws Exception {
        SysFile file = sysFile("20260605/signed.txt", "hash-signed", 1L, "signed.txt");
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://signed-url");

        String url = fileObjectService.getPresignedDownloadUrl(file);

        assertThat(url).isEqualTo("http://signed-url");
    }

    @Test
    @DisplayName("预签名链接：MinIO 异常转换为下载业务异常")
    void getPresignedDownloadUrl_minioFailureThrowsBusinessException() throws Exception {
        SysFile file = sysFile("20260605/fail.txt", "hash-fail", 1L, "fail.txt");
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new IllegalStateException("minio unavailable"));

        assertThatThrownBy(() -> fileObjectService.getPresignedDownloadUrl(file))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DOWNLOAD_FILE_EXCEPTION);
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

    private static StoredFileObjectBO storedObject(String hash, String objectPath) {
        StoredFileObjectBO object = new StoredFileObjectBO();
        object.setFileHash(hash);
        object.setObjectPath(objectPath);
        object.setFileUrl(ENDPOINT + "/" + BUCKET + "/" + objectPath);
        return object;
    }

    private static SysFile sysFile(String objectPath, String hash, Long fileSize, String name) {
        SysFile file = new SysFile();
        file.setOriginalName(name);
        file.setFilePath(objectPath);
        file.setFileHash(hash);
        file.setFileSize(fileSize);
        file.setMimeType("text/plain");
        return file;
    }

    private static GetObjectResponse getObject(String content) {
        return new GetObjectResponse(
                null, BUCKET, null, "object", new ByteArrayInputStream(content.getBytes()));
    }
}
