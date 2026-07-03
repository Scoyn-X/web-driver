package com.jiayuan.boot.system.oss.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileObjectMapper;
import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.oss.utils.DownloadThrottleSupport;
import com.jiayuan.boot.system.oss.utils.FileObjectReferenceSupport;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件物理对象服务实现。
 *
 * @author charleslam
 * @since 2026/05/16
 */
@Component
@ConditionalOnProperty(value = "oss.type", havingValue = "minio")
@ConfigurationProperties(prefix = "oss.minio")
@RequiredArgsConstructor
@Data
@Slf4j
public class MinioFileObjectServiceImpl implements FileObjectService {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String customDomain;

    private MinioClient minioClient;

    private final SysFileObjectMapper sysFileObjectMapper;
    private final QuotaService quotaService;
    private final SysFileConverter sysFileConverter;
    private final DownloadThrottleSupport downloadThrottleSupport;

    /**
     * 初始化 MinIO 客户端。
     */
    @PostConstruct
    public void init() {
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 保存上传文件或复用已有物理对象。
     */
    @Override
    public StoredFileObjectBO saveOrReuse(MultipartFile file) {
        createBucketIfAbsent(bucketName);
        try {
            byte[] bytes = file.getBytes();
            String fileHash = DigestUtil.sha256Hex(bytes);
            SysFileObject existing = sysFileObjectMapper.selectByHash(fileHash);
            String objectPath;
            if (existing != null) {
                objectPath = existing.getObjectPath();
                increaseActiveReference(fileHash);
            } else {
                String suffix = FileUtil.getSuffix(file.getOriginalFilename());
                objectPath = buildObjectPath(suffix);
                try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectPath)
                            .contentType(file.getContentType())
                            .stream(inputStream, bytes.length, -1)
                            .build());
                }
                objectPath = insertFileObject(fileHash, objectPath, (long) bytes.length);
            }
            return buildStoredFileObject(file, fileHash, objectPath);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("保存文件物理对象失败", e);
            throw new BusinessException(ResultCode.UPLOAD_FILE_EXCEPTION, e.getMessage());
        }
    }

    /**
     * 增加文件物理对象引用计数。
     */
    @Override
    public void increaseReference(String fileHash) {
        if (StrUtil.isNotBlank(fileHash)) {
            increaseActiveReference(fileHash);
        }
    }

    /**
     * 减少文件物理对象引用，最后一个引用会删除对象。
     */
    @Override
    public void decreaseReferenceOrRemove(SysFile file) {
        if (file == null) {
            return;
        }
        decreaseReferenceOrRemove(file.getFileHash(), file.getFilePath());
    }

    /**
     * 按物理对象信息减少引用计数。
     */
    @Override
    public void decreaseReferenceOrRemove(StoredFileObjectBO object) {
        if (object == null) {
            return;
        }
        decreaseReferenceOrRemove(object.getFileHash(), object.getObjectPath());
    }

    private void decreaseReferenceOrRemove(String fileHash, String objectPath) {
        if (StrUtil.isBlank(fileHash)) {
            if (StrUtil.isNotBlank(objectPath)) {
                removeMinioObject(objectPath);
            }
            return;
        }
        SysFileObject object = sysFileObjectMapper.selectByHash(fileHash);
        if (object == null) {
            if (StrUtil.isNotBlank(objectPath)) {
                removeMinioObject(objectPath);
            }
            return;
        }
        FileObjectReferenceSupport.decreaseOrRemove(fileHash, object, sysFileObjectMapper, this::removeMinioObject);
    }

    /**
     * 将 MinIO 文件流写入 HTTP 响应。
     */
    @Override
    public void writeToResponse(SysFile file, HttpServletResponse response) {
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(file.getFilePath())
                        .build())) {
            writeDownloadHeaders(file, response);

            downloadThrottleSupport.throttledCopy(
                    inputStream, response.getOutputStream(),
                    file.getFileSize(), SecurityUtils.getCurrentUserId(), quotaService);
        } catch (Exception e) {
            log.error("写入文件响应失败", e);
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "下载文件失败: " + e.getMessage());
        }
    }

    /**
     * 将匿名分享文件流按普通用户限速策略写入 HTTP 响应。
     */
    @Override
    public void writeSharedToResponse(SysFile file, HttpServletResponse response) {
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(file.getFilePath())
                        .build())) {
            writeDownloadHeaders(file, response);

            downloadThrottleSupport.throttledCopyForSharedDownload(
                    inputStream, response.getOutputStream(), file.getFileSize());
        } catch (Exception e) {
            log.error("写入分享文件响应失败", e);
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "下载分享文件失败: " + e.getMessage());
        }
    }

    private void writeDownloadHeaders(SysFile file, HttpServletResponse response) {
        response.setContentType(file.getMimeType() != null ? file.getMimeType() : "application/octet-stream");
        String encodedFileName = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
        response.setContentLengthLong(file.getFileSize() == null ? 0L : file.getFileSize());
    }

    /**
     * 获取文件的 MinIO 输入流。
     */
    @Override
    public InputStream getFileStream(SysFile file) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(file.getFilePath())
                            .build());
        } catch (Exception e) {
            log.error("获取文件流失败: {}", file.getFilePath(), e);
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "获取文件流失败: " + e.getMessage());
        }
    }

    /**
     * 生成文件预签名下载 URL。
     */
    @Override
    public String getPresignedDownloadUrl(SysFile file) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucketName)
                            .object(file.getFilePath())
                            .method(Method.GET)
                            .expiry(5, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("生成预签名 URL 失败", e);
            throw new BusinessException(ResultCode.DOWNLOAD_FILE_EXCEPTION, "生成下载链接失败: " + e.getMessage());
        }
    }

    private String insertFileObject(String fileHash, String objectPath, Long fileSize) {
        SysFileObject fileObject = new SysFileObject();
        fileObject.setFileHash(fileHash);
        fileObject.setObjectPath(objectPath);
        fileObject.setFileSize(fileSize);
        fileObject.setRefCount(1);
        try {
            sysFileObjectMapper.insert(fileObject);
            return objectPath;
        } catch (DuplicateKeyException race) {
            log.warn("并发保存相同文件指纹，回退为引用计数: {}", fileHash);
            removeMinioObject(objectPath);
            SysFileObject winner = sysFileObjectMapper.selectByHash(fileHash);
            if (winner == null) {
                throw new BusinessException(ResultCode.UPLOAD_FILE_EXCEPTION, "文件指纹冲突记录不存在");
            }
            increaseActiveReference(fileHash);
            return winner.getObjectPath();
        }
    }

    private void increaseActiveReference(String fileHash) {
        if (sysFileObjectMapper.increaseReference(fileHash) == 0) {
            throw new BusinessException(ResultCode.USER_RESOURCE_NOT_FOUND, "文件物理对象不存在");
        }
    }

    private StoredFileObjectBO buildStoredFileObject(MultipartFile file, String fileHash, String objectPath) {
        return sysFileConverter.toStoredFileObjectBO(file, fileHash, objectPath, buildFileUrl(objectPath));
    }

    private String buildObjectPath(String suffix) {
        String dateFolder = DateUtil.format(LocalDateTime.now(), "yyyyMMdd");
        String ext = StrUtil.isNotBlank(suffix) ? "." + suffix : "";
        return dateFolder + "/" + IdUtil.simpleUUID() + ext;
    }

    private String buildFileUrl(String objectPath) {
        String base = StrUtil.isBlank(customDomain) ? endpoint : customDomain;
        return base + "/" + bucketName + "/" + objectPath;
    }

    private void removeMinioObject(String objectPath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectPath)
                    .build());
        } catch (Exception e) {
            log.error("删除 MinIO 对象失败: {}", objectPath, e);
            throw new BusinessException(ResultCode.DELETE_FILE_EXCEPTION, "删除 MinIO 文件失败: " + e.getMessage());
        }
    }

    @SneakyThrows
    private void createBucketIfAbsent(String bucketName) {
        BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(bucketName).build();
        if (!minioClient.bucketExists(bucketExistsArgs)) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucketName)
                    .config(publicBucketPolicy(bucketName))
                    .build());
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
}
