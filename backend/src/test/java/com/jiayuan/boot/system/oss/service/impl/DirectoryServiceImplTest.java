package com.jiayuan.boot.system.oss.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.common.result.ResultCode;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryTreeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileMoveRequestVO;
import com.jiayuan.boot.system.oss.service.FileService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DirectoryServiceImpl 单元测试")
class DirectoryServiceImplTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private FileService fileService;

    @InjectMocks
    private DirectoryServiceImpl directoryService;

    private MockedStatic<SecurityUtils> securityUtilsMock;

    @BeforeAll
    static void initMybatisPlusLambda() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysFile.class);
    }

    @BeforeEach
    void setUp() {
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(CURRENT_USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    // ==================== createDirectory ====================

    @Nested
    @DisplayName("createDirectory")
    class CreateDirectoryTests {

        @Test
        @DisplayName("在根目录下正常创建目录")
        void create_inRoot_success() {
            when(sysFileMapper.existsNameInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(false);
            SysFile dir = new SysFile();
            dir.setId(10L);
            when(sysFileConverter.toSysFile(any())).thenReturn(dir);
            FileInfoResponseVO vo = new FileInfoResponseVO();
            vo.setId(10L);
            when(sysFileConverter.toFileInfoVO(any())).thenReturn(vo);

            DirectoryCreateRequestVO req = new DirectoryCreateRequestVO();
            req.setName("我的文档");
            req.setParentId(0L);

            FileInfoResponseVO result = directoryService.createDirectory(req);

            assertThat(result.getId()).isEqualTo(10L);
            verify(sysFileMapper).insert(any());
            verify(sysFileMapper).updateById(any());
        }

        @Test
        @DisplayName("在子目录下创建目录")
        void create_inSubDir_success() {
            SysFile parent = new SysFile();
            parent.setId(5L);
            parent.setIsDirectory(1);
            parent.setUserId(CURRENT_USER_ID);
            parent.setSpaceType("PERSONAL");
            parent.setSpaceId(CURRENT_USER_ID);
            parent.setUploaderId(CURRENT_USER_ID);
            parent.setFullPath("5");
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 5L)).thenReturn(parent);
            when(sysFileMapper.existsNameInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(false);

            SysFile dir = new SysFile();
            dir.setId(11L);
            when(sysFileConverter.toSysFile(any())).thenReturn(dir);
            FileInfoResponseVO vo = new FileInfoResponseVO();
            vo.setId(11L);
            when(sysFileConverter.toFileInfoVO(any())).thenReturn(vo);

            DirectoryCreateRequestVO req = new DirectoryCreateRequestVO();
            req.setName("子目录");
            req.setParentId(5L);

            directoryService.createDirectory(req);

            assertThat(dir.getFullPath()).isEqualTo("5,11");
        }

        @Test
        @DisplayName("同名冲突：抛 USER_REQUEST_PARAMETER_ERROR")
        void create_duplicateName_throws() {
            when(sysFileMapper.existsNameInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(true);

            DirectoryCreateRequestVO req = new DirectoryCreateRequestVO();
            req.setName("已存在");
            req.setParentId(0L);

            assertThatThrownBy(() -> directoryService.createDirectory(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

            verify(sysFileMapper, never()).insert(any());
        }

        @Test
        @DisplayName("父目录不存在：抛 USER_REQUEST_PARAMETER_ERROR")
        void create_parentNotFound_throws() {
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 999L)).thenReturn(null);

            DirectoryCreateRequestVO req = new DirectoryCreateRequestVO();
            req.setName("新目录");
            req.setParentId(999L);

            assertThatThrownBy(() -> directoryService.createDirectory(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        }

        @Test
        @DisplayName("父目录属于他人：抛 ACCESS_UNAUTHORIZED")
        void create_parentOwnedByOther_throws() {
            SysFile parent = new SysFile();
            parent.setId(5L);
            parent.setIsDirectory(1);
            parent.setUserId(OTHER_USER_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 5L)).thenReturn(parent);

            DirectoryCreateRequestVO req = new DirectoryCreateRequestVO();
            req.setName("新目录");
            req.setParentId(5L);

            assertThatThrownBy(() -> directoryService.createDirectory(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.ACCESS_UNAUTHORIZED);
        }

        @Test
        @DisplayName("团队目录上传者不能通过个人目录入口创建子目录")
        void create_parentIsTeamDirectory_throws() {
            SysFile parent = new SysFile();
            parent.setId(5L);
            parent.setIsDirectory(1);
            parent.setUserId(CURRENT_USER_ID);
            parent.setSpaceType("TEAM");
            parent.setSpaceId(88L);
            parent.setUploaderId(CURRENT_USER_ID);
            when(sysFileMapper.selectById(5L)).thenReturn(parent);

            DirectoryCreateRequestVO req = new DirectoryCreateRequestVO();
            req.setName("新目录");
            req.setParentId(5L);

            assertThatThrownBy(() -> directoryService.createDirectory(req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        }
    }

    // ==================== renameDirectory ====================

    @Nested
    @DisplayName("renameDirectory")
    class RenameDirectoryTests {

        @Test
        @DisplayName("正常重命名目录")
        void rename_success() {
            SysFile dir = buildDir(10L, CURRENT_USER_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 10L)).thenReturn(dir);
            when(sysFileMapper.existsNameInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(false);
            FileInfoResponseVO vo = new FileInfoResponseVO();
            vo.setId(10L);
            when(sysFileConverter.toFileInfoVO(any())).thenReturn(vo);

            DirectoryRenameRequestVO req = new DirectoryRenameRequestVO();
            req.setName("新名称");

            FileInfoResponseVO result = directoryService.renameDirectory(10L, req);

            assertThat(result.getId()).isEqualTo(10L);
            verify(sysFileMapper).updateById(any());
        }

        @Test
        @DisplayName("重命名同名冲突：抛 USER_REQUEST_PARAMETER_ERROR")
        void rename_duplicateName_throws() {
            SysFile dir = buildDir(10L, CURRENT_USER_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 10L)).thenReturn(dir);
            when(sysFileMapper.existsNameInSpaceDirectory(anyString(), anyLong(), anyLong(), anyString()))
                    .thenReturn(true);

            DirectoryRenameRequestVO req = new DirectoryRenameRequestVO();
            req.setName("已存在");

            assertThatThrownBy(() -> directoryService.renameDirectory(10L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
        }

        @Test
        @DisplayName("重命名他人目录：抛 ACCESS_UNAUTHORIZED")
        void rename_otherUser_throws() {
            SysFile dir = buildDir(10L, OTHER_USER_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 10L)).thenReturn(dir);

            DirectoryRenameRequestVO req = new DirectoryRenameRequestVO();
            req.setName("新名称");

            assertThatThrownBy(() -> directoryService.renameDirectory(10L, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.ACCESS_UNAUTHORIZED);
        }
    }

    // ==================== deleteDirectory ====================

    @Nested
    @DisplayName("deleteDirectory")
    class DeleteDirectoryTests {

        @Test
        @DisplayName("正常删除目录：委托 FileService")
        void delete_success() {
            SysFile dir = buildDir(10L, CURRENT_USER_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 10L)).thenReturn(dir);
            when(fileService.deleteFileById(10L)).thenReturn(true);

            boolean result = directoryService.deleteDirectory(10L);

            assertThat(result).isTrue();
            verify(fileService).deleteFileById(10L);
        }

        @Test
        @DisplayName("删除不存在的目录：抛 USER_REQUEST_PARAMETER_ERROR")
        void delete_notFound_throws() {
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 999L)).thenReturn(null);

            assertThatThrownBy(() -> directoryService.deleteDirectory(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);

            verify(fileService, never()).deleteFileById(anyLong());
        }

        @Test
        @DisplayName("删除他人目录：抛 ACCESS_UNAUTHORIZED")
        void delete_otherUser_throws() {
            SysFile dir = buildDir(10L, OTHER_USER_ID);
            when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 10L)).thenReturn(dir);

            assertThatThrownBy(() -> directoryService.deleteDirectory(10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.ACCESS_UNAUTHORIZED);
        }

        @Test
        @DisplayName("私密目录不能通过个人目录入口删除")
        void delete_privateDirectory_throws() {
            SysFile dir = buildDir(10L, CURRENT_USER_ID);
            dir.setSpaceType("PRIVATE");
            dir.setSpaceId(CURRENT_USER_ID);
            dir.setUploaderId(CURRENT_USER_ID);
            when(sysFileMapper.selectById(10L)).thenReturn(dir);

            assertThatThrownBy(() -> directoryService.deleteDirectory(10L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("resultCode")
                    .isEqualTo(ResultCode.USER_REQUEST_PARAMETER_ERROR);
            verify(fileService, never()).deleteFileById(anyLong());
        }
    }

    @Test
    @DisplayName("移动目录：校验个人目录后委托 FileService 移动")
    void moveDirectory_delegatesToFileService() {
        SysFile dir = buildDir(10L, CURRENT_USER_ID);
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 10L)).thenReturn(dir);
        FileMoveRequestVO request = new FileMoveRequestVO();
        request.setTargetDirectoryId(3L);

        directoryService.moveDirectory(10L, request);

        verify(fileService).moveFile(10L, 3L);
    }

    @Test
    @DisplayName("目录树：按 parentId 递归组装 children")
    void listDirectoryTree_buildsRecursiveTree() {
        SysFile root = buildDir(10L, CURRENT_USER_ID);
        SysFile child = buildDir(11L, CURRENT_USER_ID);
        child.setParentId(10L);
        DirectoryTreeResponseVO rootVo = treeNode(10L, 0L);
        DirectoryTreeResponseVO childVo = treeNode(11L, 10L);
        when(sysFileMapper.selectPersonalDirectoryTree(CURRENT_USER_ID)).thenReturn(List.of(root, child));
        when(sysFileConverter.toDirectoryTreeResponseVO(root)).thenReturn(rootVo);
        when(sysFileConverter.toDirectoryTreeResponseVO(child)).thenReturn(childVo);

        List<DirectoryTreeResponseVO> result = directoryService.listDirectoryTree();

        assertThat(result).containsExactly(rootVo);
        assertThat(result.get(0).getChildren()).containsExactly(childVo);
        assertThat(childVo.getChildren()).isEmpty();
    }

    // ==================== helpers ====================

    private SysFile buildDir(Long id, Long userId) {
        SysFile dir = new SysFile();
        dir.setId(id);
        dir.setUserId(userId);
        dir.setSpaceType("PERSONAL");
        dir.setSpaceId(userId);
        dir.setUploaderId(userId);
        dir.setOriginalName("目录");
        dir.setIsDirectory(1);
        dir.setParentId(0L);
        return dir;
    }

    private DirectoryTreeResponseVO treeNode(Long id, Long parentId) {
        DirectoryTreeResponseVO vo = new DirectoryTreeResponseVO();
        vo.setId(id);
        vo.setParentId(parentId);
        return vo;
    }
}
