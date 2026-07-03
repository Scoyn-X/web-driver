package com.jiayuan.boot.system.oss.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiayuan.boot.system.oss.model.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 文件元数据 Mapper 接口
 *
 * @author jiayuan
 * @since 2026/03/09
 */
@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    /**
     * 在给定的候选父目录 ID 集合中，查出至少包含一个未删除子目录的那些 ID。
     * <p>
     * 用于目录树懒加载的 {@code hasChildren} 标记：一次批量查询替代逐个 child 的 N+1 探测。
     *
     * @param userId    当前用户ID（必填，权限隔离）
     * @param parentIds 候选父目录 ID 集合（不能为空）
     * @return 含有子目录的父目录 ID 列表
     */
    List<Long> selectParentIdsHavingChildDirectory(@Param("userId") Long userId,
                                                   @Param("parentIds") Collection<Long> parentIds);

    /**
     * 在团队候选父目录 ID 集合中，查出至少包含一个未删除子目录的那些 ID。
     *
     * @param teamId    团队ID
     * @param parentIds 候选父目录 ID 集合
     * @return 含有子目录的父目录 ID 列表
     */
    List<Long> selectTeamParentIdsHavingChildDirectory(@Param("teamId") Long teamId,
                                                       @Param("parentIds") Collection<Long> parentIds);

    /**
     * 递归 CTE 查询：一次获取根节点下的所有后代（不含根节点自身）。
     * 替换原来逐层递归的 {@code collectFilesRecursively}。
     */
    List<SysFile> selectAllDescendants(@Param("rootId") Long rootId);

    /**
     * 递归 CTE 更新：移动目录时一次性更新所有后代的 full_path。
     * 替换原来逐层递归的 {@code updateChildrenFullPath}。
     */
    void updateDescendantsFullPath(@Param("rootId") Long rootId,
                                   @Param("parentFullPath") String parentFullPath);

    /**
     * 列出指定父目录下的子目录（排除回收站，按名称排序）。
     */
    List<SysFile> selectChildDirectories(@Param("userId") Long userId,
                                         @Param("parentId") Long parentId);

    /**
     * 列出当前用户所有未回收的目录（仅含id/名称/父目录ID）。
     */
    List<SysFile> selectDirectoriesForTree(@Param("userId") Long userId);

    /**
     * 按关键词搜索当前用户的文件（不包含回收站）。
     */
    List<SysFile> searchFilesByKeyword(@Param("userId") Long userId,
                                       @Param("keyword") String keyword);

    /**
     * 列出当前用户回收站的根节点（仅回收站根记录，按更新时间倒序）。
     */
    List<SysFile> selectRecycleBinRoots(@Param("userId") Long userId);

    /**
     * 校验同一父目录下是否已存在同名文件/目录（排除回收站）。
     */
    boolean existsByNameInDirectory(@Param("userId") Long userId,
                                    @Param("parentId") Long parentId,
                                    @Param("name") String name);

    /**
     * 获取父目录下所有文件名（可选前缀筛选，用于生成不重名序号）。
     */
    List<String> selectNamesInDirectory(@Param("userId") Long userId,
                                        @Param("parentId") Long parentId,
                                        @Param("prefix") String prefix);

    /**
     * 查询团队目录下的文件和子目录。
     */
    List<SysFile> selectTeamChildren(@Param("teamId") Long teamId,
                                     @Param("parentId") Long parentId);

    /**
     * 查询团队目录下的子目录。
     */
    List<SysFile> selectTeamDirectories(@Param("teamId") Long teamId,
                                        @Param("parentId") Long parentId);

    /**
     * 查询团队文件或目录。
     */
    SysFile selectTeamFile(@Param("teamId") Long teamId,
                           @Param("fileId") Long fileId);

    /**
     * 查询个人空间文件或目录。
     */
    SysFile selectPersonalFile(@Param("userId") Long userId,
                               @Param("fileId") Long fileId);

    /**
     * 查询可通过个人分享入口分享的个人文件或目录。
     *
     * @param userId 当前用户ID
     * @param fileId 文件ID
     * @return 个人分享文件
     */
    SysFile selectPersonalShareFile(@Param("userId") Long userId,
                                    @Param("fileId") Long fileId);

    /**
     * 批量查询个人分享关联的个人文件或目录。
     *
     * @param userId  当前用户ID
     * @param fileIds 文件ID集合
     * @return 个人分享文件列表
     */
    List<SysFile> selectPersonalShareFilesByIds(@Param("userId") Long userId,
                                                @Param("fileIds") Collection<Long> fileIds);

    /**
     * 查询个人目录下的文件和子目录。
     */
    List<SysFile> selectPersonalChildren(@Param("userId") Long userId,
                                         @Param("parentId") Long parentId);

    /**
     * 查询个人空间活动文件树。
     */
    List<SysFile> selectPersonalActiveTree(Long userId);

    /**
     * 根据对象路径查询个人空间活动文件。
     */
    SysFile selectPersonalActiveByPath(@Param("userId") Long userId,
                                       @Param("filePath") String filePath);

    /**
     * 校验团队目录下是否存在同名文件或目录。
     */
    boolean existsTeamNameInDirectory(@Param("teamId") Long teamId,
                                      @Param("parentId") Long parentId,
                                      @Param("name") String name);

    /**
     * 查询团队目录下的同名活动文件。
     */
    SysFile selectTeamActiveByNameInDirectory(@Param("teamId") Long teamId,
                                              @Param("parentId") Long parentId,
                                              @Param("name") String name);

    /**
     * 查询个人目录下的同名活动文件。
     */
    SysFile selectPersonalActiveByNameInDirectory(@Param("userId") Long userId,
                                                  @Param("parentId") Long parentId,
                                                  @Param("name") String name);

    /**
     * 查询团队目录下匹配前缀的活动文件名。
     */
    List<String> selectTeamNamesInDirectory(@Param("teamId") Long teamId,
                                            @Param("parentId") Long parentId,
                                            @Param("prefix") String prefix);

    /**
     * 搜索团队文件或目录。
     */
    List<SysFile> searchTeamFiles(@Param("teamId") Long teamId,
                                  @Param("keyword") String keyword);

    /**
     * 查询团队活动文件树。
     */
    List<SysFile> selectTeamActiveTree(Long teamId);

    /**
     * 查询团队回收站根节点。
     */
    List<SysFile> selectTeamTrashRoots(Long teamId);

    /**
     * 查询指定空间和父目录下的活动子节点。
     */
    List<SysFile> selectChildrenInSpace(@Param("spaceType") String spaceType,
                                        @Param("spaceId") Long spaceId,
                                        @Param("parentId") Long parentId);

    /**
     * 查询指定空间目录下的同名活动文件或目录。
     */
    SysFile selectActiveByNameInSpaceDirectory(@Param("spaceType") String spaceType,
                                               @Param("spaceId") Long spaceId,
                                               @Param("parentId") Long parentId,
                                               @Param("name") String name);

    /**
     * 查询指定空间目录下的活动子目录。
     */
    List<SysFile> selectDirectoriesInSpace(@Param("spaceType") String spaceType,
                                           @Param("spaceId") Long spaceId,
                                           @Param("parentId") Long parentId);

    /**
     * 查询指定空间候选父目录中含有活动子目录的目录ID。
     */
    List<Long> selectParentIdsHavingChildDirectoryInSpace(@Param("spaceType") String spaceType,
                                                          @Param("spaceId") Long spaceId,
                                                          @Param("parentIds") Collection<Long> parentIds);

    /**
     * 查询分享根目录内的活动目录，用于匿名目录分享边界校验。
     */
    SysFile selectActiveDirectoryInSharedTree(@Param("spaceType") String spaceType,
                                              @Param("spaceId") Long spaceId,
                                              @Param("rootId") Long rootId,
                                              @Param("directoryId") Long directoryId);

    /**
     * 锁定指定空间目录下的活动子节点。
     */
    List<Long> lockActiveChildrenInSpace(@Param("spaceType") String spaceType,
                                         @Param("spaceId") Long spaceId,
                                         @Param("parentId") Long parentId);

    /**
     * 更新指定空间根节点位置。
     */
    void updateRootLocationInSpace(@Param("spaceType") String spaceType,
                                   @Param("spaceId") Long spaceId,
                                   @Param("rootId") Long rootId,
                                   @Param("parentId") Long parentId,
                                   @Param("fullPath") String fullPath);

    /**
     * 查询指定空间内的一组文件或目录。
     */
    List<SysFile> selectFilesInSpaceByIds(@Param("spaceType") String spaceType,
                                          @Param("spaceId") Long spaceId,
                                          @Param("fileIds") Collection<Long> fileIds);

    /**
     * 查询指定空间内所有未逻辑删除的文件或目录。
     */
    List<SysFile> selectFilesInSpace(@Param("spaceType") String spaceType,
                                     @Param("spaceId") Long spaceId);

    /**
     * 查询指定空间文件或目录。
     */
    SysFile selectSpaceFile(@Param("spaceType") String spaceType,
                            @Param("spaceId") Long spaceId,
                            @Param("fileId") Long fileId);

    /**
     * 查询指定空间文件或目录；目录返回递归汇总后的后代文件大小。
     */
    SysFile selectSpaceFileWithRecursiveSize(@Param("spaceType") String spaceType,
                                             @Param("spaceId") Long spaceId,
                                             @Param("fileId") Long fileId);

    /**
     * 查询指定空间根节点下的所有后代。
     */
    List<SysFile> selectDescendantsInSpace(@Param("spaceType") String spaceType,
                                           @Param("spaceId") Long spaceId,
                                           @Param("rootId") Long rootId);

    /**
     * 查询指定空间根节点下的活动后代。
     */
    List<SysFile> selectActiveDescendantsInSpace(@Param("spaceType") String spaceType,
                                                 @Param("spaceId") Long spaceId,
                                                 @Param("rootId") Long rootId);

    /**
     * 更新指定空间内目录后代的 full_path。
     */
    void updateDescendantsFullPathInSpace(@Param("spaceType") String spaceType,
                                          @Param("spaceId") Long spaceId,
                                          @Param("rootId") Long rootId,
                                          @Param("parentFullPath") String parentFullPath);

    /**
     * 标记指定空间目录所有后代为非根回收站节点。
     */
    void updateDescendantsRecycleStateInSpace(@Param("spaceType") String spaceType,
                                              @Param("spaceId") Long spaceId,
                                              @Param("rootId") Long rootId,
                                              @Param("retentionSeconds") long retentionSeconds);

    /**
     * 按指定时间标记空间目录后代为非根回收站节点。
     */
    void updateDescendantsRecycleStateInSpaceWithExpireAt(@Param("spaceType") String spaceType,
                                                          @Param("spaceId") Long spaceId,
                                                          @Param("rootId") Long rootId,
                                                          @Param("deletedAt") LocalDateTime deletedAt,
                                                          @Param("expireAt") LocalDateTime expireAt);

    /**
     * 标记指定空间文件或目录为回收站根节点。
     */
    int moveRootToTrashInSpace(@Param("spaceType") String spaceType,
                               @Param("spaceId") Long spaceId,
                               @Param("rootId") Long rootId,
                               @Param("deletedBy") Long deletedBy,
                               @Param("retentionSeconds") long retentionSeconds);

    /**
     * 按指定时间标记空间文件或目录为回收站根节点。
     */
    // @param retentionSeconds 保留秒数
    int moveRootToTrashInSpaceWithExpireAt(@Param("spaceType") String spaceType,
                                           @Param("spaceId") Long spaceId,
                                           @Param("rootId") Long rootId,
                                           @Param("deletedBy") Long deletedBy,
                                           @Param("deletedAt") LocalDateTime deletedAt,
                                           @Param("expireAt") LocalDateTime expireAt);

    /**
     * 递归恢复指定空间的回收站根节点及后代。
     */
    int restoreTrashTreeInSpace(@Param("spaceType") String spaceType,
                                @Param("spaceId") Long spaceId,
                                @Param("rootId") Long rootId,
                                @Param("parentId") Long parentId,
                                @Param("parentFullPath") String parentFullPath,
                                @Param("rootName") String rootName);

    /**
     * 递归逻辑删除指定空间的回收站根节点及后代。
     */
    int permanentlyDeleteTrashTreeInSpace(@Param("spaceType") String spaceType,
                                          @Param("spaceId") Long spaceId,
                                          @Param("rootId") Long rootId);

    /**
     * 逻辑删除指定空间内所有文件或目录。
     */
    int permanentlyDeleteSpaceFiles(@Param("spaceType") String spaceType,
                                    @Param("spaceId") Long spaceId);

    /**
     * 校验指定空间目录下是否存在同名活动文件。
     */
    boolean existsNameInSpaceDirectory(@Param("spaceType") String spaceType,
                                       @Param("spaceId") Long spaceId,
                                       @Param("parentId") Long parentId,
                                       @Param("name") String name);

    /**
     * 查询指定空间目录下匹配前缀的活动文件名。
     */
    List<String> selectNamesInSpaceDirectory(@Param("spaceType") String spaceType,
                                             @Param("spaceId") Long spaceId,
                                             @Param("parentId") Long parentId,
                                             @Param("prefix") String prefix);

    /**
     * 查询指定空间回收站根节点。
     */
    List<SysFile> selectTrashRootsInSpace(@Param("spaceType") String spaceType,
                                          @Param("spaceId") Long spaceId);

    /**
     * 查询已到期的回收站根节点。
     */
    List<SysFile> selectExpiredTrashRoots(@Param("now") LocalDateTime now,
                                          @Param("limit") int limit);

    /**
     * 查询宽限期已到期且仍在活动状态的私密空间根节点。
     */
    List<SysFile> selectExpiredPrivateGraceRoots(@Param("now") LocalDateTime now,
                                                 @Param("vipQuota") long vipQuota,
                                                 @Param("limit") int limit);

    /**
     * 查询个人目录下的子目录。
     */
    List<SysFile> selectPersonalDirectories(@Param("userId") Long userId,
                                            @Param("parentId") Long parentId);

    /**
     * 查询个人空间活动目录树。
     */
    List<SysFile> selectPersonalDirectoryTree(Long userId);

    /**
     * 搜索个人空间活动文件。
     */
    List<SysFile> searchPersonalFiles(@Param("userId") Long userId,
                                      @Param("keyword") String keyword);

    /**
     * 查询个人回收站根节点。
     */
    List<SysFile> selectPersonalTrashRoots(Long userId);

    /**
     * 根据保留秒数更新所有回收站文件的到期时间。
     */
    int updateRecycleBinExpireAt(@Param("retentionSeconds") long retentionSeconds);

}
