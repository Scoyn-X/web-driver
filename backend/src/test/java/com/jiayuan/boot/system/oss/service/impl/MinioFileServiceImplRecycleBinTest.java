package com.jiayuan.boot.system.oss.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.mapper.SysFileObjectMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;
import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.user.model.bo.UserBriefBO;
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
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MinioFileServiceImpl} 回收站相关方法单元测试（Bonus 4.3）。
 * <p>
 * 仅覆盖 {@code listRecycleBin / restoreFromRecycleBin / permanentlyDelete} 三个新接口
 * 与其辅助方法 {@code loadRecycledFileOwnedByCurrentUser} 的关键分支。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MinioFileServiceImpl 回收站单元测试")
class MinioFileServiceImplRecycleBinTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FILE_ID = 100L;
    private static final Long DIR_ID = 200L;
    private static final Long CHILD_ID = 201L;

    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private QuotaService quotaService;
    @Mock private SysFileObjectMapper sysFileObjectMapper;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private MinioClient minioClient;

    @InjectMocks
    private MinioFileServiceImpl service;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeAll
    static void initMybatisPlusLambda() {
        // MyBatis-Plus 的 LambdaQueryWrapper/LambdaUpdateWrapper 需要先注册实体 TableInfo
        // 才能解析 method reference；单元测试没加载 Spring 上下文，手动初始化。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysFile.class);
        TableInfoHelper.initTableInfo(assistant, SysFileObject.class);
    }

    @BeforeEach
    void setUp() {
        // 非 final 字段（@Data 提供 setter）需手动注入；@InjectMocks 对非 final 字段
        // 的注入在此类的某些字段上不稳定，显式调用更可靠。
        service.setBucketName("test-bucket");
        service.setEndpoint("http://localhost:9000");
        service.setMinioClient(minioClient);

        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(CURRENT_USER_ID);
        when(sysFileConverter.toRecycleBinItemVO(any(SysFile.class), anyString()))
                .thenAnswer(inv -> {
                    SysFile file = inv.getArgument(0);
                    RecycleBinItemResponseVO vo = new RecycleBinItemResponseVO();
                    vo.setId(file.getId());
                    vo.setOriginalName(file.getOriginalName());
                    vo.setFileSize(file.getFileSize());
                    vo.setIsDirectory(file.getIsDirectory());
                    vo.setDeletedAt(file.getDeletedAt());
                    vo.setPath(inv.getArgument(1));
                    return vo;
                });
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ==================== listRecycleBin ====================

    @Nested
    @DisplayName("listRecycleBin")
    class ListRecycleBinTests {

        @Test
        @DisplayName("根级文件：path = /<name>，不查祖先名字")
        void list_rootLevel_pathHasNoAncestor() {
            SysFile f = recycledFile(FILE_ID, "a.txt");
            f.setFullPath(String.valueOf(FILE_ID));   // 仅自身
            f.setFileSize(99L);
            f.setUpdateTime(LocalDateTime.of(2026, 4, 16, 10, 0));
            when(sysFileMapper.selectPersonalTrashRoots(CURRENT_USER_ID)).thenReturn(List.of(f));

            List<RecycleBinItemResponseVO> result = service.listRecycleBin();

            assertThat(result).hasSize(1);
            RecycleBinItemResponseVO vo = result.get(0);
            assertThat(vo.getId()).isEqualTo(FILE_ID);
            assertThat(vo.getOriginalName()).isEqualTo("a.txt");
            assertThat(vo.getPath()).isEqualTo("/a.txt");
            assertThat(vo.getFileSize()).isEqualTo(99L);
            assertThat(vo.getIsDirectory()).isZero();
            assertThat(vo.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 4, 16, 10, 0));
            // 根级无祖先，不应触发批量名字查询
            verify(sysFileMapper, never()).selectFilesInSpaceByIds(anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("嵌套文件：path 拼为 /A/B/<name>，单次批量查祖先名字")
        void list_nested_pathBuildsFromAncestors() {
            SysFile a = dirEntity(10L, "A");
            SysFile b = dirEntity(11L, "B");
            SysFile f = recycledFile(FILE_ID, "baz.txt");
            f.setFullPath("10,11," + FILE_ID);
            f.setFileSize(50L);

            when(sysFileMapper.selectPersonalTrashRoots(CURRENT_USER_ID)).thenReturn(List.of(f));
            when(sysFileMapper.selectFilesInSpaceByIds(anyString(), anyLong(), any())).thenReturn(List.of(a, b));

            List<RecycleBinItemResponseVO> result = service.listRecycleBin();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPath()).isEqualTo("/A/B/baz.txt");
            verify(sysFileMapper).selectFilesInSpaceByIds(anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("祖先名字缺失：用 ? 占位，不抛异常")
        void list_missingAncestorName_usesPlaceholder() {
            SysFile a = dirEntity(10L, "A");
            SysFile f = recycledFile(FILE_ID, "baz.txt");
            // 11 这个祖先在批量查询中找不到（被独立永久删了）
            f.setFullPath("10,11," + FILE_ID);

            when(sysFileMapper.selectPersonalTrashRoots(CURRENT_USER_ID)).thenReturn(List.of(f));
            when(sysFileMapper.selectFilesInSpaceByIds(anyString(), anyLong(), any())).thenReturn(List.of(a));

            List<RecycleBinItemResponseVO> result = service.listRecycleBin();

            assertThat(result.get(0).getPath()).isEqualTo("/A/?/baz.txt");
        }

        @Test
        @DisplayName("无回收站文件返回空列表，不查任何 sys_file")
        void list_empty() {
            when(sysFileMapper.selectPersonalTrashRoots(CURRENT_USER_ID)).thenReturn(Collections.emptyList());

            assertThat(service.listRecycleBin()).isEmpty();
            verify(sysFileMapper, never()).selectFilesInSpaceByIds(anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("删除人信息：命中用户基础信息时填充账号，缺失时显示未知")
        void list_deleterBriefs_enrichKnownAndFallbackUnknown() {
            SysFile known = recycledFile(FILE_ID, "known.txt");
            known.setFullPath(String.valueOf(FILE_ID));
            known.setDeletedBy(77L);
            known.setExpireAt(LocalDateTime.of(2026, 6, 10, 10, 0));
            SysFile unknown = recycledFile(CHILD_ID, "unknown.txt");
            unknown.setFullPath(String.valueOf(CHILD_ID));
            unknown.setDeletedBy(88L);
            unknown.setExpireAt(LocalDateTime.of(2026, 6, 11, 10, 0));
            UserBriefBO brief = new UserBriefBO();
            brief.setUserId(77L);
            brief.setAccountId(7700L);
            brief.setAccountName("lin");
            brief.setNickname("林锦谦");
            when(sysFileMapper.selectPersonalTrashRoots(CURRENT_USER_ID)).thenReturn(List.of(known, unknown));
            when(sysUserMapper.selectUserBriefByIds(any())).thenReturn(List.of(brief));

            List<RecycleBinItemResponseVO> result = service.listRecycleBin();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getDeletedByUserId()).isEqualTo(77L);
            assertThat(result.get(0).getDeletedByAccountId()).isEqualTo(7700L);
            assertThat(result.get(0).getDeletedByAccountName()).isEqualTo("lin");
            assertThat(result.get(0).getDeletedByName()).isEqualTo("林锦谦");
            assertThat(result.get(0).getExpireAt()).isEqualTo(LocalDateTime.of(2026, 6, 10, 10, 0));
            assertThat(result.get(1).getDeletedByUserId()).isEqualTo(88L);
            assertThat(result.get(1).getDeletedByName()).isEqualTo("未知");
            assertThat(result.get(1).getDeletedByAccountId()).isNull();
            assertThat(result.get(1).getExpireAt()).isEqualTo(LocalDateTime.of(2026, 6, 11, 10, 0));
        }
    }

    private SysFile dirEntity(Long id, String name) {
        SysFile d = new SysFile();
        d.setId(id);
        d.setOriginalName(name);
        d.setIsDirectory(1);
        return d;
    }

    // ==================== restoreFromRecycleBin ====================

    @Nested
    @DisplayName("restoreFromRecycleBin")
    class RestoreTests {

        @Test
        @DisplayName("恢复单个文件：UPDATE in_recycle_bin=0 + 校验配额 + 重新占用配额")
        void restore_singleFile() {
            SysFile f = recycledFile(FILE_ID, "a.txt");
            f.setFileSize(100L);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);
            when(sysFileMapper.restoreTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID, 0L, "", "a.txt"))
                    .thenReturn(1);

            service.restoreFromRecycleBin(FILE_ID);

            verify(sysFileMapper).restoreTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID, 0L, "", "a.txt");
            verify(quotaService).checkQuota(CURRENT_USER_ID, 100L);
            verify(quotaService).increaseUsedSpace(CURRENT_USER_ID, 100L);
        }

        @Test
        @DisplayName("恢复目录：递归收集后代一并 UPDATE + 重新占用配额")
        void restore_directoryRecursive() {
            SysFile dir = recycledDir(DIR_ID);
            SysFile child = recycledFile(CHILD_ID, "child.txt");
            child.setFileSize(50L);
            child.setParentId(DIR_ID);
            child.setRecycleRoot(0);

            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, DIR_ID)).thenReturn(dir);
            when(sysFileMapper.selectDescendantsInSpace(anyString(), anyLong(), anyLong())).thenReturn(List.of(child));
            when(sysFileMapper.restoreTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, DIR_ID, 0L, "", "dir"))
                    .thenReturn(1);

            service.restoreFromRecycleBin(DIR_ID);

            verify(sysFileMapper).restoreTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, DIR_ID, 0L, "", "dir");
            verify(quotaService).checkQuota(CURRENT_USER_ID, 50L);
            verify(quotaService).increaseUsedSpace(CURRENT_USER_ID, 50L);
        }

        @Test
        @DisplayName("文件不存在：抛 USER_RESOURCE_NOT_FOUND")
        void restore_notFound_throws() {
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(null);

            assertThatThrownBy(() -> service.restoreFromRecycleBin(FILE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("非 owner：抛 ACCESS_UNAUTHORIZED")
        void restore_notOwner_throws() {
            SysFile f = recycledFile(FILE_ID, "a.txt");
            f.setUserId(OTHER_USER_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);

            assertThatThrownBy(() -> service.restoreFromRecycleBin(FILE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.ACCESS_UNAUTHORIZED);
        }

        @Test
        @DisplayName("文件不在回收站：抛 USER_REQUEST_PARAMETER_ERROR")
        void restore_notInRecycleBin_throws() {
            SysFile f = normalFile(FILE_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);

            assertThatThrownBy(() -> service.restoreFromRecycleBin(FILE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        }

        @Test
        @DisplayName("非回收站根节点（随父目录一同删除的子项）：拒绝单独恢复，避免孤儿引用")
        void restore_nonRoot_throws() {
            SysFile child = recycledFile(CHILD_ID, "child.txt");
            child.setRecycleRoot(0); // 随父目录一同删除的后代
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, CHILD_ID)).thenReturn(child);

            assertThatThrownBy(() -> service.restoreFromRecycleBin(CHILD_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
            verify(sysFileMapper, never()).update(any(), any());
        }
    }

    // ==================== permanentlyDelete ====================

    @Nested
    @DisplayName("permanentlyDelete")
    class PermanentDeleteTests {

        @Test
        @DisplayName("永久删文件：ref_count-- + soft delete（配额已在移入回收站时释放）")
        void permanentDelete_file_decrementsRef() {
            SysFile f = recycledFile(FILE_ID, "a.txt");
            f.setFileHash("abc123");
            f.setFileSize(58L);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);
            when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID))
                    .thenReturn(1);

            // 模拟 sys_file_object 存在且 ref_count=2 → 减为 1，不删物理对象
            SysFileObject obj = new SysFileObject();
            obj.setId(500L);
            obj.setFileHash("abc123");
            obj.setObjectPath("20260414/abc.txt");
            obj.setRefCount(2);
            when(sysFileObjectMapper.selectOne(any())).thenReturn(obj);

            service.permanentlyDelete(FILE_ID);

            // ref 减为 1（>0）→ 应 UPDATE 而非 DELETE，且 MinIO 对象保留
            verify(sysFileObjectMapper).update(any(), any());
            verify(sysFileObjectMapper, never()).deleteById(anyLong());
            verify(sysFileMapper).permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID);
            // 配额已在 deleteFileById 移入回收站时释放，permanentlyDelete 不再处理配额
            verify(quotaService, never()).decreaseUsedSpace(anyLong(), anyLong());
        }

        @Test
        @DisplayName("永久删最后一个引用：ref=0 → 删 MinIO + 删 sys_file_object")
        void permanentDelete_lastRef_removesMinio() throws Exception {
            SysFile f = recycledFile(FILE_ID, "a.txt");
            f.setFileHash("xyz789");
            f.setFileSize(30L);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);
            when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID))
                    .thenReturn(1);

            SysFileObject obj = new SysFileObject();
            obj.setId(600L);
            obj.setFileHash("xyz789");
            obj.setObjectPath("20260414/xyz.txt");
            obj.setRefCount(1); // 减完为 0
            when(sysFileObjectMapper.selectOne(any())).thenReturn(obj);

            service.permanentlyDelete(FILE_ID);

            // 最后一个引用：应该删 MinIO + 删 sys_file_object
            verify(minioClient).removeObject(any());
            verify(sysFileObjectMapper).deleteById(600L);
            verify(sysFileMapper).permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID);
            // 配额已在 deleteFileById 移入回收站时释放，permanentlyDelete 不再处理配额
            verify(quotaService, never()).decreaseUsedSpace(anyLong(), anyLong());
        }

        @Test
        @DisplayName("永久删 Lab1 旧文件：无 hash 时按 filePath 删除 MinIO")
        void permanentDelete_legacyFileWithoutHash_removesByFilePath() throws Exception {
            SysFile f = recycledFile(FILE_ID, "legacy.txt");
            f.setFilePath("legacy/path.txt");
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);
            when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID))
                    .thenReturn(1);

            service.permanentlyDelete(FILE_ID);

            verify(minioClient).removeObject(any());
            verify(sysFileObjectMapper, never()).selectOne(any());
            verify(sysFileMapper).permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID);
        }

        @Test
        @DisplayName("永久删缺失对象记录：有 hash 无 object 时回退按 filePath 删除")
        void permanentDelete_missingObjectFallback_removesByFilePath() throws Exception {
            SysFile f = recycledFile(FILE_ID, "orphan.txt");
            f.setFileHash("orphan_hash");
            f.setFilePath("orphan/path.txt");
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);
            when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID))
                    .thenReturn(1);
            when(sysFileObjectMapper.selectOne(any())).thenReturn(null);

            service.permanentlyDelete(FILE_ID);

            verify(minioClient).removeObject(any());
            verify(sysFileObjectMapper, never()).deleteById(anyLong());
            verify(sysFileMapper).permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID);
        }

        @Test
        @DisplayName("永久删缺失对象记录且无 filePath：只删除元数据树")
        void permanentDelete_missingObjectAndBlankPath_skipsMinioRemoval() throws Exception {
            SysFile f = recycledFile(FILE_ID, "metadata-only.txt");
            f.setFileHash("metadata_hash");
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);
            when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID))
                    .thenReturn(1);
            when(sysFileObjectMapper.selectOne(any())).thenReturn(null);

            service.permanentlyDelete(FILE_ID);

            verify(minioClient, never()).removeObject(any());
            verify(sysFileMapper).permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, FILE_ID);
        }

        @Test
        @DisplayName("永久删目录：递归处理所有后代文件（不处理配额）")
        void permanentDelete_directory_recursive() {
            SysFile dir = recycledDir(DIR_ID);
            SysFile child = recycledFile(CHILD_ID, "child.txt");
            child.setFileSize(25L);
            child.setFileHash("child_hash");
            child.setRecycleRoot(0);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, DIR_ID)).thenReturn(dir);
            when(sysFileMapper.selectDescendantsInSpace(anyString(), anyLong(), anyLong())).thenReturn(List.of(child));
            when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, DIR_ID))
                    .thenReturn(1);

            SysFileObject obj = new SysFileObject();
            obj.setId(700L);
            obj.setFileHash("child_hash");
            obj.setObjectPath("20260414/child.txt");
            obj.setRefCount(1);
            when(sysFileObjectMapper.selectOne(any())).thenReturn(obj);

            service.permanentlyDelete(DIR_ID);

            verify(sysFileMapper).permanentlyDeleteTrashTreeInSpace("PERSONAL", CURRENT_USER_ID, DIR_ID);
            // 配额已在 deleteFileById 移入回收站时释放，permanentlyDelete 不再处理配额
            verify(quotaService, never()).decreaseUsedSpace(anyLong(), anyLong());
        }

        @Test
        @DisplayName("文件不在回收站：拒绝永久删")
        void permanentDelete_notInRecycleBin_throws() {
            SysFile f = normalFile(FILE_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);

            assertThatThrownBy(() -> service.permanentlyDelete(FILE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
            verify(sysFileMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("非 owner：拒绝永久删")
        void permanentDelete_notOwner_throws() {
            SysFile f = recycledFile(FILE_ID, "a.txt");
            f.setUserId(OTHER_USER_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);

            assertThatThrownBy(() -> service.permanentlyDelete(FILE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.ACCESS_UNAUTHORIZED);
        }

        @Test
        @DisplayName("非回收站根节点：拒绝单独永久删除，需对父目录整体操作")
        void permanentDelete_nonRoot_throws() {
            SysFile child = recycledFile(CHILD_ID, "child.txt");
            child.setRecycleRoot(0);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, CHILD_ID)).thenReturn(child);

            assertThatThrownBy(() -> service.permanentlyDelete(CHILD_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
            verify(sysFileMapper, never()).deleteById(anyLong());
        }
    }

    // ==================== helpers ====================

    private SysFile recycledFile(Long id, String name) {
        SysFile f = new SysFile();
        f.setId(id);
        f.setUserId(CURRENT_USER_ID);
        f.setSpaceType("PERSONAL");
        f.setSpaceId(CURRENT_USER_ID);
        f.setUploaderId(CURRENT_USER_ID);
        f.setOriginalName(name);
        f.setIsDirectory(0);
        f.setInRecycleBin(1);
        f.setRecycleRoot(1);
        return f;
    }

    private SysFile recycledDir(Long id) {
        SysFile d = new SysFile();
        d.setId(id);
        d.setUserId(CURRENT_USER_ID);
        d.setSpaceType("PERSONAL");
        d.setSpaceId(CURRENT_USER_ID);
        d.setUploaderId(CURRENT_USER_ID);
        d.setOriginalName("dir");
        d.setIsDirectory(1);
        d.setInRecycleBin(1);
        d.setRecycleRoot(1);
        return d;
    }

    private SysFile normalFile(Long id) {
        SysFile f = new SysFile();
        f.setId(id);
        f.setUserId(CURRENT_USER_ID);
        f.setSpaceType("PERSONAL");
        f.setSpaceId(CURRENT_USER_ID);
        f.setUploaderId(CURRENT_USER_ID);
        f.setOriginalName("normal.txt");
        f.setIsDirectory(0);
        f.setInRecycleBin(0);
        return f;
    }
}
