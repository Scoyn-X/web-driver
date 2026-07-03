package com.jiayuan.boot.system.share.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.oss.utils.DownloadThrottleSupport;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.share.converter.ShareFileConverter;
import com.jiayuan.boot.system.share.converter.SysShareConverter;
import com.jiayuan.boot.system.share.mapper.SysShareMapper;
import com.jiayuan.boot.system.share.model.bo.ShareBuildContextBO;
import com.jiayuan.boot.system.share.model.entity.SysShare;
import com.jiayuan.boot.system.share.model.vo.ShareAccessResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareCreateRequestVO;
import com.jiayuan.boot.system.share.model.vo.ShareInfoResponseVO;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ShareServiceImpl} 单元测试
 * <p>
 * 覆盖 Lab2 3.1.3 文件分享系统的全部功能分支：创建、列表、取消、访问、校验提取码、下载。
 * 用 Mockito mock 掉所有依赖（mapper / converter / FileService / SecurityUtils 静态方法），
 * 保证测试快速（无 Spring 上下文、无 DB、无 MinIO）且隔离。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ShareServiceImpl 单元测试")
class ShareServiceImplTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long CURRENT_ACCOUNT_ID = 10L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long OTHER_ACCOUNT_ID = 20L;
    private static final Long TEAM_ID = 88L;
    private static final Long FILE_ID = 100L;
    private static final Long SHARE_ID = 500L;
    private static final String SHARE_TOKEN = "abcd1234abcd1234abcd1234abcd1234";
    private static final String ORIGINAL_NAME = "报告.txt";

    @Mock
    private SysShareMapper sysShareMapper;
    @Mock
    private SysFileMapper sysFileMapper;
    @Mock
    private SysShareConverter sysShareConverter;
    @Mock
    private ShareFileConverter shareFileConverter;
    @Mock
    private FileObjectService fileObjectService;
    @Mock
    private TeamPermissionService teamPermissionService;
    @Mock
    private DownloadThrottleSupport downloadThrottleSupport;

    @InjectMocks
    private ShareServiceImpl shareService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeEach
    void setUp() {
        // 默认返回当前用户 ID；个别测试会覆盖
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(CURRENT_USER_ID);
        securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(CURRENT_ACCOUNT_ID);

        // MapStruct converter 简化 stub：只复制常用字段，避免每个测试都写
        when(sysShareConverter.toShareInfoVO(any(SysShare.class))).thenAnswer(invocation -> {
            SysShare share = invocation.getArgument(0);
            return testModel(new ShareInfoResponseVO(), fields(
                    "id", share.getId(),
                    "fileId", share.getFileId(),
                    "shareToken", share.getShareToken(),
                    "accessType", share.getAccessType(),
                    "extractCode", share.getExtractCode(),
                    "expireTime", share.getExpireTime(),
                    "createTime", share.getCreateTime()));
        });
        when(sysShareConverter.toShareInfoVO(any(SysShare.class), anyString(), anyString(), any())).thenAnswer(invocation -> {
            SysShare share = invocation.getArgument(0);
            return testModel(new ShareInfoResponseVO(), fields(
                    "id", share.getId(),
                    "fileId", share.getFileId(),
                    "shareToken", share.getShareToken(),
                    "accessType", share.getAccessType(),
                    "extractCode", share.getExtractCode(),
                    "expireTime", share.getExpireTime(),
                    "createTime", share.getCreateTime(),
                    "fileName", invocation.getArgument(1),
                    "statusDesc", invocation.getArgument(2),
                    "isDirectory", invocation.getArgument(3)));
        });
        when(sysShareConverter.toSysShare(any(), any(ShareBuildContextBO.class))).thenAnswer(invocation -> {
            ShareCreateRequestVO req = invocation.getArgument(0);
            ShareBuildContextBO context = invocation.getArgument(1);
            return testModel(new SysShare(), fields(
                    "userId", context.userId(),
                    "fileId", req.getFileId(),
                    "shareToken", context.shareToken(),
                    "accessType", req.getAccessType(),
                    "extractCode", context.extractCode(),
                    "expireTime", context.expireTime(),
                    "status", 0));
        });
        when(sysShareConverter.toTeamSysShare(any(), any(ShareBuildContextBO.class))).thenAnswer(invocation -> {
            ShareCreateRequestVO req = invocation.getArgument(0);
            ShareBuildContextBO context = invocation.getArgument(1);
            return testModel(new SysShare(), fields(
                    "userId", context.userId(),
                    "creatorAccountId", context.creatorAccountId(),
                    "spaceType", "TEAM",
                    "teamId", context.teamId(),
                    "fileId", req.getFileId(),
                    "shareToken", context.shareToken(),
                    "accessType", req.getAccessType(),
                    "extractCode", context.extractCode(),
                    "expireTime", context.expireTime(),
                    "status", 0));
        });
        when(sysShareConverter.toShareAccessResponseVO(any(SysFile.class), anyBoolean())).thenAnswer(invocation -> {
            SysFile file = invocation.getArgument(0);
            boolean requireExtractCode = invocation.getArgument(1);
            return testModel(new ShareAccessResponseVO(), fields(
                    "fileName", file.getOriginalName(),
                    "fileSize", file.getFileSize(),
                    "fileSizeFormatted", file.getFileSize() + " B",
                    "mimeType", file.getMimeType(),
                    "isDirectory", file.getIsDirectory() != null && file.getIsDirectory() == 1,
                    "requireExtractCode", requireExtractCode,
                    "fileUploadTime", file.getCreateTime()));
        });
        when(shareFileConverter.toSharedFileInfoVOList(any(), anyLong())).thenAnswer(invocation -> {
            List<SysFile> files = invocation.getArgument(0);
            Long rootId = invocation.getArgument(1);
            return files.stream().map(file -> toSharedFileInfoVO(file, rootId)).toList();
        });
        when(shareFileConverter.toFileListResponseVO(any(), any())).thenAnswer(invocation -> {
            return testModel(new FileListResponseVO(), fields(
                    "items", invocation.getArgument(0),
                    "breadcrumb", invocation.getArgument(1)));
        });
    }
    @Nested
    @DisplayName("team share management")
    class TeamShareManagementTests {
        @Test
        @DisplayName("创建团队分享：按团队空间查询文件并写入 TEAM 归属")
        void createTeamShare_usesTeamSpaceAndPersistsTeamScope() {
            SysFile file = teamFile();
            when(sysFileMapper.selectTeamFile(TEAM_ID, FILE_ID)).thenReturn(file);

            ShareCreateRequestVO req = shareCreateRequest(FILE_ID, 0);

            ShareInfoResponseVO result = shareService.createTeamShare(TEAM_ID, req);

            ArgumentCaptor<SysShare> captor = ArgumentCaptor.forClass(SysShare.class);
            verify(sysFileMapper).selectTeamFile(TEAM_ID, FILE_ID);
            verify(sysShareMapper).insert(captor.capture());
            assertThat(captor.getValue().getSpaceType()).isEqualTo("TEAM");
            assertThat(captor.getValue().getTeamId()).isEqualTo(TEAM_ID);
            assertThat(captor.getValue().getCreatorAccountId()).isEqualTo(CURRENT_ACCOUNT_ID);
            assertThat(result.getFileName()).isEqualTo(ORIGINAL_NAME);
            assertThat(result.getStatusDesc()).isEqualTo("有效");
            assertThat(result.getIsDirectory()).isFalse();
        }
        @Test
        @DisplayName("创建团队分享：回收站文件不可分享")
        void createTeamShare_recycleBinFile_throws() {
            SysFile file = testModel(teamFile(), fields("inRecycleBin", 1));
            when(sysFileMapper.selectTeamFile(TEAM_ID, FILE_ID)).thenReturn(file);

            ShareCreateRequestVO req = shareCreateRequest(FILE_ID, 0);

            assertThatThrownBy(() -> shareService.createTeamShare(TEAM_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.SHARE_FILE_DELETED);
            verify(sysShareMapper, never()).insert(any());
        }
        @Test
        @DisplayName("列出团队分享：按团队空间批量补充文件名和目录标记")
        void listTeamShares_mapsTeamFileNamesAndDirectoryFlag() {
            SysShare share = teamShare(0);
            when(sysShareMapper.selectTeamShares(TEAM_ID)).thenReturn(List.of(share));

            SysFile file = testModel(teamFile(), fields("isDirectory", 1));
            when(sysFileMapper.selectFilesInSpaceByIds(eq("TEAM"), eq(TEAM_ID), any()))
                    .thenReturn(List.of(file));

            List<ShareInfoResponseVO> result = shareService.listTeamShares(TEAM_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileName()).isEqualTo(ORIGINAL_NAME);
            assertThat(result.get(0).getStatusDesc()).isEqualTo("有效");
            assertThat(result.get(0).getIsDirectory()).isTrue();
        }
        @Test
        @DisplayName("获取团队分享详情：仅查询指定团队分享")
        void getTeamShare_returnsTeamShareDetail() {
            SysShare share = teamShare(0);
            when(sysShareMapper.selectTeamShare(TEAM_ID, SHARE_ID)).thenReturn(share);
            when(sysFileMapper.selectTeamFile(TEAM_ID, FILE_ID)).thenReturn(teamFile());

            ShareInfoResponseVO result = shareService.getTeamShare(TEAM_ID, SHARE_ID);

            assertThat(result.getId()).isEqualTo(SHARE_ID);
            assertThat(result.getFileName()).isEqualTo(ORIGINAL_NAME);
            assertThat(result.getIsDirectory()).isFalse();
            verify(sysShareMapper).selectTeamShare(TEAM_ID, SHARE_ID);
        }
        @Test
        @DisplayName("取消团队分享：创建者可取消自己的分享")
        void cancelTeamShare_creatorDeletesOwnShare() {
            SysShare share = teamShare(0);
            when(sysShareMapper.selectTeamShare(TEAM_ID, SHARE_ID)).thenReturn(share);
            when(sysShareMapper.deleteTeamShare(TEAM_ID, SHARE_ID)).thenReturn(1);

            shareService.cancelTeamShare(TEAM_ID, SHARE_ID);

            verify(sysShareMapper).selectTeamShare(TEAM_ID, SHARE_ID);
            verify(sysShareMapper).deleteTeamShare(TEAM_ID, SHARE_ID);
            verify(teamPermissionService, never()).checkPermission(anyLong(), anyLong(), anyString());
        }
        @Test
        @DisplayName("取消团队分享：非创建者需具备分享管理权限")
        void cancelTeamShare_managerDeletesOtherCreatorShare() {
            SysShare share = testModel(teamShare(0), fields("creatorAccountId", OTHER_ACCOUNT_ID));
            when(sysShareMapper.selectTeamShare(TEAM_ID, SHARE_ID)).thenReturn(share);
            when(sysShareMapper.deleteTeamShare(TEAM_ID, SHARE_ID)).thenReturn(1);

            shareService.cancelTeamShare(TEAM_ID, SHARE_ID);

            verify(teamPermissionService).checkPermission(TEAM_ID, CURRENT_ACCOUNT_ID, "share:manage");
            verify(sysShareMapper).deleteTeamShare(TEAM_ID, SHARE_ID);
        }
        @Test
        @DisplayName("取消团队分享：非创建者缺少分享管理权限时拒绝")
        void cancelTeamShare_nonCreatorWithoutManagePermissionThrows() {
            SysShare share = testModel(teamShare(0), fields("creatorAccountId", OTHER_ACCOUNT_ID));
            when(sysShareMapper.selectTeamShare(TEAM_ID, SHARE_ID)).thenReturn(share);
            doThrow(new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API, "缺少团队权限：share:manage"))
                    .when(teamPermissionService)
                    .checkPermission(TEAM_ID, CURRENT_ACCOUNT_ID, "share:manage");

            assertThatThrownBy(() -> shareService.cancelTeamShare(TEAM_ID, SHARE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.NO_PERMISSION_TO_USE_API);

            verify(sysShareMapper, never()).deleteTeamShare(anyLong(), anyLong());
        }
        @Test
        @DisplayName("团队解散：批量失效团队分享")
        void invalidateTeamShares_delegatesToMapper() {
            shareService.invalidateTeamShares(TEAM_ID);

            verify(sysShareMapper).invalidateTeamShares(TEAM_ID);
        }
        @Test
        @DisplayName("成员失效：批量失效该成员创建的团队分享")
        void invalidateTeamSharesByCreator_delegatesToMapper() {
            shareService.invalidateTeamSharesByCreator(TEAM_ID, OTHER_USER_ID);

            verify(sysShareMapper).invalidateTeamSharesByCreator(TEAM_ID, OTHER_USER_ID);
        }
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }
    @Nested
    @DisplayName("createShare")
    class CreateShareTests {
        @Test
        @DisplayName("全公开分享：不生成提取码，永久有效")
        void createShare_public_noExtractCode_noExpiry() {
            SysFile file = ownedFile();
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(file);

            ShareCreateRequestVO req = shareCreateRequest(FILE_ID, 0);

            ShareInfoResponseVO result = shareService.createShare(req);

            assertThat(result.getAccessType()).isEqualTo(0);
            assertThat(result.getExtractCode()).isNull();
            assertThat(result.getExpireTime()).isNull();
            assertThat(result.getFileName()).isEqualTo(ORIGINAL_NAME);
            assertThat(result.getStatusDesc()).isEqualTo("有效");
            assertThat(result.getShareToken()).isNotBlank();
            assertThat(result.getIsDirectory()).isFalse();
            verify(sysShareMapper).insert(any(SysShare.class));
        }
        @Test
        @DisplayName("目录分享：返回 isDirectory=true 供前端展示文件夹")
        void createShare_directory_returnsDirectoryFlag() {
            SysFile directory = directory(FILE_ID, 0L, String.valueOf(FILE_ID));
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(directory);

            ShareCreateRequestVO req = shareCreateRequest(FILE_ID, 0);

            ShareInfoResponseVO result = shareService.createShare(req);

            assertThat(result.getIsDirectory()).isTrue();
        }
        @Test
        @DisplayName("分享码分享：自动生成 4 位字母数字提取码")
        void createShare_withExtractCode_generates4CharAlphanumeric() {
            SysFile file = ownedFile();
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(file);

            ShareCreateRequestVO req = shareCreateRequest(FILE_ID, 1, 7);

            ShareInfoResponseVO result = shareService.createShare(req);

            assertThat(result.getExtractCode())
                    .isNotBlank()
                    .hasSize(4)
                    .matches("[A-Z0-9]{4}");
        }
        @Test
        @DisplayName("expireDays=7：过期时间设置为 7 天后")
        void createShare_expireDays7_setsExpireTime7DaysLater() {
            SysFile file = ownedFile();
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(file);

            ShareCreateRequestVO req = shareCreateRequest(FILE_ID, 0, 7);

            LocalDateTime before = LocalDateTime.now().plusDays(7).minusSeconds(5);
            ShareInfoResponseVO result = shareService.createShare(req);
            LocalDateTime after = LocalDateTime.now().plusDays(7).plusSeconds(5);

            assertThat(result.getExpireTime()).isBetween(before, after);
        }
        @Test
        @DisplayName("文件不存在：抛 USER_RESOURCE_NOT_FOUND")
        void createShare_fileNotFound_throws() {
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(null);

            ShareCreateRequestVO req = shareCreateRequest(FILE_ID, 0);

            assertThatThrownBy(() -> shareService.createShare(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
            verify(sysShareMapper, never()).insert(any());
        }
        @Test
        @DisplayName("分享他人文件：抛 SHARE_NOT_OWNER")
        void createShare_notOwner_throws() {
            SysFile file = testModel(new SysFile(), fields(
                    "id", FILE_ID,
                    "userId", OTHER_USER_ID,
                    "originalName", ORIGINAL_NAME));
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(file);

            ShareCreateRequestVO req = shareCreateRequest(FILE_ID, 0);

            assertThatThrownBy(() -> shareService.createShare(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.SHARE_NOT_OWNER);
            verify(sysShareMapper, never()).insert(any());
        }
        @Test
        @DisplayName("团队文件上传者不能通过个人分享入口分享")
        void createShare_teamFileUploadedByCurrentUser_throws() {
            SysFile file = testModel(ownedFile(), fields(
                    "spaceType", "TEAM",
                    "spaceId", 88L,
                    "uploaderId", CURRENT_USER_ID));
            when(sysFileMapper.selectById(FILE_ID)).thenReturn(file);

            ShareCreateRequestVO req = shareCreateRequest(FILE_ID, 0);

            assertThatThrownBy(() -> shareService.createShare(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_RESOURCE_NOT_FOUND);
            verify(sysShareMapper, never()).insert(any());
        }
    }
    @Nested
    @DisplayName("listMyShares")
    class ListMySharesTests {
        @Test
        @DisplayName("当前用户无分享：返回空列表，不查文件")
        void listMyShares_empty() {
            when(sysShareMapper.selectPersonalShares(CURRENT_USER_ID)).thenReturn(Collections.emptyList());

            List<ShareInfoResponseVO> result = shareService.listMyShares();

            assertThat(result).isEmpty();
            verify(sysFileMapper, never()).selectPersonalShareFilesByIds(anyLong(), any());
        }
        @Test
        @DisplayName("多条分享：fileName 和 isDirectory 从 sys_file 批量映射")
        void listMyShares_mapsFileNamesAndDirectoryFlag() {
            SysShare s1 = share(SHARE_ID, FILE_ID, 0, 0, null);
            SysShare s2 = share(SHARE_ID + 1, FILE_ID + 1, 1, 0, LocalDateTime.now().plusDays(3));
            when(sysShareMapper.selectPersonalShares(CURRENT_USER_ID)).thenReturn(List.of(s1, s2));

            SysFile f1 = testModel(new SysFile(), fields("id", FILE_ID, "originalName", "a.txt", "isDirectory", 0));
            SysFile f2 = testModel(new SysFile(), fields("id", FILE_ID + 1, "originalName", "b", "isDirectory", 1));
            when(sysFileMapper.selectPersonalShareFilesByIds(anyLong(), any())).thenReturn(List.of(f1, f2));

            List<ShareInfoResponseVO> result = shareService.listMyShares();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(ShareInfoResponseVO::getFileName).containsExactlyInAnyOrder("a.txt", "b");
            assertThat(result).extracting(ShareInfoResponseVO::getIsDirectory).containsExactly(false, true);
        }
        @Test
        @DisplayName("被分享文件已永久删除：fileName=[已删除] + statusDesc=文件已删除")
        void listMyShares_fileDeleted_placeholder() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            when(sysShareMapper.selectPersonalShares(CURRENT_USER_ID)).thenReturn(List.of(s));
            when(sysFileMapper.selectPersonalShareFilesByIds(anyLong(), any())).thenReturn(Collections.emptyList());

            List<ShareInfoResponseVO> result = shareService.listMyShares();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileName()).isEqualTo("[已删除]");
            assertThat(result.get(0).getStatusDesc()).isEqualTo("文件已删除");
            assertThat(result.get(0).getIsDirectory()).isNull();
        }
        @Test
        @DisplayName("被分享文件在回收站：statusDesc=文件已删除，fileName 保留原名")
        void listMyShares_fileInRecycleBin_markedDeleted() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            when(sysShareMapper.selectPersonalShares(CURRENT_USER_ID)).thenReturn(List.of(s));

            SysFile f = testModel(new SysFile(), fields(
                    "id", FILE_ID,
                    "originalName", "a.txt",
                    "inRecycleBin", 1));
            when(sysFileMapper.selectPersonalShareFilesByIds(anyLong(), any())).thenReturn(List.of(f));

            List<ShareInfoResponseVO> result = shareService.listMyShares();

            assertThat(result.get(0).getFileName()).isEqualTo("a.txt");
            assertThat(result.get(0).getStatusDesc()).isEqualTo("文件已删除");
        }
        @Test
        @DisplayName("过期优先级高于文件删除：文件回收+已过期 → statusDesc=已过期")
        void listMyShares_expiredAndDeleted_expiredWins() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, LocalDateTime.now().minusDays(1));
            when(sysShareMapper.selectPersonalShares(CURRENT_USER_ID)).thenReturn(List.of(s));

            SysFile f = testModel(new SysFile(), fields(
                    "id", FILE_ID,
                    "originalName", "a.txt",
                    "inRecycleBin", 1));
            when(sysFileMapper.selectPersonalShareFilesByIds(anyLong(), any())).thenReturn(List.of(f));

            assertThat(shareService.listMyShares().get(0).getStatusDesc()).isEqualTo("已过期");
        }
        @Test
        @DisplayName("已过期分享：statusDesc = 已过期")
        void listMyShares_expired_statusDesc() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, LocalDateTime.now().minusDays(1));
            when(sysShareMapper.selectPersonalShares(CURRENT_USER_ID)).thenReturn(List.of(s));

            SysFile f = testModel(new SysFile(), fields(
                    "id", FILE_ID,
                    "originalName", "a.txt"));
            when(sysFileMapper.selectPersonalShareFilesByIds(anyLong(), any())).thenReturn(List.of(f));

            List<ShareInfoResponseVO> result = shareService.listMyShares();

            assertThat(result.get(0).getStatusDesc()).isEqualTo("已过期");
        }
    }
    @Nested
    @DisplayName("cancelShare")
    class CancelShareTests {
        @Test
        @DisplayName("正常取消：调用 deleteById 触发 @TableLogic 逻辑删除")
        void cancelShare_success() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            when(sysShareMapper.selectPersonalShare(CURRENT_USER_ID, SHARE_ID)).thenReturn(s);

            shareService.cancelShare(SHARE_ID);

            verify(sysShareMapper).deleteById(SHARE_ID);
            verify(sysShareMapper, never()).updateById(any());
        }
        @Test
        @DisplayName("分享不存在：抛 SHARE_NOT_FOUND（覆盖被重复取消的场景）")
        void cancelShare_notFound_throws() {
            // selectPersonalShare 已过滤已删/非个人分享记录；mock 返回 null 覆盖两种情况
            when(sysShareMapper.selectPersonalShare(CURRENT_USER_ID, SHARE_ID)).thenReturn(null);

            assertThatThrownBy(() -> shareService.cancelShare(SHARE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_NOT_FOUND);
            verify(sysShareMapper, never()).deleteById(any());
        }
        @Test
        @DisplayName("非分享创建者：抛 SHARE_NOT_OWNER")
        void cancelShare_notOwner_throws() {
            SysShare s = testModel(share(SHARE_ID, FILE_ID, 0, 0, null), fields("userId", OTHER_USER_ID));
            when(sysShareMapper.selectPersonalShare(CURRENT_USER_ID, SHARE_ID)).thenReturn(s);

            assertThatThrownBy(() -> shareService.cancelShare(SHARE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_NOT_OWNER);
            verify(sysShareMapper, never()).deleteById(any());
        }
    }
    @Nested
    @DisplayName("getShareByToken")
    class GetShareByTokenTests {
        @Test
        @DisplayName("全公开分享：requireExtractCode = false")
        void getShareByToken_public() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);

            SysFile f = normalFile();
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(f);

            ShareAccessResponseVO result = shareService.getShareByToken(SHARE_TOKEN);

            assertThat(result.getFileName()).isEqualTo(ORIGINAL_NAME);
            assertThat(result.getFileSize()).isEqualTo(58L);
            assertThat(result.getFileSizeFormatted()).isEqualTo("58 B");
            assertThat(result.getRequireExtractCode()).isFalse();
            assertThat(result.getIsDirectory()).isFalse();
        }

        @Test
        @DisplayName("目录分享访问页：返回递归目录大小")
        void getShareByToken_directoryUsesRecursiveSize() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);

            SysFile directory = testModel(directory(FILE_ID, 0L, String.valueOf(FILE_ID)), fields("fileSize", 0L));
            SysFile sizedDirectory = testModel(directory, fields("fileSize", 300L));
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(directory);
            when(sysFileMapper.selectSpaceFileWithRecursiveSize("PERSONAL", CURRENT_USER_ID, FILE_ID))
                    .thenReturn(sizedDirectory);

            ShareAccessResponseVO result = shareService.getShareByToken(SHARE_TOKEN);

            assertThat(result.getFileSize()).isEqualTo(300L);
            assertThat(result.getFileSizeFormatted()).isEqualTo("300 B");
            assertThat(result.getIsDirectory()).isTrue();
            verify(sysFileMapper).selectSpaceFileWithRecursiveSize("PERSONAL", CURRENT_USER_ID, FILE_ID);
        }

        @Test
        @DisplayName("分享码分享：requireExtractCode = true")
        void getShareByToken_extractCodeRequired() {
            SysShare s = shareWithExtractCode();
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(normalFile());

            ShareAccessResponseVO result = shareService.getShareByToken(SHARE_TOKEN);

            assertThat(result.getRequireExtractCode()).isTrue();
        }
        @Test
        @DisplayName("token 不存在：抛 SHARE_NOT_FOUND")
        void getShareByToken_notFound_throws() {
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(null);

            assertThatThrownBy(() -> shareService.getShareByToken(SHARE_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_NOT_FOUND);
        }
        @Test
        @DisplayName("分享已取消（被 @TableLogic 过滤）：selectOne 返 null → SHARE_NOT_FOUND")
        void getShareByToken_cancelled_throws() {
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(null);

            assertThatThrownBy(() -> shareService.getShareByToken(SHARE_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_NOT_FOUND);
        }
        @Test
        @DisplayName("分享已过期：抛 SHARE_EXPIRED")
        void getShareByToken_expired_throws() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, LocalDateTime.now().minusMinutes(1));
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);

            assertThatThrownBy(() -> shareService.getShareByToken(SHARE_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXPIRED);
        }
        @Test
        @DisplayName("文件已删除：抛 SHARE_FILE_DELETED")
        void getShareByToken_fileDeleted_throws() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(null);

            assertThatThrownBy(() -> shareService.getShareByToken(SHARE_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_FILE_DELETED);
        }
        @Test
        @DisplayName("团队分享：按 teamId + fileId 查询团队空间文件")
        void getShareByToken_teamShare_usesTeamFile() {
            SysShare s = teamShare(0);
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);
            when(sysFileMapper.selectTeamFile(TEAM_ID, FILE_ID)).thenReturn(teamFile());

            ShareAccessResponseVO result = shareService.getShareByToken(SHARE_TOKEN);

            assertThat(result.getFileName()).isEqualTo(ORIGINAL_NAME);
            verify(sysFileMapper).selectTeamFile(TEAM_ID, FILE_ID);
            verify(sysFileMapper, never()).selectPersonalShareFile(anyLong(), anyLong());
        }
    }
    @Nested
    @DisplayName("verifyExtractCode")
    class VerifyExtractCodeTests {
        @Test
        @DisplayName("全公开分享：任何 code 都直接通过")
        void verify_publicShare_passesAlways() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);

            assertThatCode(() -> shareService.verifyExtractCode(SHARE_TOKEN, null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> shareService.verifyExtractCode(SHARE_TOKEN, "WHATEVER"))
                    .doesNotThrowAnyException();
        }
        @Test
        @DisplayName("正确提取码：通过")
        void verify_correctCode_passes() {
            SysShare s = shareWithExtractCode();
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);

            assertThatCode(() -> shareService.verifyExtractCode(SHARE_TOKEN, "ABCD"))
                    .doesNotThrowAnyException();
        }
        @Test
        @DisplayName("大小写不敏感：小写输入仍通过")
        void verify_caseInsensitive() {
            SysShare s = shareWithExtractCode();
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);

            assertThatCode(() -> shareService.verifyExtractCode(SHARE_TOKEN, "abcd"))
                    .doesNotThrowAnyException();
        }
        @Test
        @DisplayName("错误提取码：抛 SHARE_EXTRACT_CODE_WRONG")
        void verify_wrongCode_throws() {
            SysShare s = shareWithExtractCode();
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);

            assertThatThrownBy(() -> shareService.verifyExtractCode(SHARE_TOKEN, "WXYZ"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXTRACT_CODE_WRONG);
        }
        @Test
        @DisplayName("null 提取码（分享码分享）：抛 SHARE_EXTRACT_CODE_WRONG")
        void verify_nullCode_throws() {
            SysShare s = shareWithExtractCode();
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);

            assertThatThrownBy(() -> shareService.verifyExtractCode(SHARE_TOKEN, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXTRACT_CODE_WRONG);
        }
    }
    @Nested
    @DisplayName("listSharedChildren")
    class ListSharedChildrenTests {
        @Test
        @DisplayName("团队目录分享：parentId=0 时列出分享根目录子项并裁剪 fullPath")
        void listSharedChildren_teamRoot_returnsChildrenInsideShareRoot() {
            SysShare s = teamShare(0);
            SysFile root = teamDirectory(FILE_ID, 0L, "10," + FILE_ID);
            SysFile child = teamDirectory(FILE_ID + 1, FILE_ID, "10," + FILE_ID + "," + (FILE_ID + 1));
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);
            when(sysFileMapper.selectTeamFile(TEAM_ID, FILE_ID)).thenReturn(root);
            when(sysFileMapper.selectChildrenInSpace("TEAM", TEAM_ID, FILE_ID)).thenReturn(List.of(child));

            FileListResponseVO result = shareService.listSharedChildren(SHARE_TOKEN, 0L, null);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getFullPath()).containsExactly(FILE_ID, FILE_ID + 1);
            assertThat(result.getItems().get(0).getFileUrl()).isNull();
            assertThat(result.getBreadcrumb())
                    .extracting(BreadcrumbItemResponseVO::getId)
                    .containsExactly(FILE_ID);
            verify(sysFileMapper).selectChildrenInSpace("TEAM", TEAM_ID, FILE_ID);
        }
        @Test
        @DisplayName("目录分享：指定分享根子孙目录时只列该目录子项")
        void listSharedChildren_descendantParent_returnsDescendantChildren() {
            Long parentId = FILE_ID + 1;
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            SysFile root = directory(FILE_ID, 0L, "10," + FILE_ID);
            SysFile parent = directory(parentId, FILE_ID, "10," + FILE_ID + "," + parentId);
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(root);
            when(sysFileMapper.selectActiveDirectoryInSharedTree("PERSONAL", CURRENT_USER_ID, FILE_ID, parentId))
                    .thenReturn(parent);
            when(sysFileMapper.selectFilesInSpaceByIds(eq("PERSONAL"), eq(CURRENT_USER_ID), any()))
                    .thenReturn(List.of(root, parent));
            when(sysFileMapper.selectChildrenInSpace("PERSONAL", CURRENT_USER_ID, parentId)).thenReturn(List.of());

            FileListResponseVO result = shareService.listSharedChildren(SHARE_TOKEN, parentId, null);

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getBreadcrumb())
                    .extracting(BreadcrumbItemResponseVO::getId)
                    .containsExactly(FILE_ID, parentId);
        }
        @Test
        @DisplayName("文件分享调用 children：抛 SHARE_EXCEPTION")
        void listSharedChildren_fileShare_throws() {
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID)).thenReturn(normalFile());

            assertThatThrownBy(() -> shareService.listSharedChildren(SHARE_TOKEN, 0L, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXCEPTION);
        }
        @Test
        @DisplayName("parentId 越出分享根目录：拒绝访问")
        void listSharedChildren_parentOutsideSharedRoot_throws() {
            Long outsideParentId = FILE_ID + 99;
            SysShare s = share(SHARE_ID, FILE_ID, 0, 0, null);
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);
            when(sysFileMapper.selectPersonalShareFile(CURRENT_USER_ID, FILE_ID))
                    .thenReturn(directory(FILE_ID, 0L, String.valueOf(FILE_ID)));
            when(sysFileMapper.selectActiveDirectoryInSharedTree("PERSONAL", CURRENT_USER_ID, FILE_ID, outsideParentId))
                    .thenReturn(null);

            assertThatThrownBy(() -> shareService.listSharedChildren(SHARE_TOKEN, outsideParentId, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXCEPTION);
        }
        @Test
        @DisplayName("分享码目录：children 校验 code 后才列目录")
        void listSharedChildren_extractCodeWrong_throwsBeforeFileLookup() {
            SysShare s = shareWithExtractCode();
            when(sysShareMapper.selectShareByToken(SHARE_TOKEN)).thenReturn(s);

            assertThatThrownBy(() -> shareService.listSharedChildren(SHARE_TOKEN, 0L, "WRONG"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode").isEqualTo(ResultCode.SHARE_EXTRACT_CODE_WRONG);
            verify(sysFileMapper, never()).selectPersonalShareFile(anyLong(), anyLong());
        }
    }
    private ShareCreateRequestVO shareCreateRequest(Long fileId, int accessType) {
        return shareCreateRequest(fileId, accessType, null);
    }

    private ShareCreateRequestVO shareCreateRequest(Long fileId, int accessType, Integer expireDays) {
        return testModel(new ShareCreateRequestVO(), fields(
                "fileId", fileId,
                "accessType", accessType,
                "expireDays", expireDays));
    }

    /** 构建属于 CURRENT_USER 的文件实体 */
    private SysFile ownedFile() {
        return testModel(new SysFile(), fields(
                "id", FILE_ID,
                "userId", CURRENT_USER_ID,
                "spaceType", "PERSONAL",
                "spaceId", CURRENT_USER_ID,
                "uploaderId", CURRENT_USER_ID,
                "originalName", ORIGINAL_NAME,
                "fileSize", 58L,
                "mimeType", "text/plain",
                "isDirectory", 0,
                "createTime", LocalDateTime.now().minusMinutes(10)));
    }

    /** 等同 ownedFile，给访问分享场景用 */
    private SysFile normalFile() {
        return ownedFile();
    }

    private SysFile directory(Long id, Long parentId, String fullPath) {
        return testModel(ownedFile(), fields(
                "id", id,
                "parentId", parentId,
                "isDirectory", 1,
                "fullPath", fullPath));
    }

    /** 构建团队空间文件 */
    private SysFile teamFile() {
        return testModel(ownedFile(), fields(
                "spaceType", "TEAM",
                "spaceId", TEAM_ID,
                "uploaderId", CURRENT_USER_ID));
    }

    private SysFile teamDirectory(Long id, Long parentId, String fullPath) {
        return testModel(directory(id, parentId, fullPath), fields(
                "spaceType", "TEAM",
                "spaceId", TEAM_ID));
    }

    /** 构建分享实体 */
    private SysShare share(Long id, Long fileId, int accessType, int status, LocalDateTime expireTime) {
        return testModel(new SysShare(), fields(
                "id", id,
                "userId", CURRENT_USER_ID,
                "fileId", fileId,
                "shareToken", SHARE_TOKEN,
                "accessType", accessType,
                "status", status,
                "expireTime", expireTime,
                "createTime", LocalDateTime.now().minusMinutes(30)));
    }

    private SysShare teamShare(int accessType) {
        return testModel(share(SHARE_ID, FILE_ID, accessType, 0, null), fields(
                "spaceType", "TEAM",
                "teamId", TEAM_ID,
                "creatorAccountId", CURRENT_ACCOUNT_ID));
    }

    private SysShare shareWithExtractCode() {
        return testModel(share(SHARE_ID, FILE_ID, 1, 0, null), fields("extractCode", "ABCD"));
    }

    private FileInfoResponseVO toFileInfoVO(SysFile file) {
        return testModel(new FileInfoResponseVO(), fields(
                "id", file.getId(),
                "originalName", file.getOriginalName(),
                "fileSize", file.getFileSize(),
                "mimeType", file.getMimeType(),
                "createTime", file.getCreateTime(),
                "fileUrl", file.getFileUrl(),
                "isDirectory", file.getIsDirectory(),
                "parentId", file.getParentId(),
                "fullPath", com.jiayuan.boot.common.util.StringUtils.parseIdList(file.getFullPath())));
    }

    private FileInfoResponseVO toSharedFileInfoVO(SysFile file, Long rootId) {
        FileInfoResponseVO vo = toFileInfoVO(file);
        List<Long> fullPath = vo.getFullPath();
        int rootIndex = fullPath.indexOf(rootId);
        return testModel(vo, fields(
                "fullPath", rootIndex < 0 ? List.of() : fullPath.subList(rootIndex, fullPath.size()),
                "fileUrl", null));
    }

    private static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value != null) {
                values.put((String) pairs[i], value);
            }
        }
        return values;
    }

    private static <T> T testModel(T target, Map<String, Object> values) {
        values.forEach((name, value) -> ReflectionTestUtils.setField(target, name, value));
        return target;
    }
}
