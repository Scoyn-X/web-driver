package com.jiayuan.boot.system.privatespace.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.admin.config.SystemConfigProperties;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.oss.config.TrashRetentionProperties;
import com.jiayuan.boot.system.privatespace.converter.PrivateSpaceConverter;
import com.jiayuan.boot.system.privatespace.mapper.PrivateSpaceMapper;
import com.jiayuan.boot.system.privatespace.model.entity.PrivateSpace;
import com.jiayuan.boot.system.privatespace.model.enums.PrivateSpaceState;
import com.jiayuan.boot.system.privatespace.model.vo.PrivatePasswordRequestVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSessionRequestVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSessionResponseVO;
import com.jiayuan.boot.system.privatespace.model.vo.PrivateSpaceStatusResponseVO;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.bo.PrivateFileBuildBO;
import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 私密空间管理服务单元测试。
 *
 * @author charleslam
 * @since 2026/05/18
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrivateSpaceServiceImpl 单元测试")
class PrivateSpaceServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final String RAW_TOKEN = "jwt-token-a";
    private static final String SESSION_KEY = "private-space:session:" + USER_ID + ":" + DigestUtil.sha256Hex(RAW_TOKEN);

    @Mock private PrivateSpaceMapper privateSpaceMapper;
    @Mock private QuotaService quotaService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private PrivateSpaceConverter privateSpaceConverter;
    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private FileObjectService fileObjectService;
    @Mock private HttpServletResponse response;
    @Mock private TrashRetentionProperties trashRetentionProperties;
    @Mock private SystemConfigProperties systemConfigProperties;

    @InjectMocks
    private PrivateSpaceServiceImpl privateSpaceService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + RAW_TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        securityUtilsMock.close();
    }

    @Test
    @DisplayName("未设置密码：返回 DISABLED")
    void getStatus_disabled() {
        PrivateSpaceStatusResponseVO expected = new PrivateSpaceStatusResponseVO();
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(null);
        when(privateSpaceConverter.toStatusResponseVO(PrivateSpaceState.DISABLED, null, null, null))
                .thenReturn(expected);

        PrivateSpaceStatusResponseVO result = privateSpaceService.getStatus();

        assertThat(result).isSameAs(expected);
        verify(stringRedisTemplate).getExpire(SESSION_KEY, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("首次设置密码：插入 BCrypt 哈希")
    void updatePassword_firstSet() {
        PrivatePasswordRequestVO request = testModel(new PrivatePasswordRequestVO(), fields("password", "new-pass"));
        PrivateSpace entity = new PrivateSpace();
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(null);
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new");
        when(privateSpaceConverter.toPrivateSpace(USER_ID, "encoded-new")).thenReturn(entity);

        privateSpaceService.updatePassword(request);

        verify(privateSpaceMapper).insert(entity);
    }

    @Test
    @DisplayName("修改密码：旧密码错误则拒绝")
    void updatePassword_wrongOldPassword() {
        PrivatePasswordRequestVO request = testModel(new PrivatePasswordRequestVO(), fields(
                "oldPassword", "bad-old",
                "password", "new-pass"));
        PrivateSpace entity = privateSpace("encoded-old");
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(entity);
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new");
        when(passwordEncoder.matches("bad-old", "encoded-old")).thenReturn(false);

        assertThatThrownBy(() -> privateSpaceService.updatePassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_PASSWORD_ERROR);
    }

    @Test
    @DisplayName("修改密码：旧密码正确时更新哈希并删除当前解锁会话")
    void updatePassword_successUpdatesHashAndDeletesSession() {
        PrivatePasswordRequestVO request = testModel(new PrivatePasswordRequestVO(), fields(
                "oldPassword", "old-pass",
                "password", "new-pass"));
        PrivateSpace entity = privateSpace("encoded-old");
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(entity);
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new");
        when(passwordEncoder.matches("old-pass", "encoded-old")).thenReturn(true);

        privateSpaceService.updatePassword(request);

        assertThat(entity.getPasswordHash()).isEqualTo("encoded-new");
        verify(privateSpaceMapper).updateById(entity);
        verify(stringRedisTemplate).delete(SESSION_KEY);
    }

    @Test
    @DisplayName("修改密码：已有密码但旧密码为空时拒绝")
    void updatePassword_missingOldPasswordThrows() {
        PrivatePasswordRequestVO request = testModel(new PrivatePasswordRequestVO(), fields("password", "new-pass"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-old"));
        when(passwordEncoder.encode("new-pass")).thenReturn("encoded-new");

        assertThatThrownBy(() -> privateSpaceService.updatePassword(request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY);
    }

    @Test
    @DisplayName("解锁成功：写入绑定当前 token 的 3 分钟 Redis 会话")
    void unlock_success() {
        PrivateSessionRequestVO request = testModel(new PrivateSessionRequestVO(), fields("password", "secret"));
        PrivateSessionResponseVO expected = new PrivateSessionResponseVO();
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(passwordEncoder.matches("secret", "encoded-secret")).thenReturn(true);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(privateSpaceConverter.toSessionResponseVO(any(LocalDateTime.class))).thenReturn(expected);

        PrivateSessionResponseVO result = privateSpaceService.unlock(request);

        assertThat(result).isSameAs(expected);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(SESSION_KEY), eq("1"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    @DisplayName("非 VIP 用户不能解锁私密空间")
    void unlock_nonVipDenied() {
        PrivateSessionRequestVO request = testModel(new PrivateSessionRequestVO(), fields("password", "secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> privateSpaceService.unlock(request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NO_PERMISSION_TO_USE_API);
    }

    @Test
    @DisplayName("解锁失败：密码错误时不创建 Redis 会话")
    void unlock_wrongPasswordThrows() {
        PrivateSessionRequestVO request = testModel(new PrivateSessionRequestVO(), fields("password", "bad"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(passwordEncoder.matches("bad", "encoded-secret")).thenReturn(false);

        assertThatThrownBy(() -> privateSpaceService.unlock(request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_PASSWORD_ERROR);

        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("解锁失败：尚未设置密码时拒绝")
    void unlock_withoutPasswordThrows() {
        PrivateSessionRequestVO request = testModel(new PrivateSessionRequestVO(), fields("password", "secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace(" "));

        assertThatThrownBy(() -> privateSpaceService.unlock(request))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_OPERATION_EXCEPTION);
    }

    @Test
    @DisplayName("ACTIVE：可列出私密空间目录并计算子目录标记")
    void listDirectories_active() {
        SysFile childDirectory = sysFile(11L, "素材", 3L, 1, "3,11");
        DirectoryNodeResponseVO expected = directoryNode(11L, "素材", true);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 3L)).thenReturn(sysFile(3L, "项目", 0L, 1, "3"));
        when(sysFileMapper.selectDirectoriesInSpace("PRIVATE", USER_ID, 3L)).thenReturn(List.of(childDirectory));
        when(sysFileMapper.selectParentIdsHavingChildDirectoryInSpace("PRIVATE", USER_ID, List.of(11L)))
                .thenReturn(List.of(11L));
        when(sysFileConverter.toDirectoryNodeVO(childDirectory, true)).thenReturn(expected);

        List<DirectoryNodeResponseVO> result = privateSpaceService.listDirectories(3L);

        assertThat(result).containsExactly(expected);
    }

    @Test
    @DisplayName("ACTIVE：目录下没有子目录时直接返回空列表")
    void listDirectories_empty() {
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectDirectoriesInSpace("PRIVATE", USER_ID, 0L)).thenReturn(List.of());

        assertThat(privateSpaceService.listDirectories(null)).isEmpty();

        verify(sysFileMapper, never()).selectParentIdsHavingChildDirectoryInSpace(any(), any(), any());
    }

    @Test
    @DisplayName("GRACE_PERIOD：可查看私密空间文件列表")
    void listFiles_gracePeriodAllowed() {
        SysFile file = sysFile(21L, "报告.pdf", 0L, 0, "21");
        FileInfoResponseVO item = new FileInfoResponseVO();
        FileListResponseVO expected = fileList(List.of(item), List.of(rootBreadcrumb()));
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpaceInGracePeriod());
        when(quotaService.isVip(USER_ID)).thenReturn(false);
        when(sysFileMapper.selectChildrenInSpace("PRIVATE", USER_ID, 0L)).thenReturn(List.of(file));
        when(sysFileConverter.toFileInfoVOList(List.of(file))).thenReturn(List.of(item));
        when(sysFileConverter.toFileListResponseVO(any(), any())).thenReturn(expected);

        FileListResponseVO result = privateSpaceService.listFiles(null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("ACTIVE：可创建私密空间目录")
    void createDirectory_active() {
        DirectoryCreateRequestVO request = testModel(new DirectoryCreateRequestVO(), fields(
                "name", "凭证",
                "parentId", 0L));
        SysFile directory = sysFile(null, "凭证", 0L, 1, null);
        FileInfoResponseVO expected = new FileInfoResponseVO();
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileConverter.toPrivateDirectory(any(PrivateFileBuildBO.class))).thenReturn(directory);
        when(sysFileMapper.insert(directory)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(directory, "id", 41L);
            return 1;
        });
        when(sysFileConverter.toFileInfoVO(directory)).thenReturn(expected);

        FileInfoResponseVO result = privateSpaceService.createDirectory(request);

        assertThat(result).isSameAs(expected);
        verify(sysFileMapper).lockActiveChildrenInSpace("PRIVATE", USER_ID, 0L);
        assertThat(directory.getFullPath()).isEqualTo("41");
    }

    @Test
    @DisplayName("GRACE_PERIOD：拒绝上传等新增容量动作")
    void uploadFile_gracePeriodDenied() {
        MockMultipartFile file = new MockMultipartFile("file", "private.txt", "text/plain", "secret".getBytes());
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpaceInGracePeriod());
        when(quotaService.isVip(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> privateSpaceService.uploadFile(file, 0L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.NO_PERMISSION_TO_USE_API);
        verify(fileObjectService, never()).saveOrReuse(file);
    }

    @Test
    @DisplayName("ACTIVE：上传私密空间文件时写入 PRIVATE 空间元数据并增加容量")
    void uploadFile_active() {
        MockMultipartFile multipartFile = new MockMultipartFile("file", "private.txt", "text/plain", "secret".getBytes());
        StoredFileObjectBO storedObject = storedObject(6L);
        SysFile inserted = sysFile(null, "private.txt", 0L, 0, null);
        FileInfoResponseVO expected = new FileInfoResponseVO();
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(fileObjectService.saveOrReuse(multipartFile)).thenReturn(storedObject);
        when(sysFileConverter.toPrivateUploadedFile(any(PrivateFileBuildBO.class))).thenReturn(inserted);
        when(sysFileMapper.insert(inserted)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(inserted, "id", 31L);
            return 1;
        });
        when(sysFileConverter.toFileInfoVO(inserted)).thenReturn(expected);

        FileInfoResponseVO result = privateSpaceService.uploadFile(multipartFile, null);

        assertThat(result).isSameAs(expected);
        verify(quotaService).checkSingleFileLimit(USER_ID, 6L);
        verify(quotaService).checkQuota(USER_ID, 6L);
        verify(quotaService).increaseUsedSpace(USER_ID, 6L);
        assertThat(inserted.getFullPath()).isEqualTo("31");
    }

    @Test
    @DisplayName("ACTIVE：上传元数据构造失败时回滚物理对象引用")
    void uploadFile_metadataBuildFailsRollsBackStoredObject() {
        MockMultipartFile multipartFile = new MockMultipartFile("file", "private.txt", "text/plain", "secret".getBytes());
        StoredFileObjectBO storedObject = storedObject(6L);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(fileObjectService.saveOrReuse(multipartFile)).thenReturn(storedObject);
        when(sysFileConverter.toPrivateUploadedFile(any(PrivateFileBuildBO.class)))
                .thenThrow(new IllegalStateException("mapping failed"));

        assertThatThrownBy(() -> privateSpaceService.uploadFile(multipartFile, null))
                .isInstanceOf(IllegalStateException.class);

        verify(fileObjectService).decreaseReferenceOrRemove(storedObject);
        verify(quotaService).decreaseUsedSpace(USER_ID, 6L);
    }

    @Test
    @DisplayName("ACTIVE：上传元数据插入失败时优先释放已构造的私密文件引用")
    void uploadFile_insertFailsRollsBackPrivateFile() {
        MockMultipartFile multipartFile = new MockMultipartFile("file", "private.txt", "text/plain", "secret".getBytes());
        StoredFileObjectBO storedObject = storedObject(6L);
        SysFile privateFile = sysFile(null, "private.txt", 0L, 0, null);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(fileObjectService.saveOrReuse(multipartFile)).thenReturn(storedObject);
        when(sysFileConverter.toPrivateUploadedFile(any(PrivateFileBuildBO.class))).thenReturn(privateFile);
        when(sysFileMapper.insert(privateFile)).thenThrow(new IllegalStateException("insert failed"));

        assertThatThrownBy(() -> privateSpaceService.uploadFile(multipartFile, null))
                .isInstanceOf(IllegalStateException.class);

        verify(fileObjectService).decreaseReferenceOrRemove(privateFile);
        verify(fileObjectService, never()).decreaseReferenceOrRemove(storedObject);
        verify(quotaService).decreaseUsedSpace(USER_ID, 6L);
    }

    @Test
    @DisplayName("上传文件：文件对象为空时拒绝")
    void uploadFile_nullFileThrows() {
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);

        assertThatThrownBy(() -> privateSpaceService.uploadFile(null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.REQUEST_REQUIRED_PARAMETER_IS_EMPTY);
    }

    @Test
    @DisplayName("LOCKED：拒绝查看私密空间文件")
    void getFile_lockedDenied() {
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> privateSpaceService.getFile(21L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.ACCESS_UNAUTHORIZED);
    }

    @Test
    @DisplayName("ACTIVE：可查询私密空间文件详情")
    void getFile_active() {
        SysFile file = sysFile(21L, "报告.pdf", 0L, 0, "21");
        FileInfoResponseVO expected = new FileInfoResponseVO();
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 21L)).thenReturn(file);
        when(sysFileConverter.toFileInfoVO(file)).thenReturn(expected);

        assertThat(privateSpaceService.getFile(21L)).isSameAs(expected);
    }

    @Test
    @DisplayName("ACTIVE：下载私密空间文件委托对象存储写出")
    void downloadFile_active() {
        SysFile file = sysFile(21L, "报告.pdf", 0L, 0, "21");
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 21L)).thenReturn(file);

        privateSpaceService.downloadFile(21L, response);

        verify(fileObjectService).writeToResponse(file, response);
    }

    @Test
    @DisplayName("ACTIVE：下载目录时拒绝")
    void downloadFile_directoryThrows() {
        SysFile directory = sysFile(21L, "资料", 0L, 1, "21");
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 21L)).thenReturn(directory);

        assertThatThrownBy(() -> privateSpaceService.downloadFile(21L, response))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DOWNLOAD_FILE_EXCEPTION);

        verify(fileObjectService, never()).writeToResponse(any(), any());
    }

    @Test
    @DisplayName("ACTIVE：移动私密空间目录时更新根节点与后代路径")
    void moveFile_activeDirectory() {
        SysFile source = sysFile(31L, "项目", 0L, 1, "31");
        SysFile target = sysFile(11L, "归档", 0L, 1, "11");
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 31L)).thenReturn(source);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 11L)).thenReturn(target);

        privateSpaceService.moveFile(31L, 11L);

        verify(sysFileMapper).lockActiveChildrenInSpace("PRIVATE", USER_ID, 11L);
        verify(sysFileMapper).updateRootLocationInSpace("PRIVATE", USER_ID, 31L, 11L, "11,31");
        verify(sysFileMapper).updateDescendantsFullPathInSpace("PRIVATE", USER_ID, 31L, "11,31");
    }

    @Test
    @DisplayName("ACTIVE：移动到原目录时拒绝")
    void moveFile_sameParentThrows() {
        SysFile source = sysFile(31L, "项目", 11L, 1, "11,31");
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 31L)).thenReturn(source);

        assertThatThrownBy(() -> privateSpaceService.moveFile(31L, 11L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("ACTIVE：目录不能移动到自身后代路径下")
    void moveFile_descendantTargetThrows() {
        SysFile source = sysFile(31L, "项目", 0L, 1, "31");
        SysFile child = sysFile(32L, "子目录", 31L, 1, "31,32");
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 31L)).thenReturn(source);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 32L)).thenReturn(child);

        assertThatThrownBy(() -> privateSpaceService.moveFile(31L, 32L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("ACTIVE：删除私密目录到回收站时递归标记并释放容量")
    void deleteToTrash_directory() {
        SysFile root = sysFile(31L, "项目", 0L, 1, "31");
        SysFile child = testModel(sysFile(32L, "报告.pdf", 31L, 0, "31,32"), fields("fileSize", 6L));
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 31L)).thenReturn(root);
        when(sysFileMapper.selectActiveDescendantsInSpace("PRIVATE", USER_ID, 31L)).thenReturn(List.of(child));
        when(sysFileMapper.moveRootToTrashInSpace(eq("PRIVATE"), eq(USER_ID), eq(31L), eq(USER_ID), anyLong())).thenReturn(1);

        privateSpaceService.deleteToTrash(31L);

        verify(sysFileMapper).moveRootToTrashInSpace(eq("PRIVATE"), eq(USER_ID), eq(31L), eq(USER_ID), anyLong());
        verify(sysFileMapper).updateDescendantsRecycleStateInSpace(eq("PRIVATE"), eq(USER_ID), eq(31L), anyLong());
        verify(quotaService).decreaseUsedSpace(USER_ID, 6L);
    }

    @Test
    @DisplayName("ACTIVE：重复删除状态未变化时不释放容量")
    void deleteToTrash_stateChangedSkipsQuota() {
        SysFile root = testModel(sysFile(31L, "旧报告.pdf", 0L, 0, "31"), fields("fileSize", 6L));
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 31L)).thenReturn(root);

        assertThatThrownBy(() -> privateSpaceService.deleteToTrash(31L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
        verify(quotaService, never()).decreaseUsedSpace(USER_ID, 6L);
    }

    @Test
    @DisplayName("GRACE_PERIOD：可列出私密回收站")
    void listTrash_gracePeriodAllowed() {
        SysFile parent = sysFile(11L, "归档", 0L, 1, "11");
        SysFile trash = trashRoot(41L, "旧报告.pdf", 11L, 0, "11,41", 6L);
        RecycleBinItemResponseVO expected = new RecycleBinItemResponseVO();
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpaceInGracePeriod());
        when(quotaService.isVip(USER_ID)).thenReturn(false);
        when(sysFileMapper.selectTrashRootsInSpace("PRIVATE", USER_ID)).thenReturn(List.of(trash));
        when(sysFileMapper.selectFilesInSpaceByIds(eq("PRIVATE"), eq(USER_ID), any())).thenReturn(List.of(parent));
        when(sysFileConverter.toRecycleBinItemVO(trash, "/归档/旧报告.pdf")).thenReturn(expected);

        List<RecycleBinItemResponseVO> result = privateSpaceService.listTrash();

        assertThat(result).containsExactly(expected);
    }

    @Test
    @DisplayName("GRACE_PERIOD：私密回收站为空时直接返回空列表")
    void listTrash_empty() {
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpaceInGracePeriod());
        when(quotaService.isVip(USER_ID)).thenReturn(false);
        when(sysFileMapper.selectTrashRootsInSpace("PRIVATE", USER_ID)).thenReturn(List.of());

        assertThat(privateSpaceService.listTrash()).isEmpty();
    }

    @Test
    @DisplayName("GRACE_PERIOD：删除者查不到时显示未知")
    void listTrash_unknownDeleterUsesFallbackName() {
        SysFile trash = testModel(trashRoot(41L, "旧报告.pdf", 0L, 0, "41", 6L), fields("deletedBy", 99L));
        RecycleBinItemResponseVO item = new RecycleBinItemResponseVO();
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpaceInGracePeriod());
        when(quotaService.isVip(USER_ID)).thenReturn(false);
        when(sysFileMapper.selectTrashRootsInSpace("PRIVATE", USER_ID)).thenReturn(List.of(trash));
        when(sysUserMapper.selectUserBriefByIds(any())).thenReturn(List.of());
        when(sysFileConverter.toRecycleBinItemVO(trash, "/旧报告.pdf")).thenReturn(item);

        List<RecycleBinItemResponseVO> result = privateSpaceService.listTrash();

        assertThat(result.get(0).getDeletedByUserId()).isEqualTo(99L);
        assertThat(result.get(0).getDeletedByName()).isEqualTo("未知");
    }

    @Test
    @DisplayName("ACTIVE：恢复私密回收站文件时按重命名策略恢复并重新计入容量")
    void restoreTrash_rename() {
        SysFile trash = trashRoot(41L, "旧报告.pdf", 11L, 0, "11,41", 6L);
        SysFile parent = sysFile(11L, "归档", 0L, 1, "11");
        SysFile conflict = sysFile(51L, "旧报告.pdf", 11L, 0, "11,51");
        SysFile restored = sysFile(41L, "旧报告(1).pdf", 11L, 0, "11,41");
        FileInfoResponseVO expected = new FileInfoResponseVO();
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 41L)).thenReturn(trash, restored);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 11L)).thenReturn(parent);
        when(sysFileMapper.selectActiveByNameInSpaceDirectory("PRIVATE", USER_ID, 11L, "旧报告.pdf"))
                .thenReturn(conflict);
        when(sysFileMapper.existsNameInSpaceDirectory("PRIVATE", USER_ID, 11L, "旧报告.pdf")).thenReturn(true);
        when(sysFileMapper.selectNamesInSpaceDirectory("PRIVATE", USER_ID, 11L, "旧报告"))
                .thenReturn(List.of("旧报告.pdf"));
        when(sysFileMapper.restoreTrashTreeInSpace("PRIVATE", USER_ID, 41L, 11L, "11", "旧报告(1).pdf"))
                .thenReturn(1);
        when(sysFileConverter.toFileInfoVO(restored)).thenReturn(expected);

        FileInfoResponseVO result = privateSpaceService.restoreTrash(41L, ConflictPolicy.RENAME);

        assertThat(result).isSameAs(expected);
        verify(quotaService).checkQuota(USER_ID, 6L);
        verify(quotaService).increaseUsedSpace(USER_ID, 6L);
        verify(sysFileMapper).restoreTrashTreeInSpace("PRIVATE", USER_ID, 41L, 11L, "11", "旧报告(1).pdf");
    }

    @Test
    @DisplayName("ACTIVE：恢复同名冲突且未指定策略时拒绝")
    void restoreTrash_conflictWithoutPolicyThrows() {
        SysFile trash = trashRoot(41L, "旧报告.pdf", 0L, 0, "41", 6L);
        SysFile conflict = sysFile(51L, "旧报告.pdf", 0L, 0, "51");
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 41L)).thenReturn(trash);
        when(sysFileMapper.selectActiveByNameInSpaceDirectory("PRIVATE", USER_ID, 0L, "旧报告.pdf"))
                .thenReturn(conflict);

        assertThatThrownBy(() -> privateSpaceService.restoreTrash(41L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
    }

    @Test
    @DisplayName("ACTIVE：恢复 OVERWRITE 策略先将活动冲突项移入回收站")
    void restoreTrash_overwriteMovesConflictToTrash() {
        SysFile trash = trashRoot(41L, "旧报告.pdf", 0L, 0, "41", 6L);
        SysFile conflict = testModel(sysFile(51L, "旧报告.pdf", 0L, 0, "51"), fields("fileSize", 4L));
        SysFile restored = sysFile(41L, "旧报告.pdf", 0L, 0, "41");
        FileInfoResponseVO expected = new FileInfoResponseVO();
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 41L)).thenReturn(trash, restored);
        when(sysFileMapper.selectActiveByNameInSpaceDirectory("PRIVATE", USER_ID, 0L, "旧报告.pdf"))
                .thenReturn(conflict);
        when(sysFileMapper.moveRootToTrashInSpace(eq("PRIVATE"), eq(USER_ID), eq(51L), eq(USER_ID), anyLong()))
                .thenReturn(1);
        when(sysFileMapper.restoreTrashTreeInSpace("PRIVATE", USER_ID, 41L, 0L, "", "旧报告.pdf"))
                .thenReturn(1);
        when(sysFileConverter.toFileInfoVO(restored)).thenReturn(expected);

        assertThat(privateSpaceService.restoreTrash(41L, ConflictPolicy.OVERWRITE)).isSameAs(expected);

        verify(quotaService).decreaseUsedSpace(USER_ID, 4L);
        verify(quotaService).increaseUsedSpace(USER_ID, 6L);
    }

    @Test
    @DisplayName("ACTIVE：恢复状态未变化时不重新计入容量")
    void restoreTrash_stateChangedSkipsQuotaIncrease() {
        SysFile trash = trashRoot(41L, "旧报告.pdf", 0L, 0, "41", 6L);
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 41L)).thenReturn(trash);

        assertThatThrownBy(() -> privateSpaceService.restoreTrash(41L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
        verify(quotaService).checkQuota(USER_ID, 6L);
        verify(quotaService, never()).increaseUsedSpace(USER_ID, 6L);
    }

    @Test
    @DisplayName("ACTIVE：永久删除私密回收站文件时释放物理对象引用")
    void permanentlyDeleteTrash_file() {
        SysFile trash = testModel(trashRoot(41L, "旧报告.pdf", 0L, 0, "41", 6L), fields(
                "fileHash", "hash",
                "filePath", "20260521/stored.pdf"));
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 41L)).thenReturn(trash);
        when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("PRIVATE", USER_ID, 41L)).thenReturn(1);

        privateSpaceService.permanentlyDeleteTrash(41L);

        verify(sysFileMapper).permanentlyDeleteTrashTreeInSpace("PRIVATE", USER_ID, 41L);
        verify(fileObjectService).decreaseReferenceOrRemove(trash);
    }

    @Test
    @DisplayName("ACTIVE：永久删除私密回收站目录时释放后代文件引用")
    void permanentlyDeleteTrash_directoryReleasesDescendantFiles() {
        SysFile root = trashRoot(41L, "旧目录", 0L, 1, "41", 0L);
        SysFile childFile = testModel(trashRoot(42L, "旧报告.pdf", 41L, 0, "41,42", 6L), fields("recycleRoot", 0));
        SysFile childDirectory = testModel(trashRoot(43L, "子目录", 41L, 1, "41,43", 0L), fields("recycleRoot", 0));
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 41L)).thenReturn(root);
        when(sysFileMapper.selectDescendantsInSpace("PRIVATE", USER_ID, 41L))
                .thenReturn(List.of(childFile, childDirectory));
        when(sysFileMapper.permanentlyDeleteTrashTreeInSpace("PRIVATE", USER_ID, 41L)).thenReturn(1);

        privateSpaceService.permanentlyDeleteTrash(41L);

        verify(fileObjectService).decreaseReferenceOrRemove(childFile);
        verify(fileObjectService, never()).decreaseReferenceOrRemove(root);
        verify(fileObjectService, never()).decreaseReferenceOrRemove(childDirectory);
    }

    @Test
    @DisplayName("ACTIVE：永久删除状态未变化时不释放物理对象引用")
    void permanentlyDeleteTrash_stateChangedSkipsReferenceDecrease() {
        SysFile trash = testModel(trashRoot(41L, "旧报告.pdf", 0L, 0, "41", 6L), fields(
                "fileHash", "hash",
                "filePath", "20260521/stored.pdf"));
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace("encoded-secret"));
        when(quotaService.isVip(USER_ID)).thenReturn(true);
        when(stringRedisTemplate.getExpire(SESSION_KEY, TimeUnit.SECONDS)).thenReturn(60L);
        when(sysFileMapper.selectSpaceFile("PRIVATE", USER_ID, 41L)).thenReturn(trash);

        assertThatThrownBy(() -> privateSpaceService.permanentlyDeleteTrash(41L))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
        verify(fileObjectService, never()).decreaseReferenceOrRemove(trash);
    }

    @Test
    @DisplayName("VIP 降级：为已开启私密空间写入宽限期截止时间")
    void handleVipStateChanged_downgradeSetsGraceExpireAt() {
        PrivateSpace privateSpace = privateSpace("encoded-secret");
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpace);
        when(systemConfigProperties.getPrivateGracePeriodSeconds()).thenReturn(30L);
        LocalDateTime lowerBound = LocalDateTime.now().plusSeconds(29);

        privateSpaceService.handleVipStateChanged(USER_ID, false);

        ArgumentCaptor<LocalDateTime> graceExpireAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(privateSpaceMapper).updateGraceExpireAt(eq(USER_ID), graceExpireAtCaptor.capture());
        assertThat(graceExpireAtCaptor.getValue())
                .isAfterOrEqualTo(lowerBound)
                .isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(31));
    }

    @Test
    @DisplayName("VIP 恢复：清除私密空间宽限期截止时间")
    void handleVipStateChanged_upgradeClearsGraceExpireAt() {
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(privateSpaceInGracePeriod());

        privateSpaceService.handleVipStateChanged(USER_ID, true);

        verify(privateSpaceMapper).updateGraceExpireAt(USER_ID, null);
    }

    @Test
    @DisplayName("未开启私密空间：VIP 变化不写入宽限期")
    void handleVipStateChanged_disabledSkipsGraceUpdate() {
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(null);

        privateSpaceService.handleVipStateChanged(USER_ID, false);

        verify(privateSpaceMapper, never()).updateGraceExpireAt(eq(USER_ID), any());
    }

    @Test
    @DisplayName("VIP 宽限期已结束：状态为 EXPIRED 并返回过期提醒")
    void getStatus_expiredGracePeriodReturnsReminder() {
        PrivateSpace expired = testModel(privateSpace("encoded-secret"),
                fields("graceExpireAt", LocalDateTime.now().minusSeconds(1)));
        PrivateSpaceStatusResponseVO expected = new PrivateSpaceStatusResponseVO();
        when(privateSpaceMapper.selectByUserId(USER_ID)).thenReturn(expired);
        when(quotaService.isVip(USER_ID)).thenReturn(false);
        when(privateSpaceConverter.toStatusResponseVO(
                eq(PrivateSpaceState.EXPIRED), eq(null), eq(expired.getGraceExpireAt()),
                eq("VIP 宽限期已结束，私密空间已过期"))).thenReturn(expected);

        assertThat(privateSpaceService.getStatus()).isSameAs(expected);
    }

    private PrivateSpace privateSpace(String passwordHash) {
        return testModel(new PrivateSpace(), fields(
                "userId", USER_ID,
                "passwordHash", passwordHash));
    }

    private PrivateSpace privateSpaceInGracePeriod() {
        return testModel(privateSpace("encoded-secret"), fields("graceExpireAt", LocalDateTime.now().plusDays(1)));
    }

    private SysFile sysFile(Long id, String originalName, Long parentId, Integer isDirectory, String fullPath) {
        return testModel(new SysFile(), fields(
                "id", id,
                "originalName", originalName,
                "parentId", parentId,
                "isDirectory", isDirectory,
                "fullPath", fullPath,
                "inRecycleBin", 0));
    }

    private DirectoryNodeResponseVO directoryNode(Long id, String name, boolean hasChildren) {
        return testModel(new DirectoryNodeResponseVO(), fields(
                "id", id,
                "name", name,
                "hasChildren", hasChildren));
    }

    private StoredFileObjectBO storedObject(Long fileSize) {
        return testModel(new StoredFileObjectBO(), fields(
                "storedName", "stored.txt",
                "objectPath", "20260521/stored.txt",
                "fileUrl", "http://minio/stored.txt",
                "fileSize", fileSize,
                "mimeType", "text/plain",
                "fileHash", "hash"));
    }

    private SysFile trashRoot(Long id, String originalName, Long parentId,
                              Integer isDirectory, String fullPath, Long fileSize) {
        return testModel(sysFile(id, originalName, parentId, isDirectory, fullPath), fields(
                "inRecycleBin", 1,
                "recycleRoot", 1,
                "fileSize", fileSize,
                "updateTime", LocalDateTime.now()));
    }

    private BreadcrumbItemResponseVO rootBreadcrumb() {
        return new BreadcrumbItemResponseVO(0L, "根目录");
    }

    private FileListResponseVO fileList(List<FileInfoResponseVO> items, List<BreadcrumbItemResponseVO> breadcrumb) {
        return testModel(new FileListResponseVO(), fields(
                "items", items,
                "breadcrumb", breadcrumb));
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put((String) pairs[i], pairs[i + 1]);
        }
        return values;
    }

    private static <T> T testModel(T target, Map<String, Object> values) {
        values.forEach((name, value) -> ReflectionTestUtils.setField(target, name, value));
        return target;
    }
}
