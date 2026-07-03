package com.jiayuan.boot.system.team.converter;

import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.team.model.bo.TeamTrashItemBuildBO;
import com.jiayuan.boot.system.team.model.vo.TeamTrashItemResponseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

/**
 * 团队回收站对象转换器。
 *
 * @author charleslam
 * @since 2026/05/21
 */
@Mapper(componentModel = "spring")
public interface TeamTrashConverter {

    /**
     * 转换团队回收站列表项。
     *
     * @param build 团队回收站列表项构造参数
     * @return 团队回收站列表项响应对象
     */
    @Mapping(target = "id", source = "file.id")
    @Mapping(target = "originalName", source = "file.originalName")
    @Mapping(target = "deletedAt", source = "deletedAt")
    @Mapping(target = "fileSize", expression = "java(build.getFile().getFileSize() == null ? 0L : build.getFile().getFileSize())")
    @Mapping(target = "isDirectory", source = "file.isDirectory")
    TeamTrashItemResponseVO toResponseVO(TeamTrashItemBuildBO build);

    /**
     * 转换团队回收站列表项。
     *
     * @param file          团队回收站文件元数据
     * @param path          人类可读完整路径
     * @param deletedAt     放入回收站时间
     * @param retentionDays 回收站保留天数（兼容旧调用，实际以持久化 expireAt 为准）
     * @return 团队回收站列表项响应对象
     */
    @Mapping(target = "id", source = "file.id")
    @Mapping(target = "originalName", source = "file.originalName")
    @Mapping(target = "path", source = "path")
    @Mapping(target = "deletedByName", expression = "java(null)")
    @Mapping(target = "deletedAt", source = "file.deletedAt")
    @Mapping(target = "expireAt", source = "file.expireAt")
    @Mapping(target = "fileSize", expression = "java(file.getFileSize() == null ? 0L : file.getFileSize())")
    @Mapping(target = "isDirectory", source = "file.isDirectory")
    @Mapping(target = "status", constant = "IN_TRASH")
    TeamTrashItemResponseVO toResponseVO(SysFile file, String path, LocalDateTime deletedAt, long retentionDays);
}
