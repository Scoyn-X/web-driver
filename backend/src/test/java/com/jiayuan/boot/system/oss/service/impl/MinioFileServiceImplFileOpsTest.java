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
import com.jiayuan.boot.system.oss.utils.DownloadThrottleSupport;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import io.minio.MinioClient;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinioFileServiceImpl 文件操作单元测试（复制、移动、删除 + 配额联动）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MinioFileServiceImpl 文件操作单元测试")
class MinioFileServiceImplFileOpsTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private QuotaService quotaService;
    @Mock private SysFileObjectMapper sysFileObjectMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private SystemConfigProperties configProperties;
    @Mock private DownloadThrottleSupport downloadThrottleSupport;
    @Mock private MinioClient minioClient;

    @InjectMocks
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
        service.setBucketName("test-bucket");
        service.setEndpoint("http://localhost:9000");
        service.setMinioClient(minioClient);
        when(configProperties.getTrashRetentionSeconds()).thenReturn(259200L);

        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(CURRENT_USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ==================== copyFile ====================

    @Nested
    @DisplayName("copyFile")
    class CopyFileTests {

        @Test
        @DisplayName("正常复制文件：新增记录 + ref_count++ + 配额扣减")
        void copy_success() {
            SysFile source = buildFile(100L, CURRENT_USER_ID, "report.pdf", 1024L);
            source.setFileHash("hash123");
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 100L)).thenReturn(source);
            when(sysFileMapper.existsNameInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(false);

            FileInfoResponseVO vo = new FileInfoResponseVO();
            vo.setId(101L);
            when(sysFileConverter.toFileInfoVO(any())).thenReturn(vo);

            FileInfoResponseVO result = service.copyFile(100L, 0L);

            assertThat(result.getId()).isEqualTo(101L);
            verify(quotaService).checkQuota(CURRENT_USER_ID, 1024L);
            verify(quotaService).increaseUsedSpace(CURRENT_USER_ID, 1024L);
            verify(sysFileObjectMapper).update(any(), any()); // ref_count++
            verify(sysFileMapper).insert(any());
        }

        @Test
        @DisplayName("复制他人文件：抛 ACCESS_UNAUTHORIZED")
        void copy_otherUser_throws() {
            SysFile source = buildFile(100L, OTHER_USER_ID, "report.pdf", 1024L);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 100L)).thenReturn(source);

            assertThatThrownBy(() -> service.copyFile(100L, 0L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.ACCESS_UNAUTHORIZED);

            verify(sysFileMapper, never()).insert(any());
        }

        @Test
        @DisplayName("团队文件上传者不能通过个人复制入口复制")
        void copy_teamFileUploadedByCurrentUser_throws() {
            SysFile source = buildFile(100L, CURRENT_USER_ID, "team-report.pdf", 1024L);
            source.setSpaceType("TEAM");
            source.setSpaceId(88L);
            source.setUploaderId(CURRENT_USER_ID);
            when(sysFileMapper.selectById(100L)).thenReturn(source);

            assertThatThrownBy(() -> service.copyFile(100L, 0L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);

            verify(sysFileMapper, never()).insert(any());
        }

        @Test
        @DisplayName("配额不足：抛 USER_QUOTA_EXHAUSTED，不产生记录")
        void copy_quotaExhausted_throws() {
            SysFile source = buildFile(100L, CURRENT_USER_ID, "big.zip", 999999999L);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 100L)).thenReturn(source);
            org.mockito.Mockito.doThrow(new BusinessException(ResultCode.USER_QUOTA_EXHAUSTED))
                    .when(quotaService).checkQuota(CURRENT_USER_ID, 999999999L);

            assertThatThrownBy(() -> service.copyFile(100L, 0L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_QUOTA_EXHAUSTED);

            verify(sysFileMapper, never()).insert(any());
        }

        @Test
        @DisplayName("同名自动重命名：目标目录已有同名文件")
        void copy_duplicateName_autoRename() {
            SysFile source = buildFile(100L, CURRENT_USER_ID, "report.pdf", 1024L);
            source.setFileHash("hash123");
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 100L)).thenReturn(source);
            when(sysFileMapper.existsNameInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(true)   // report.pdf 已存在
                    .thenReturn(false); // report(1).pdf 不存在
            when(sysFileMapper.selectNamesInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(List.of("report.pdf"));

            FileInfoResponseVO vo = new FileInfoResponseVO();
            vo.setId(101L);
            when(sysFileConverter.toFileInfoVO(any())).thenReturn(vo);

            service.copyFile(100L, 0L);

            verify(sysFileMapper).insert(any());
        }
    }

    // ==================== moveFile ====================

    @Nested
    @DisplayName("moveFile")
    class MoveFileTests {

        @Test
        @DisplayName("正常移动文件到根目录")
        void move_success() {
            SysFile source = buildFile(100L, CURRENT_USER_ID, "report.pdf", 1024L);
            source.setParentId(5L);
            source.setInRecycleBin(0);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 100L)).thenReturn(source);
            when(sysFileMapper.existsNameInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(false);

            FileInfoResponseVO vo = new FileInfoResponseVO();
            vo.setId(100L);
            when(sysFileConverter.toFileInfoVO(any())).thenReturn(vo);

            FileInfoResponseVO result = service.moveFile(100L, 0L);

            assertThat(result.getId()).isEqualTo(100L);
            verify(sysFileMapper).updateById(any());
        }

        @Test
        @DisplayName("移动他人文件：抛 ACCESS_UNAUTHORIZED")
        void move_otherUser_throws() {
            SysFile source = buildFile(100L, OTHER_USER_ID, "report.pdf", 1024L);
            source.setInRecycleBin(0);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 100L)).thenReturn(source);

            assertThatThrownBy(() -> service.moveFile(100L, 0L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.ACCESS_UNAUTHORIZED);
        }

        @Test
        @DisplayName("目标目录下同名冲突：抛 USER_REQUEST_PARAMETER_ERROR")
        void move_duplicateName_throws() {
            SysFile source = buildFile(100L, CURRENT_USER_ID, "report.pdf", 1024L);
            source.setParentId(5L);
            source.setInRecycleBin(0);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 100L)).thenReturn(source);
            when(sysFileMapper.existsNameInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(true);

            assertThatThrownBy(() -> service.moveFile(100L, 0L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        }
    }

    // ==================== deleteFileById ====================

    @Nested
    @DisplayName("deleteFileById")
    class DeleteFileTests {

        @Test
        @DisplayName("删除单个文件：标记回收站 + 释放配额")
        void delete_singleFile_success() {
            SysFile file = buildFile(100L, CURRENT_USER_ID, "a.txt", 500L);
            file.setInRecycleBin(0);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 100L)).thenReturn(file);
            when(sysFileMapper.moveRootToTrashInSpace(eq("PERSONAL"), eq(CURRENT_USER_ID), eq(100L), eq(CURRENT_USER_ID), anyLong())).thenReturn(1);

            boolean result = service.deleteFileById(100L);

            assertThat(result).isTrue();
            verify(sysFileMapper).moveRootToTrashInSpace(eq("PERSONAL"), eq(CURRENT_USER_ID), eq(100L), eq(CURRENT_USER_ID), anyLong());
            verify(quotaService).decreaseUsedSpace(CURRENT_USER_ID, 500L);
        }

        @Test
        @DisplayName("删除目录：递归标记回收站 + 仅目录自身为 recycle_root + 释放所有子文件配额")
        void delete_directory_recursive() {
            SysFile dir = buildFile(200L, CURRENT_USER_ID, "docs", 0L);
            dir.setIsDirectory(1);
            dir.setInRecycleBin(0);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 200L)).thenReturn(dir);

            SysFile child = buildFile(201L, CURRENT_USER_ID, "child.txt", 300L);
            when(sysFileMapper.selectDescendantsInSpace(anyString(), anyLong(), anyLong()))
                    .thenReturn(List.of(child));
            when(sysFileMapper.moveRootToTrashInSpace(eq("PERSONAL"), eq(CURRENT_USER_ID), eq(200L), eq(CURRENT_USER_ID), anyLong())).thenReturn(1);

            service.deleteFileById(200L);

            verify(sysFileMapper).updateDescendantsRecycleStateInSpace(eq("PERSONAL"), eq(CURRENT_USER_ID), eq(200L), anyLong());
            verify(sysFileMapper).moveRootToTrashInSpace(eq("PERSONAL"), eq(CURRENT_USER_ID), eq(200L), eq(CURRENT_USER_ID), anyLong());
            verify(quotaService).decreaseUsedSpace(CURRENT_USER_ID, 300L);
        }

        @Test
        @DisplayName("删除单个文件：自身即为 recycle_root")
        void delete_singleFile_marksAsRoot() {
            SysFile file = buildFile(101L, CURRENT_USER_ID, "a.txt", 100L);
            file.setInRecycleBin(0);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 101L)).thenReturn(file);
            when(sysFileMapper.moveRootToTrashInSpace(eq("PERSONAL"), eq(CURRENT_USER_ID), eq(101L), eq(CURRENT_USER_ID), anyLong())).thenReturn(1);

            service.deleteFileById(101L);

            verify(sysFileMapper).moveRootToTrashInSpace(eq("PERSONAL"), eq(CURRENT_USER_ID), eq(101L), eq(CURRENT_USER_ID), anyLong());
        }

        @Test
        @DisplayName("删除他人文件：抛 ACCESS_UNAUTHORIZED")
        void delete_otherUser_throws() {
            SysFile file = buildFile(100L, OTHER_USER_ID, "a.txt", 500L);
            file.setInRecycleBin(0);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 100L)).thenReturn(file);

            assertThatThrownBy(() -> service.deleteFileById(100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.ACCESS_UNAUTHORIZED);

            verify(quotaService, never()).decreaseUsedSpace(anyLong(), anyLong());
        }

        @Test
        @DisplayName("私密文件上传者不能通过个人删除入口删除")
        void delete_privateFileUploadedByCurrentUser_throws() {
            SysFile file = buildFile(100L, CURRENT_USER_ID, "secret.pdf", 500L);
            file.setSpaceType("PRIVATE");
            file.setSpaceId(CURRENT_USER_ID);
            file.setUploaderId(CURRENT_USER_ID);
            file.setInRecycleBin(0);
            when(sysFileMapper.selectById(100L)).thenReturn(file);

            assertThatThrownBy(() -> service.deleteFileById(100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);

            verify(quotaService, never()).decreaseUsedSpace(anyLong(), anyLong());
        }
    }

    // ==================== helpers ====================

    private SysFile buildFile(Long id, Long userId, String name, Long size) {
        SysFile f = new SysFile();
        f.setId(id);
        f.setUserId(userId);
        f.setSpaceType("PERSONAL");
        f.setSpaceId(userId);
        f.setUploaderId(userId);
        f.setOriginalName(name);
        f.setFileSize(size);
        f.setIsDirectory(0);
        f.setParentId(0L);
        f.setFilePath("20260416/uuid.pdf");
        f.setStoredName("uuid.pdf");
        f.setFileUrl("http://localhost:9000/test-bucket/20260416/uuid.pdf");
        f.setMimeType("application/pdf");
        return f;
    }
}
