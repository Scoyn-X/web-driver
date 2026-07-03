package com.jiayuan.boot.system.oss.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jiayuan.boot.common.exception.BusinessException;
import com.jiayuan.boot.system.oss.converter.SysFileConverter;
import com.jiayuan.boot.system.oss.mapper.SysFileMapper;
import com.jiayuan.boot.system.oss.mapper.SysFileObjectMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.entity.SysFileObject;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.quota.service.QuotaService;
import com.jiayuan.boot.system.security.util.SecurityUtils;
import io.minio.MinioClient;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MinioFileServiceImpl#listFiles(Long)} 单元测试，覆盖面包屑构造的核心分支。
 *
 * @author charleslam
 * @since 2026/04/16
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MinioFileServiceImpl listFiles + breadcrumb")
class MinioFileServiceImplListFilesTest {

    private static final Long CURRENT_USER_ID = 1L;

    @Mock private SysFileMapper sysFileMapper;
    @Mock private SysFileConverter sysFileConverter;
    @Mock private QuotaService quotaService;
    @Mock private SysFileObjectMapper sysFileObjectMapper;
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
        securityUtilsMock = mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getCurrentUserId).thenReturn(CURRENT_USER_ID);
    }

    @AfterEach
    void tearDown() {
        securityUtilsMock.close();
    }

    @Test
    @DisplayName("根目录：面包屑仅含「根目录」，不发起父目录查询")
    void rootListing_breadcrumbHasRootOnly() {
        when(sysFileMapper.selectPersonalChildren(CURRENT_USER_ID, 0L)).thenReturn(Collections.emptyList());
        when(sysFileConverter.toFileInfoVOList(any())).thenReturn(Collections.emptyList());

        FileListResponseVO result = service.listFiles(0L);

        assertThat(result.getBreadcrumb()).hasSize(1);
        assertThat(result.getBreadcrumb().get(0).getId()).isZero();
        assertThat(result.getBreadcrumb().get(0).getName()).isEqualTo("根目录");
        verify(sysFileMapper, never()).selectPersonalFile(anyLong(), anyLong());
    }

    @Test
    @DisplayName("嵌套目录 /A/B：面包屑 = [根目录, A, B]")
    void nestedListing_breadcrumbBuildsFromFullPath() {
        SysFile parentB = dirEntity(20L, "B");
        parentB.setFullPath("10,20");
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 20L)).thenReturn(parentB);

        SysFile a = dirEntity(10L, "A");
        when(sysFileMapper.selectFilesInSpaceByIds("PERSONAL", CURRENT_USER_ID, List.of(10L, 20L)))
                .thenReturn(List.of(a, parentB));

        when(sysFileMapper.selectPersonalChildren(CURRENT_USER_ID, 20L)).thenReturn(Collections.emptyList());
        when(sysFileConverter.toFileInfoVOList(any())).thenReturn(Collections.emptyList());

        FileListResponseVO result = service.listFiles(20L);

        List<BreadcrumbItemResponseVO> crumbs = result.getBreadcrumb();
        assertThat(crumbs).hasSize(3);
        assertThat(crumbs.get(0).getId()).isZero();
        assertThat(crumbs.get(0).getName()).isEqualTo("根目录");
        assertThat(crumbs.get(1).getId()).isEqualTo(10L);
        assertThat(crumbs.get(1).getName()).isEqualTo("A");
        assertThat(crumbs.get(2).getId()).isEqualTo(20L);
        assertThat(crumbs.get(2).getName()).isEqualTo("B");
    }

    @Test
    @DisplayName("祖先名字缺失：用 ? 占位")
    void nestedListing_missingAncestor_placeholder() {
        SysFile parentB = dirEntity(20L, "B");
        parentB.setFullPath("10,20");
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 20L)).thenReturn(parentB);

        // 仅返回 B 自身，A(10) 缺失
        when(sysFileMapper.selectFilesInSpaceByIds("PERSONAL", CURRENT_USER_ID, List.of(10L, 20L)))
                .thenReturn(List.of(parentB));

        when(sysFileMapper.selectPersonalChildren(CURRENT_USER_ID, 20L)).thenReturn(Collections.emptyList());
        when(sysFileConverter.toFileInfoVOList(any())).thenReturn(Collections.emptyList());

        FileListResponseVO result = service.listFiles(20L);

        assertThat(result.getBreadcrumb()).hasSize(3);
        assertThat(result.getBreadcrumb().get(1).getName()).isEqualTo("?");
    }

    @Test
    @DisplayName("父目录不存在：拒绝列目录，避免跨用户 parentId 枚举")
    void parentNotFound_deniedBeforeListing() {
        when(sysFileMapper.selectPersonalFile(CURRENT_USER_ID, 99L)).thenReturn(null);

        assertThatThrownBy(() -> service.listFiles(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标目录不存在");

        verify(sysFileMapper, never()).selectPersonalChildren(anyLong(), anyLong());
    }

    @Test
    @DisplayName("items 字段透传 converter 的转换结果")
    void items_passthroughConverter() {
        SysFile child = new SysFile();
        child.setId(50L);
        when(sysFileMapper.selectPersonalChildren(CURRENT_USER_ID, 0L)).thenReturn(List.of(child));
        FileInfoResponseVO vo = new FileInfoResponseVO();
        vo.setId(50L);
        when(sysFileConverter.toFileInfoVOList(any())).thenReturn(List.of(vo));

        FileListResponseVO result = service.listFiles(0L);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getId()).isEqualTo(50L);
    }

    private SysFile dirEntity(Long id, String name) {
        SysFile d = new SysFile();
        d.setId(id);
        d.setUserId(CURRENT_USER_ID);
        d.setSpaceType("PERSONAL");
        d.setSpaceId(CURRENT_USER_ID);
        d.setUploaderId(CURRENT_USER_ID);
        d.setOriginalName(name);
        d.setIsDirectory(1);
        return d;
    }
}
