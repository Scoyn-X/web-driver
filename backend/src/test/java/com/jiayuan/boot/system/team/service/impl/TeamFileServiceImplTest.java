package com.jiayuan.boot.system.team.service.impl;

import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.auth.mapper.SysUserMapper;
import com.jiayuan.boot.system.auth.model.entity.SysUser;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileCopyRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileMoveRequestVO;
import com.jiayuan.boot.system.oss.model.vo.FileTreeResponseVO;
import com.jiayuan.boot.system.oss.service.FileObjectService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import com.jiayuan.boot.system.team.controller.TeamFileController;
import com.jiayuan.boot.system.team.controller.TeamTrashController;
import com.jiayuan.boot.system.team.converter.TeamTrashConverter;
import com.jiayuan.boot.system.team.model.enums.ConflictPolicy;
import com.jiayuan.boot.system.team.model.vo.TeamFileResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamTrashItemResponseVO;
import com.jiayuan.boot.system.team.model.vo.TransferFromPersonalRequestVO;
import com.jiayuan.boot.system.team.model.vo.TransferToPersonalRequestVO;
import com.jiayuan.boot.system.team.security.RequireTeamPerm;
import com.jiayuan.boot.system.team.service.TeamFileService;
import com.jiayuan.boot.system.team.service.TeamPermissionService;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 团队文件只读服务单元测试。
 *
 * @author charleslam
 * @since 2026/05/18
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamFileServiceImpl 单元测试")
class TeamFileServiceImplTest {

    private static final Long TEAM_ID = 9L;
    private static final Long USER_ID = 7L;
    private static final Long ACCOUNT_ID = 70L;
    private static final String PERMISSION_FILE_LIST = "file:list";
    private static final String PERMISSION_FILE_DETAIL = "file:detail";
    private static final String PERMISSION_FILE_DOWNLOAD = "file:download";
    private static final String REQUIRE_FILE_LIST = "@requireTeamPerm.hasPerm('file:list')";
    private static final String REQUIRE_FILE_DETAIL = "@requireTeamPerm.hasPerm('file:detail')";
    private static final String REQUIRE_FILE_DOWNLOAD = "@requireTeamPerm.hasPerm('file:download')";
    private static final String REQUIRE_FILE_UPLOAD = "@requireTeamPerm.hasPerm('file:upload')";
    private static final String REQUIRE_FILE_MOVE = "@requireTeamPerm.hasPerm('file:move')";
    private static final String REQUIRE_FILE_COPY = "@requireTeamPerm.hasPerm('file:copy')";
    private static final String REQUIRE_FILE_TRANSFER_TO_TEAM = "@requireTeamPerm.hasPerm('file:transfer:to-team')";
    private static final String REQUIRE_FILE_TRANSFER_TO_PERSONAL =
            "@requireTeamPerm.hasPerm('file:transfer:to-personal')";
    private static final String REQUIRE_FILE_DELETE = "@requireTeamPerm.hasPerm('file:delete')";
    private static final String REQUIRE_TRASH_LIST = "@requireTeamPerm.hasPerm('trash:list')";
    private static final String REQUIRE_TRASH_DELETE = "@requireTeamPerm.hasPerm('trash:delete')";

    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private SysUserMapper sysUserMapper;
    @Mock private FileObjectService fileObjectService;
    @Mock private TeamFileWriteService teamFileWriteService;
    @Mock private TeamPermissionService teamPermissionService;
    @Mock private TeamTrashConverter teamTrashConverter;
    @Mock private HttpServletResponse response;

    private TeamFileServiceImpl teamFileService;

    @BeforeEach
    void setUp() {
        TeamFileLookupService lookupService = new TeamFileLookupService(sysFileMapper);
        teamFileService = new TeamFileServiceImpl(
                sysFileMapper, sysFileConverter, sysUserMapper, fileObjectService,
                teamFileWriteService, lookupService, teamTrashConverter);
    }

    @Test
    @DisplayName("列出根目录文件：返回根面包屑")
    void listFiles_root() {
        FileListResponseVO response = new FileListResponseVO();
        response.setItems(Collections.emptyList());
        response.setBreadcrumb(List.of(new BreadcrumbItemResponseVO(0L, "根目录")));
        when(sysFileMapper.selectTeamChildren(TEAM_ID, 0L)).thenReturn(Collections.emptyList());
        when(sysFileConverter.toFileInfoVOList(anyList())).thenReturn(Collections.emptyList());
        when(sysFileConverter.toFileListResponseVO(Collections.emptyList(), List.of(new BreadcrumbItemResponseVO(0L, "根目录"))))
                .thenReturn(response);

        FileListResponseVO result = teamFileService.listFiles(TEAM_ID, 0L);

        verifyNoInteractions(teamPermissionService);
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getBreadcrumb()).extracting(BreadcrumbItemResponseVO::getName).containsExactly("根目录");
    }

    @Test
    @DisplayName("列出团队目录：空目录列表直接返回空")
    void listDirectories_emptyListSkipsChildLookup() {
        when(sysFileMapper.selectTeamDirectories(TEAM_ID, 0L)).thenReturn(Collections.emptyList());

        List<DirectoryNodeResponseVO> result = teamFileService.listDirectories(TEAM_ID, null);

        assertThat(result).isEmpty();
        verify(sysFileMapper).selectTeamDirectories(TEAM_ID, 0L);
        verifyNoInteractions(sysFileConverter);
    }

    @Test
    @DisplayName("列出团队目录：按子目录存在性设置 hasChildren")
    void listDirectories_mapsHasChildrenFlag() {
        SysFile first = TeamFileTestFixtures.teamDirectory(TEAM_ID, USER_ID, 11L, 0L, "11");
        SysFile second = TeamFileTestFixtures.teamDirectory(TEAM_ID, USER_ID, 12L, 0L, "12");
        DirectoryNodeResponseVO firstNode = testModel(new DirectoryNodeResponseVO(), Map.of(
                "id", 11L,
                "name", "一级目录",
                "hasChildren", true));
        DirectoryNodeResponseVO secondNode = testModel(new DirectoryNodeResponseVO(), Map.of(
                "id", 12L,
                "name", "空目录",
                "hasChildren", false));
        when(sysFileMapper.selectTeamDirectories(TEAM_ID, 0L)).thenReturn(List.of(first, second));
        when(sysFileMapper.selectTeamParentIdsHavingChildDirectory(TEAM_ID, List.of(11L, 12L)))
                .thenReturn(List.of(11L));
        when(sysFileConverter.toDirectoryNodeVO(first, true)).thenReturn(firstNode);
        when(sysFileConverter.toDirectoryNodeVO(second, false)).thenReturn(secondNode);

        List<DirectoryNodeResponseVO> result = teamFileService.listDirectories(TEAM_ID, null);

        assertThat(result).containsExactly(firstNode, secondNode);
        verify(sysFileMapper).selectTeamDirectories(TEAM_ID, 0L);
        verify(sysFileMapper).selectTeamParentIdsHavingChildDirectory(TEAM_ID, List.of(11L, 12L));
    }

    @Test
    @DisplayName("列出子目录文件：返回根目录和父目录面包屑")
    void listFiles_nestedDirectoryBuildsBreadcrumb() {
        SysFile parent = testModel(
                TeamFileTestFixtures.teamDirectory(TEAM_ID, USER_ID, 11L, 0L, "11"),
                Map.of("originalName", "资料"));
        SysFile child = teamFile(12L, 0);
        FileInfoResponseVO item = testModel(new FileInfoResponseVO(), Map.of("id", 12L));
        FileListResponseVO response = new FileListResponseVO();
        response.setItems(List.of(item));
        when(sysFileMapper.selectTeamFile(TEAM_ID, 11L)).thenReturn(parent);
        when(sysFileMapper.selectTeamChildren(TEAM_ID, 11L)).thenReturn(List.of(child));
        when(sysFileMapper.selectFilesInSpaceByIds("TEAM", TEAM_ID, List.of(11L))).thenReturn(List.of(parent));
        when(sysFileConverter.toFileInfoVOList(List.of(child))).thenReturn(List.of(item));
        when(sysFileConverter.toFileListResponseVO(eq(List.of(item)), anyList())).thenReturn(response);

        FileListResponseVO result = teamFileService.listFiles(TEAM_ID, 11L);

        ArgumentCaptor<List<BreadcrumbItemResponseVO>> breadcrumbCaptor = ArgumentCaptor.forClass(List.class);
        assertThat(result).isSameAs(response);
        verify(sysFileConverter).toFileListResponseVO(eq(List.of(item)), breadcrumbCaptor.capture());
        assertThat(breadcrumbCaptor.getValue())
                .extracting(BreadcrumbItemResponseVO::getName)
                .containsExactly("根目录", "资料");
    }

    @Test
    @DisplayName("获取团队文件详情：补充上传者昵称")
    void getFile_mapsUploaderName() {
        SysFile file = teamFile(12L, 0);
        SysUser uploader = sysUser(USER_ID, "上传者");
        TeamFileResponseVO response = testModel(new TeamFileResponseVO(), Map.of("id", 12L));
        when(sysFileMapper.selectTeamFile(TEAM_ID, 12L)).thenReturn(file);
        when(sysUserMapper.selectBatchIds(List.of(USER_ID))).thenReturn(List.of(uploader));
        when(sysFileConverter.toTeamFileResponseVO(file, "上传者")).thenReturn(response);

        TeamFileResponseVO result = teamFileService.getFile(TEAM_ID, 12L);

        assertThat(result).isSameAs(response);
        verify(sysFileConverter).toTeamFileResponseVO(file, "上传者");
    }

    @Test
    @DisplayName("创建团队目录：委托写服务并补充上传者")
    void createDirectory_returnsTeamFileResponse() {
        DirectoryCreateRequestVO request = testModel(new DirectoryCreateRequestVO(), Map.of("name", "资料"));
        SysFile created = teamFile(13L, 1);
        TeamFileResponseVO response = testModel(new TeamFileResponseVO(), Map.of("id", 13L));
        when(teamFileWriteService.createDirectory(TEAM_ID, request)).thenReturn(created);
        when(sysUserMapper.selectBatchIds(List.of(USER_ID))).thenReturn(List.of(sysUser(USER_ID, "上传者")));
        when(sysFileConverter.toTeamFileResponseVO(created, "上传者")).thenReturn(response);

        TeamFileResponseVO result = teamFileService.createDirectory(TEAM_ID, request);

        assertThat(result).isSameAs(response);
        verify(teamFileWriteService).createDirectory(TEAM_ID, request);
    }

    @Test
    @DisplayName("重命名团队目录：委托写服务并返回转换结果")
    void renameDirectory_returnsTeamFileResponse() {
        DirectoryRenameRequestVO request = testModel(new DirectoryRenameRequestVO(), Map.of("name", "新资料"));
        SysFile renamed = teamFile(13L, 1);
        TeamFileResponseVO response = testModel(new TeamFileResponseVO(), Map.of("id", 13L));
        when(teamFileWriteService.renameDirectory(TEAM_ID, 13L, request)).thenReturn(renamed);
        when(sysUserMapper.selectBatchIds(List.of(USER_ID))).thenReturn(List.of(sysUser(USER_ID, "上传者")));
        when(sysFileConverter.toTeamFileResponseVO(renamed, "上传者")).thenReturn(response);

        TeamFileResponseVO result = teamFileService.renameDirectory(TEAM_ID, 13L, request);

        assertThat(result).isSameAs(response);
        verify(teamFileWriteService).renameDirectory(TEAM_ID, 13L, request);
    }

    @Test
    @DisplayName("上传团队文件：委托写服务并返回转换结果")
    void uploadFile_returnsTeamFileResponse() {
        MockMultipartFile upload = new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes());
        SysFile uploaded = teamFile(14L, 0);
        TeamFileResponseVO response = testModel(new TeamFileResponseVO(), Map.of("id", 14L));
        when(teamFileWriteService.uploadFile(TEAM_ID, upload, 0L)).thenReturn(uploaded);
        when(sysUserMapper.selectBatchIds(List.of(USER_ID))).thenReturn(List.of(sysUser(USER_ID, "上传者")));
        when(sysFileConverter.toTeamFileResponseVO(uploaded, "上传者")).thenReturn(response);

        TeamFileResponseVO result = teamFileService.uploadFile(TEAM_ID, upload, 0L);

        assertThat(result).isSameAs(response);
        verify(teamFileWriteService).uploadFile(TEAM_ID, upload, 0L);
    }

    @Test
    @DisplayName("下载目录：拒绝并不写响应")
    void downloadFile_directoryDenied() {
        SysFile directory = teamFile(12L, 1);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 12L)).thenReturn(directory);

        assertThatThrownBy(() -> teamFileService.downloadFile(TEAM_ID, 12L, response))
                .isInstanceOf(BusinessException.class)
                .extracting("resultCode")
                .isEqualTo(ResultCode.DOWNLOAD_FILE_EXCEPTION);
    }

    @Test
    @DisplayName("下载文件：写入响应")
    void downloadFile_success() {
        SysFile file = teamFile(12L, 0);
        when(sysFileMapper.selectTeamFile(TEAM_ID, 12L)).thenReturn(file);

        teamFileService.downloadFile(TEAM_ID, 12L, response);

        verifyNoInteractions(teamPermissionService);
        verify(fileObjectService).writeToResponse(file, response);
    }

    @Test
    @DisplayName("移动团队文件：委托写服务处理状态变更")
    void moveFile_delegatesToWriteService() {
        teamFileService.moveFile(TEAM_ID, 12L, 20L);

        verify(teamFileWriteService).moveFile(TEAM_ID, 12L, 20L);
    }

    @Test
    @DisplayName("复制团队文件：委托写服务并返回团队文件响应")
    void copyFile_returnsTeamFileResponse() {
        SysFile copied = teamFile(66L, 0);
        TeamFileResponseVO response = testModel(new TeamFileResponseVO(), Map.of("id", 66L));
        when(teamFileWriteService.copyFile(TEAM_ID, 50L, 11L)).thenReturn(copied);
        when(sysUserMapper.selectBatchIds(List.of(USER_ID))).thenReturn(List.of(sysUser(USER_ID, "上传者")));
        when(sysFileConverter.toTeamFileResponseVO(copied, "上传者")).thenReturn(response);

        TeamFileResponseVO result = teamFileService.copyFile(TEAM_ID, 50L, 11L);

        assertThat(result).isSameAs(response);
        verify(teamFileWriteService).copyFile(TEAM_ID, 50L, 11L);
    }

    @Test
    @DisplayName("删除团队文件到回收站：委托写服务处理一致性")
    void deleteToTrash_delegatesToWriteService() {
        teamFileService.deleteToTrash(TEAM_ID, 12L);

        verify(teamFileWriteService).deleteToTrash(TEAM_ID, 12L);
    }

    @Test
    @DisplayName("搜索空关键词：返回空列表")
    void searchFiles_blankKeyword() {
        assertThat(teamFileService.searchFiles(TEAM_ID, " ")).isEmpty();
        verifyNoInteractions(teamPermissionService);
    }

    @Test
    @DisplayName("搜索团队文件：按上传者昵称转换响应")
    void searchFiles_mapsUploaderNames() {
        SysFile found = teamFile(18L, 0);
        TeamFileResponseVO response = testModel(new TeamFileResponseVO(), Map.of("id", 18L));
        when(sysFileMapper.searchTeamFiles(TEAM_ID, "报告")).thenReturn(List.of(found));
        when(sysUserMapper.selectBatchIds(List.of(USER_ID))).thenReturn(List.of(sysUser(USER_ID, "上传者")));
        when(sysFileConverter.toTeamFileResponseVO(found, "上传者")).thenReturn(response);

        List<TeamFileResponseVO> result = teamFileService.searchFiles(TEAM_ID, "报告");

        assertThat(result).containsExactly(response);
        verify(sysFileMapper).searchTeamFiles(TEAM_ID, "报告");
    }

    @Test
    @DisplayName("列出团队回收站：返回回收站根节点")
    void listTrash_returnsDesignTrashItems() {
        LocalDateTime deletedAt = LocalDateTime.of(2026, 5, 6, 21, 0);
        SysFile root = testModel(teamFile(21L, 0), Map.of(
                "originalName", "旧版本.docx",
                "fileSize", 2048L,
                "fullPath", "11,21",
                "deletedAt", deletedAt,
                "expireAt", deletedAt.plusDays(3),
                "inRecycleBin", 1,
                "recycleRoot", 1));

        SysFile parent = testModel(
                TeamFileTestFixtures.teamDirectory(TEAM_ID, USER_ID, 11L, 0L, "11"),
                Map.of("originalName", "项目资料"));

        TeamTrashItemResponseVO response = testModel(new TeamTrashItemResponseVO(), Map.of(
                "id", root.getId(),
                "originalName", root.getOriginalName(),
                "path", "/项目资料/旧版本.docx",
                "deletedAt", root.getDeletedAt(),
                "expireAt", root.getExpireAt(),
                "fileSize", root.getFileSize(),
                "isDirectory", root.getIsDirectory(),
                "status", "IN_TRASH"));

        when(sysFileMapper.selectTeamTrashRoots(TEAM_ID)).thenReturn(List.of(root));
        when(sysFileMapper.selectFilesInSpaceByIds(eq("TEAM"), eq(TEAM_ID), anyCollection()))
                .thenReturn(List.of(parent));
        when(teamTrashConverter.toResponseVO(
                eq(root),
                eq("/项目资料/旧版本.docx"),
                eq(LocalDateTime.of(2026, 5, 6, 21, 0)),
                eq(3L))).thenReturn(response);

        List<TeamTrashItemResponseVO> result = teamFileService.listTrash(TEAM_ID);

        assertThat(result).containsExactly(response);
        verify(sysFileMapper).selectTeamTrashRoots(TEAM_ID);
        verify(teamTrashConverter).toResponseVO(
                eq(root),
                eq("/项目资料/旧版本.docx"),
                eq(LocalDateTime.of(2026, 5, 6, 21, 0)),
                eq(3L));
    }

    @Test
    @DisplayName("列出团队回收站：空列表不查上传者")
    void listTrash_emptyListSkipsUploaderLookup() {
        when(sysFileMapper.selectTeamTrashRoots(TEAM_ID)).thenReturn(Collections.emptyList());

        List<TeamTrashItemResponseVO> result = teamFileService.listTrash(TEAM_ID);

        assertThat(result).isEmpty();
        verify(sysFileMapper).selectTeamTrashRoots(TEAM_ID);
        verifyNoInteractions(sysUserMapper, sysFileConverter, teamTrashConverter);
    }

    @Test
    @DisplayName("列出团队回收站：删除人资料缺失时显示未知")
    void listTrash_missingDeleterBriefUsesUnknownName() {
        SysFile root = testModel(teamFile(21L, 0), Map.of(
                "originalName", "旧版本.docx",
                "fullPath", "21",
                "deletedBy", 99L,
                "inRecycleBin", 1,
                "recycleRoot", 1));
        TeamTrashItemResponseVO response = testModel(new TeamTrashItemResponseVO(), Map.of("id", 21L));
        when(sysFileMapper.selectTeamTrashRoots(TEAM_ID)).thenReturn(List.of(root));
        when(sysUserMapper.selectUserBriefByIds(List.of(99L))).thenReturn(Collections.emptyList());
        when(teamTrashConverter.toResponseVO(eq(root), eq("/旧版本.docx"), eq(root.getDeletedAt()), eq(3L)))
                .thenReturn(response);

        List<TeamTrashItemResponseVO> result = teamFileService.listTrash(TEAM_ID);

        assertThat(result).containsExactly(response);
        assertThat(response.getDeletedByUserId()).isEqualTo(99L);
        assertThat(response.getDeletedByName()).isEqualTo("未知");
    }

    @Test
    @DisplayName("恢复团队回收站：同名冲突转换为对外异常并携带冲突文件")
    void restoreTrash_wrapsNameConflictWithConflictResponse() {
        SysFile conflict = teamFile(31L, 0);
        TeamFileResponseVO conflictResponse = testModel(new TeamFileResponseVO(), Map.of("id", 31L));
        when(teamFileWriteService.restoreFromTrash(TEAM_ID, 21L, ConflictPolicy.RENAME))
                .thenThrow(new TeamFileWriteService.RestoreNameConflictException(conflict));
        when(sysUserMapper.selectBatchIds(List.of(USER_ID))).thenReturn(List.of(sysUser(USER_ID, "上传者")));
        when(sysFileConverter.toTeamFileResponseVO(conflict, "上传者")).thenReturn(conflictResponse);

        assertThatThrownBy(() -> teamFileService.restoreTrash(TEAM_ID, 21L, ConflictPolicy.RENAME))
                .isInstanceOf(TeamFileService.RestoreConflictException.class)
                .extracting("conflictFile")
                .isSameAs(conflictResponse);
    }

    @Test
    @DisplayName("永久删除团队回收站：委托写服务处理一致性")
    void permanentlyDeleteTrash_delegatesToWriteService() {
        teamFileService.permanentlyDeleteTrash(TEAM_ID, 21L);

        verify(teamFileWriteService).permanentlyDeleteTrash(TEAM_ID, 21L);
    }

    @Test
    @DisplayName("列出团队文件树：目录递归设置子节点，文件节点子列表为空")
    void listFileTree_buildsNestedChildren() {
        SysFile dir = testModel(teamFile(31L, 1), Map.of("parentId", 0L));
        SysFile file = testModel(teamFile(32L, 0), Map.of("parentId", 31L));
        FileTreeResponseVO dirNode = testModel(new FileTreeResponseVO(), Map.of(
                "id", 31L,
                "parentId", 0L,
                "isDirectory", 1));
        FileTreeResponseVO fileNode = testModel(new FileTreeResponseVO(), Map.of(
                "id", 32L,
                "parentId", 31L,
                "isDirectory", 0));
        when(sysFileMapper.selectTeamActiveTree(TEAM_ID)).thenReturn(List.of(dir, file));
        when(sysFileConverter.toFileTreeResponseVO(dir)).thenReturn(dirNode);
        when(sysFileConverter.toFileTreeResponseVO(file)).thenReturn(fileNode);

        List<FileTreeResponseVO> result = teamFileService.listFileTree(TEAM_ID);

        assertThat(result).containsExactly(dirNode);
        assertThat(dirNode.getChildren()).containsExactly(fileNode);
        assertThat(fileNode.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("转存个人文件到团队：委托写服务并返回团队文件响应")
    void transferFromPersonal_returnsTeamFileResponse() {
        TransferFromPersonalRequestVO request = testModel(new TransferFromPersonalRequestVO(), Map.of(
                "sourceFileId", 50L,
                "conflictPolicy", ConflictPolicy.RENAME));
        SysFile copied = testModel(teamFile(60L, 0), Map.of("uploaderId", USER_ID));
        TeamFileResponseVO response = testModel(new TeamFileResponseVO(), Map.of("id", 60L));
        when(teamFileWriteService.transferFromPersonal(TEAM_ID, 50L, null, ConflictPolicy.RENAME)).thenReturn(copied);
        when(sysFileConverter.toTeamFileResponseVO(copied, null)).thenReturn(response);

        TeamFileResponseVO result = teamFileService.transferFromPersonal(TEAM_ID, request);

        assertThat(result).isSameAs(response);
        verify(teamFileWriteService).transferFromPersonal(TEAM_ID, 50L, null, ConflictPolicy.RENAME);
    }

    @Test
    @DisplayName("转存团队文件到个人空间：委托写服务并返回个人文件响应")
    void transferToPersonal_returnsPersonalFileResponse() {
        TransferToPersonalRequestVO request = testModel(new TransferToPersonalRequestVO(), Map.of(
                "conflictPolicy", ConflictPolicy.RENAME));
        SysFile copied = personalFile(60L, 0);
        FileInfoResponseVO response = testModel(new FileInfoResponseVO(), Map.of("id", 60L));
        when(teamFileWriteService.transferToPersonal(TEAM_ID, 50L, null, ConflictPolicy.RENAME)).thenReturn(copied);
        when(sysFileConverter.toFileInfoVO(copied)).thenReturn(response);

        FileInfoResponseVO result = teamFileService.transferToPersonal(TEAM_ID, 50L, request);

        assertThat(result).isSameAs(response);
        verify(teamFileWriteService).transferToPersonal(TEAM_ID, 50L, null, ConflictPolicy.RENAME);
    }

    @Test
    @DisplayName("团队只读接口：Controller 声明 PreAuthorize 权限表达式")
    void readEndpointsDeclarePreAuthorize() throws Exception {
        assertPreAuthorize("listDirectories", REQUIRE_FILE_LIST, Long.class, Long.class);
        assertPreAuthorize("listTeamFiles", REQUIRE_FILE_LIST, Long.class, Long.class);
        assertPreAuthorize("getTeamFile", REQUIRE_FILE_DETAIL, Long.class, Long.class);
        assertPreAuthorize("downloadTeamFile", REQUIRE_FILE_DOWNLOAD, Long.class, Long.class, HttpServletResponse.class);
        assertPreAuthorize("searchTeamFiles", REQUIRE_FILE_LIST, Long.class, String.class);
        assertPreAuthorize("listTeamFileTree", REQUIRE_FILE_LIST, Long.class);
        assertControllerPreAuthorize(TeamTrashController.class, "listTrash", REQUIRE_TRASH_LIST, Long.class);
    }

    @Test
    @DisplayName("团队变更接口：Controller 声明 PreAuthorize 权限表达式")
    void mutationEndpointsDeclarePreAuthorize() throws Exception {
        assertPreAuthorize("createTeamDirectory", REQUIRE_FILE_UPLOAD, Long.class, DirectoryCreateRequestVO.class);
        assertPreAuthorize("uploadTeamFile", REQUIRE_FILE_UPLOAD, Long.class, MultipartFile.class, Long.class);
        assertPreAuthorize("moveTeamFile", REQUIRE_FILE_MOVE, Long.class, Long.class, FileMoveRequestVO.class);
        assertPreAuthorize("copyTeamFile", REQUIRE_FILE_COPY, Long.class, Long.class, FileCopyRequestVO.class);
        assertPreAuthorize("transferFromPersonal",
                REQUIRE_FILE_TRANSFER_TO_TEAM, Long.class, TransferFromPersonalRequestVO.class);
        assertPreAuthorize("transferToPersonal",
                REQUIRE_FILE_TRANSFER_TO_PERSONAL, Long.class, Long.class, TransferToPersonalRequestVO.class);
        assertPreAuthorize("deleteToTrash", REQUIRE_FILE_DELETE, Long.class, Long.class);
        assertControllerPreAuthorize(TeamTrashController.class, "permanentlyDeleteTrash",
                REQUIRE_TRASH_DELETE, Long.class, Long.class);
    }

    @Test
    @DisplayName("团队权限表达式：按权限点字符串校验下载权限")
    void requireTeamPerm_checksCurrentUserPermissionByPermissionString() {
        RequireTeamPerm requireTeamPerm = new RequireTeamPerm(teamPermissionService);

        bindTeamIdRequestContext("teamId");
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ACCOUNT_ID);
            assertThat(requireTeamPerm.hasPerm(PERMISSION_FILE_DOWNLOAD)).isTrue();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        verify(teamPermissionService).checkPermission(TEAM_ID, ACCOUNT_ID, PERMISSION_FILE_DOWNLOAD);
    }

    @Test
    @DisplayName("团队权限表达式：权限服务拒绝时返回 false")
    void requireTeamPerm_deniesWhenPermissionServiceDenies() {
        RequireTeamPerm requireTeamPerm = new RequireTeamPerm(teamPermissionService);

        bindTeamIdRequestContext("teamId");
        try (MockedStatic<SecurityUtils> securityUtilsMock = mockStatic(SecurityUtils.class)) {
            securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(USER_ID);
            securityUtilsMock.when(SecurityUtils::getCurrentAccountId).thenReturn(ACCOUNT_ID);
            doThrow(new BusinessException(ResultCode.NO_PERMISSION_TO_USE_API))
                    .when(teamPermissionService)
                    .checkPermission(TEAM_ID, ACCOUNT_ID, PERMISSION_FILE_LIST);

            assertThat(requireTeamPerm.hasPerm(PERMISSION_FILE_LIST)).isFalse();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("团队权限表达式：只有 id 路径变量时拒绝访问")
    void requireTeamPerm_deniesWhenOnlyIdPathVariableExists() {
        RequireTeamPerm requireTeamPerm = new RequireTeamPerm(teamPermissionService);

        bindTeamIdRequestContext("id");
        try {
            assertThat(requireTeamPerm.hasPerm(PERMISSION_FILE_LIST)).isFalse();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        verifyNoInteractions(teamPermissionService);
    }

    private SysFile teamFile(Long id, Integer isDirectory) {
        return TeamFileTestFixtures.teamFile(TEAM_ID, USER_ID, id, isDirectory);
    }

    private SysFile personalFile(Long id, Integer isDirectory) {
        return testModel(TeamFileTestFixtures.teamFile(USER_ID, USER_ID, id, isDirectory), Map.of(
                "spaceType", "PERSONAL",
                "spaceId", USER_ID,
                "userId", USER_ID));
    }

    private SysUser sysUser(Long id, String nickname) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }

    private void bindTeamIdRequestContext(String variableName) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                Map.of(variableName, String.valueOf(TEAM_ID)));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void assertPreAuthorize(String methodName, String expression, Class<?>... parameterTypes)
            throws Exception {
        PreAuthorize annotation = TeamFileController.class.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(methodName + " 权限注解").isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }

    private void assertControllerPreAuthorize(
            Class<?> controllerClass, String methodName, String expression, Class<?>... parameterTypes)
            throws Exception {
        PreAuthorize annotation = controllerClass.getMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertThat(annotation).as(methodName + " 权限注解").isNotNull();
        assertThat(annotation.value()).isEqualTo(expression);
    }

    private static <T> T testModel(T target, Map<String, Object> values) {
        values.forEach((name, value) -> writeField(target, name, value));
        return target;
    }

    private static void writeField(Object target, String name, Object value) {
        try {
            Field field = findField(target.getClass(), name);
            if (!field.canAccess(target)) {
                field.trySetAccessible();
            }
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("测试造数字段不存在: " + name, e);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

}
