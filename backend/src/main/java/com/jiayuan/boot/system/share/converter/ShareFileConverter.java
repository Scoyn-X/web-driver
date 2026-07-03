package com.jiayuan.boot.system.share.converter;

import com.jiayuan.boot.common.util.StringUtils;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import org.mapstruct.Context;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.ArrayList;
import java.util.List;

/**
 * 匿名分享目录文件转换器。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Mapper(componentModel = "spring")
public interface ShareFileConverter {

    /**
     * 转换分享目录文件响应，并裁剪分享根目录外路径。
     */
    @Mapping(target = "fullPath",
            expression = "java(com.jiayuan.boot.system.share.converter.ShareFileConverter.toSharedPath(sysFile.getFullPath(), rootId))")
    @Mapping(target = "fileUrl", ignore = true)
    @Named("toSharedFileInfoVO")
    FileInfoResponseVO toSharedFileInfoVO(SysFile sysFile, @Context Long rootId);

    /**
     * 批量转换分享目录文件响应。
     */
    @IterableMapping(qualifiedByName = "toSharedFileInfoVO")
    List<FileInfoResponseVO> toSharedFileInfoVOList(List<SysFile> sysFiles, @Context Long rootId);

    /**
     * 转换文件列表响应信封。
     */
    FileListResponseVO toFileListResponseVO(List<FileInfoResponseVO> items,
                                            List<BreadcrumbItemResponseVO> breadcrumb);

    /**
     * 将完整路径裁剪为分享根目录起始的相对路径。
     */
    static List<Long> toSharedPath(String fullPath, Long rootId) {
        List<Long> path = StringUtils.parseIdList(fullPath);
        int rootIndex = path.indexOf(rootId);
        if (rootIndex < 0) {
            return new ArrayList<>();
        }
        return path.subList(rootIndex, path.size());
    }
}
