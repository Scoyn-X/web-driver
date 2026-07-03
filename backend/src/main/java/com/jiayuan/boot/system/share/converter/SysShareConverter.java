package com.jiayuan.boot.system.share.converter;

import com.jiayuan.boot.common.util.FileUtils;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import com.jiayuan.boot.system.share.model.entity.SysShare;
import com.jiayuan.boot.system.share.model.bo.ShareBuildContextBO;
import com.jiayuan.boot.system.share.model.vo.ShareAccessResponseVO;
import com.jiayuan.boot.system.share.model.vo.ShareCreateRequestVO;
import com.jiayuan.boot.system.share.model.vo.ShareInfoResponseVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 分享对象转换器（MapStruct）
 *
 * @author charleslam
 * @since 2026/04/14
 */
@Mapper(componentModel = "spring", imports = {FileUtils.class})
public interface SysShareConverter {

    /**
     * 基础字段映射；fileName / statusDesc 由 Service 层补充，不在此处计算。
     */
    @Mapping(target = "fileName", ignore = true)
    @Mapping(target = "statusDesc", ignore = true)
    @Mapping(target = "isDirectory", ignore = true)
    ShareInfoResponseVO toShareInfoVO(SysShare share);

    /**
     * 转换分享列表/详情响应，并补充文件名与状态描述。
     *
     * @param share      分享实体
     * @param fileName   文件名
     * @param statusDesc 状态描述
     * @return 分享信息响应
     */
    @Mapping(target = "fileName", source = "fileName")
    @Mapping(target = "statusDesc", source = "statusDesc")
    @Mapping(target = "isDirectory", source = "isDirectory")
    ShareInfoResponseVO toShareInfoVO(SysShare share, String fileName, String statusDesc, Boolean isDirectory);

    /**
     * 分享实体构造：extractCode / expireTime 由 Service 层按需计算后传入。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "userId", source = "context.userId")
    @Mapping(target = "creatorAccountId", ignore = true)
    @Mapping(target = "spaceType", constant = "PERSONAL")
    @Mapping(target = "teamId", ignore = true)
    @Mapping(target = "fileId", source = "request.fileId")
    @Mapping(target = "shareToken", source = "context.shareToken")
    @Mapping(target = "accessType", source = "request.accessType")
    @Mapping(target = "status", constant = "0")
    @Mapping(target = "extractCode", source = "context.extractCode")
    @Mapping(target = "expireTime", source = "context.expireTime")
    SysShare toSysShare(ShareCreateRequestVO request, ShareBuildContextBO context);

    /**
     * 团队分享实体构造：extractCode / expireTime 由 Service 层按需计算后传入。
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "userId", source = "context.userId")
    @Mapping(target = "creatorAccountId", source = "context.creatorAccountId")
    @Mapping(target = "spaceType", constant = "TEAM")
    @Mapping(target = "teamId", source = "context.teamId")
    @Mapping(target = "fileId", source = "request.fileId")
    @Mapping(target = "shareToken", source = "context.shareToken")
    @Mapping(target = "accessType", source = "request.accessType")
    @Mapping(target = "status", constant = "0")
    @Mapping(target = "extractCode", source = "context.extractCode")
    @Mapping(target = "expireTime", source = "context.expireTime")
    SysShare toTeamSysShare(ShareCreateRequestVO request, ShareBuildContextBO context);

    /**
     * 分享访问响应：requireExtractCode 由 Service 层计算后传入。
     */
    @Mapping(target = "fileName", source = "file.originalName")
    @Mapping(target = "fileSize", source = "file.fileSize")
    @Mapping(target = "fileSizeFormatted",
            expression = "java(FileUtils.formatFileSize(file.getFileSize() == null ? 0L : file.getFileSize()))")
    @Mapping(target = "mimeType", source = "file.mimeType")
    @Mapping(target = "isDirectory",
            expression = "java(file.getIsDirectory() != null && file.getIsDirectory() == 1)")
    @Mapping(target = "requireExtractCode", source = "requireExtractCode")
    @Mapping(target = "fileUploadTime", source = "file.createTime")
    ShareAccessResponseVO toShareAccessResponseVO(SysFile file, boolean requireExtractCode);

}
