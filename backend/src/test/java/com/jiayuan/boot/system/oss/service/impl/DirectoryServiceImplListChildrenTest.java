package com.jiayuan.boot.system.oss.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.service.FileService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DirectoryServiceImpl#listChildDirectories(Long)} 单元测试。
 * <p>
 * 验证目录树懒加载的查询拼装、hasChildren 探测、空集短路与回收站排除（后者由查询条件保证）。
 *
 * @author charleslam
 * @since 2026/04/16
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DirectoryServiceImpl 子目录懒加载")
class DirectoryServiceImplListChildrenTest {

    private static final Long CURRENT_USER_ID = 1L;

    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private FileService fileService;

    @InjectMocks
    private DirectoryServiceImpl service;

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

        when(sysFileConverter.toDirectoryNodeVO(any(), anyBoolean())).thenAnswer(inv -> {
            SysFile f = inv.getArgument(0);
            boolean hasChildren = inv.getArgument(1);
            DirectoryNodeResponseVO vo = new DirectoryNodeResponseVO();
            vo.setId(f.getId());
            vo.setName(f.getOriginalName());
            vo.setParentId(f.getParentId());
            vo.setHasChildren(hasChildren);
            return vo;
        });
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    @DisplayName("无子目录：返回空列表，且不发起 hasChildren 探测")
    void empty_noLookup() {
        when(sysFileMapper.selectPersonalDirectories(CURRENT_USER_ID, 0L)).thenReturn(Collections.emptyList());

        List<DirectoryNodeResponseVO> result = service.listChildDirectories(0L);

        assertThat(result).isEmpty();
        verify(sysFileMapper, never()).selectParentIdsHavingChildDirectory(anyLong(), any());
    }

    @Test
    @DisplayName("叶子子目录：hasChildren=false")
    void leafChildren_falseFlag() {
        SysFile a = dir(10L, "A", 0L);
        SysFile b = dir(11L, "B", 0L);
        when(sysFileMapper.selectPersonalDirectories(CURRENT_USER_ID, 0L)).thenReturn(List.of(a, b));
        when(sysFileMapper.selectParentIdsHavingChildDirectory(eq(CURRENT_USER_ID), any()))
                .thenReturn(Collections.emptyList());

        List<DirectoryNodeResponseVO> result = service.listChildDirectories(0L);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(node -> assertThat(node.getHasChildren()).isFalse());
        assertThat(result).extracting(DirectoryNodeResponseVO::getName).containsExactly("A", "B");
    }

    @Test
    @DisplayName("混合：仅含子目录的父被标记 hasChildren=true")
    void mixedChildren_correctFlags() {
        SysFile a = dir(10L, "A", 0L);   // 有子目录
        SysFile b = dir(11L, "B", 0L);   // 无子目录
        when(sysFileMapper.selectPersonalDirectories(CURRENT_USER_ID, 0L)).thenReturn(List.of(a, b));
        when(sysFileMapper.selectParentIdsHavingChildDirectory(eq(CURRENT_USER_ID), any()))
                .thenReturn(List.of(10L));

        List<DirectoryNodeResponseVO> result = service.listChildDirectories(0L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getHasChildren()).isTrue();
        assertThat(result.get(1).getId()).isEqualTo(11L);
        assertThat(result.get(1).getHasChildren()).isFalse();
    }

    @Test
    @DisplayName("parentId=null 视为根目录（防御性）")
    void nullParent_treatedAsRoot() {
        when(sysFileMapper.selectPersonalDirectories(CURRENT_USER_ID, 0L)).thenReturn(Collections.emptyList());

        List<DirectoryNodeResponseVO> result = service.listChildDirectories(null);

        assertThat(result).isEmpty();
        verify(sysFileMapper).selectPersonalDirectories(CURRENT_USER_ID, 0L);
    }

    private SysFile dir(Long id, String name, Long parentId) {
        SysFile d = new SysFile();
        d.setId(id);
        d.setUserId(CURRENT_USER_ID);
        d.setSpaceType("PERSONAL");
        d.setSpaceId(CURRENT_USER_ID);
        d.setUploaderId(CURRENT_USER_ID);
        d.setOriginalName(name);
        d.setParentId(parentId);
        d.setIsDirectory(1);
        d.setInRecycleBin(0);
        return d;
    }
}
