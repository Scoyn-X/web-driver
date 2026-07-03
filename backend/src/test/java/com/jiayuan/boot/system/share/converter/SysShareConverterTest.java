package com.jiayuan.boot.system.share.converter;

import com.jiayuan.boot.system.share.model.entity.SysShare;
import com.jiayuan.boot.system.share.model.vo.ShareInfoResponseVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SysShareConverter} 单元测试（MapStruct 基础字段映射）。
 *
 * @author charleslam
 * @since 2026/04/14
 */
@DisplayName("SysShareConverter 单元测试")
class SysShareConverterTest {

    private final SysShareConverter converter = Mappers.getMapper(SysShareConverter.class);

    @Test
    @DisplayName("基础字段逐一复制")
    void toShareInfoVO_copiesAllFields() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 14, 10, 30);
        SysShare share = new SysShare();
        share.setId(1L);
        share.setFileId(100L);
        share.setShareToken("token123");
        share.setAccessType(1);
        share.setExtractCode("ABCD");
        share.setExpireTime(now.plusDays(7));
        share.setCreateTime(now);

        ShareInfoResponseVO vo = converter.toShareInfoVO(share);

        assertThat(vo.getId()).isEqualTo(1L);
        assertThat(vo.getFileId()).isEqualTo(100L);
        assertThat(vo.getShareToken()).isEqualTo("token123");
        assertThat(vo.getAccessType()).isEqualTo(1);
        assertThat(vo.getExtractCode()).isEqualTo("ABCD");
        assertThat(vo.getExpireTime()).isEqualTo(now.plusDays(7));
        assertThat(vo.getCreateTime()).isEqualTo(now);
        assertThat(vo.getIsDirectory()).isNull();
    }

    @Test
    @DisplayName("fileName / statusDesc / isDirectory 不由基础 converter 填写（由 service 层补充）")
    void toShareInfoVO_ignoresServiceLayerFields() {
        SysShare share = new SysShare();
        share.setId(1L);

        ShareInfoResponseVO vo = converter.toShareInfoVO(share);

        assertThat(vo.getFileName()).isNull();
        assertThat(vo.getStatusDesc()).isNull();
        assertThat(vo.getIsDirectory()).isNull();
    }

    @Test
    @DisplayName("分享展示字段由带上下文 converter 入参映射")
    void toShareInfoVO_withDisplayContext_mapsDirectoryFlag() {
        SysShare share = new SysShare();
        share.setId(1L);

        ShareInfoResponseVO vo = converter.toShareInfoVO(share, "文档", "有效", true);

        assertThat(vo.getFileName()).isEqualTo("文档");
        assertThat(vo.getStatusDesc()).isEqualTo("有效");
        assertThat(vo.getIsDirectory()).isTrue();
    }

    @Test
    @DisplayName("null 输入返 null")
    void toShareInfoVO_nullInput_returnsNull() {
        assertThat(converter.toShareInfoVO(null)).isNull();
    }
}
