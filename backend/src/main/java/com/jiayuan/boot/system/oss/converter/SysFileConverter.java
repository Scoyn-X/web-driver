package com.jiayuan.boot.system.oss.converter;

import com.jiayuan.boot.common.util.StringUtils;
import com.jiayuan.boot.system.oss.model.bo.FileCloneBuildBO;
import com.jiayuan.boot.system.oss.model.bo.PrivateFileBuildBO;
import com.jiayuan.boot.system.oss.model.bo.StoredFileObjectBO;
import com.jiayuan.boot.system.oss.model.bo.TeamDirectoryBuildBO;
import com.jiayuan.boot.system.oss.model.bo.TeamFileBuildBO;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.oss.model.vo.BreadcrumbItemResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryTreeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileListResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileTreeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.RecycleBinItemResponseVO;
import com.jiayuan.boot.system.team.model.vo.TeamFileResponseVO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件对象转换器（MapStruct）
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Mapper(componentModel = "spring")
public interface SysFileConverter {

    @Mapping(target = "fullPath", source = "fullPath", qualifiedByName = "toParsedIdList")
    FileInfoResponseVO toFileInfoVO(SysFile sysFile);

    List<FileInfoResponseVO> toFileInfoVOList(List<SysFile> sysFiles);

    /**
     * 转换文件列表响应信封。
     *
     * @param items      文件列表项
     * @param breadcrumb 面包屑
     * @return 文件列表响应
     */
    @Mapping(target = "items", source = "items")
    @Mapping(target = "breadcrumb", source = "breadcrumb")
    FileListResponseVO toFileListResponseVO(List<FileInfoResponseVO> items,
                                            List<BreadcrumbItemResponseVO> breadcrumb);

    /**
     * 转换团队文件响应对象。
     *
     * @param sysFile      文件元数据
     * @param uploaderName 上传者名称
     * @return 团队文件响应对象
     */
    @Mapping(target = "teamId", source = "sysFile.spaceId")
    @Mapping(target = "uploaderId", source = "sysFile.uploaderId")
    @Mapping(target = "fullPath", source = "sysFile.fullPath", qualifiedByName = "toParsedIdList")
    TeamFileResponseVO toTeamFileResponseVO(SysFile sysFile, String uploaderName);

    /**
     * 转换已保存的物理文件对象信息。
     *
     * @param file       上传文件
     * @param fileHash   文件指纹
     * @param objectPath MinIO 对象路径
     * @param fileUrl    文件访问 URL
     * @return 已保存的物理文件对象信息
     */
    @Mapping(target = "storedName", expression = "java(objectPath.substring(objectPath.lastIndexOf('/') + 1))")
    @Mapping(target = "fileSize", expression = "java(file.getSize())")
    @Mapping(target = "mimeType", expression = "java(file.getContentType())")
    StoredFileObjectBO toStoredFileObjectBO(MultipartFile file,
                                            String fileHash,
                                            String objectPath,
                                            String fileUrl);

    @Mapping(target = "fullPath", source = "fullPath", qualifiedByName = "toParsedIdList")
    @Mapping(target = "children", ignore = true)
    FileTreeResponseVO toFileTreeResponseVO(SysFile sysFile);

    @Mapping(target = "originalName", source = "name")
    @Mapping(target = "storedName", constant = "")
    @Mapping(target = "filePath", constant = "")
    @Mapping(target = "fileUrl", constant = "")
    @Mapping(target = "fileSize", constant = "0L")
    @Mapping(target = "mimeType", ignore = true)
    @Mapping(target = "isDirectory", constant = "1")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "fullPath", ignore = true)
    SysFile toSysFile(com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO vo);

    /**
     * SysFile → DirectoryNodeVO：用于目录树懒加载节点。
     * hasChildren 由 Service 层计算后传入。
     */
    @Mapping(target = "name", source = "sysFile.originalName")
    DirectoryNodeResponseVO toDirectoryNodeVO(SysFile sysFile, boolean hasChildren);

    /**
     * SysFile → DirectoryTreeResponseVO：用于目录树完整加载。
     * children 由 Service 层递归构建后设置。
     */
    @Mapping(target = "name", source = "sysFile.originalName")
    @Mapping(target = "children", ignore = true)
    DirectoryTreeResponseVO toDirectoryTreeResponseVO(SysFile sysFile);

    /**
     * SysFile → RecycleBinItemVO：回收站列表项。
     * path 由 Service 层预计算后传入。
     */
    @Mapping(target = "id", source = "f.id")
    @Mapping(target = "originalName", source = "f.originalName")
    @Mapping(target = "fileSize", expression = "java(f.getFileSize() == null ? 0L : f.getFileSize())")
    @Mapping(target = "isDirectory", source = "f.isDirectory")
    @Mapping(target = "deletedAt", source = "f.deletedAt")
    @Mapping(target = "path", source = "path")
    RecycleBinItemResponseVO toRecycleBinItemVO(SysFile f, String path);

    /**
     * 委托 StringUtils 解析ID列表
     */
    @Named("toParsedIdList")
    static List<Long> toParsedIdList(String fullPath) {
        return StringUtils.parseIdList(fullPath);
    }

    /**
     * 转换团队目录元数据。
     *
     * @param build 团队目录构造参数
     * @return 团队目录元数据
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "fullPath", ignore = true)
    @Mapping(target = "storedName", constant = "")
    @Mapping(target = "filePath", constant = "")
    @Mapping(target = "fileUrl", constant = "")
    @Mapping(target = "fileSize", constant = "0L")
    @Mapping(target = "mimeType", ignore = true)
    @Mapping(target = "fileHash", ignore = true)
    @Mapping(target = "isDirectory", constant = "1")
    @Mapping(target = "userId", source = "uploaderId")
    @Mapping(target = "spaceType", constant = "TEAM")
    @Mapping(target = "spaceId", source = "teamId")
    @Mapping(target = "inRecycleBin", constant = "0")
    @Mapping(target = "recycleRoot", constant = "0")
    SysFile toTeamDirectory(TeamDirectoryBuildBO build);

    /**
     * 转换团队上传文件元数据。
     *
     * @param build 团队上传文件构造参数
     * @return 团队文件元数据
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "fullPath", ignore = true)
    @Mapping(target = "storedName", source = "object.storedName")
    @Mapping(target = "filePath", source = "object.objectPath")
    @Mapping(target = "fileUrl", source = "object.fileUrl")
    @Mapping(target = "fileSize", source = "object.fileSize")
    @Mapping(target = "mimeType", source = "object.mimeType")
    @Mapping(target = "fileHash", source = "object.fileHash")
    @Mapping(target = "isDirectory", constant = "0")
    @Mapping(target = "userId", source = "uploaderId")
    @Mapping(target = "spaceType", constant = "TEAM")
    @Mapping(target = "spaceId", source = "teamId")
    @Mapping(target = "inRecycleBin", constant = "0")
    @Mapping(target = "recycleRoot", constant = "0")
    SysFile toTeamUploadedFile(TeamFileBuildBO build);

    /**
     * 转换私密空间目录元数据。
     *
     * @param build 私密空间目录构造参数
     * @return 私密空间目录元数据
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "originalName", source = "originalName")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "spaceType", constant = "PRIVATE")
    @Mapping(target = "spaceId", source = "userId")
    @Mapping(target = "uploaderId", source = "userId")
    @Mapping(target = "storedName", constant = "")
    @Mapping(target = "filePath", constant = "")
    @Mapping(target = "fileUrl", constant = "")
    @Mapping(target = "fileSize", constant = "0L")
    @Mapping(target = "isDirectory", constant = "1")
    @Mapping(target = "inRecycleBin", constant = "0")
    @Mapping(target = "recycleRoot", constant = "0")
    SysFile toPrivateDirectory(PrivateFileBuildBO build);

    /**
     * 转换私密空间上传文件元数据。
     *
     * @param build 私密空间上传文件构造参数
     * @return 私密空间文件元数据
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "originalName", source = "originalName")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "parentId", source = "parentId")
    @Mapping(target = "spaceType", constant = "PRIVATE")
    @Mapping(target = "spaceId", source = "userId")
    @Mapping(target = "uploaderId", source = "userId")
    @Mapping(target = "storedName", source = "object.storedName")
    @Mapping(target = "filePath", source = "object.objectPath")
    @Mapping(target = "fileUrl", source = "object.fileUrl")
    @Mapping(target = "fileSize", source = "object.fileSize")
    @Mapping(target = "mimeType", source = "object.mimeType")
    @Mapping(target = "fileHash", source = "object.fileHash")
    @Mapping(target = "isDirectory", constant = "0")
    @Mapping(target = "inRecycleBin", constant = "0")
    @Mapping(target = "recycleRoot", constant = "0")
    SysFile toPrivateUploadedFile(PrivateFileBuildBO build);

    /**
     * 转换逻辑复制文件元数据。
     *
     * @param build 复制构造参数
     * @return 新文件元数据
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "fullPath", ignore = true)
    @Mapping(target = "storedName", source = "source.storedName")
    @Mapping(target = "filePath", source = "source.filePath")
    @Mapping(target = "fileUrl", source = "source.fileUrl")
    @Mapping(target = "fileSize", source = "source.fileSize")
    @Mapping(target = "mimeType", source = "source.mimeType")
    @Mapping(target = "fileHash", source = "source.fileHash")
    @Mapping(target = "isDirectory", source = "source.isDirectory")
    @Mapping(target = "inRecycleBin", constant = "0")
    @Mapping(target = "recycleRoot", constant = "0")
    SysFile toClonedFile(FileCloneBuildBO build);

}
