package com.jiayuan.boot.system.oss.service;

import com.jiayuan.boot.system.oss.model.vo.DirectoryCreateRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryNodeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryRenameRequestVO;
import com.jiayuan.boot.system.oss.model.vo.DirectoryTreeResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileInfoResponseVO;
import com.jiayuan.boot.system.oss.model.vo.FileMoveRequestVO;

import java.util.List;

/**
 * 目录管理服务接口
 *
 * @author didongchen
 * @since 2026/04/11
 */
public interface DirectoryService {

    /**
     * 创建目录
     *
     * @param request 创建目录请求
     * @return 目录信息
     */
    FileInfoResponseVO createDirectory(DirectoryCreateRequestVO request);

    /**
     * 重命名目录
     *
     * @param directoryId 目录ID
     * @param request     重命名请求
     * @return 更新后的目录信息
     */
    FileInfoResponseVO renameDirectory(Long directoryId, DirectoryRenameRequestVO request);

    /**
     * 移动目录
     *
     * @param directoryId 目录ID
     * @param request     移动请求
     */
    void moveDirectory(Long directoryId, FileMoveRequestVO request);

    /**
     * 删除目录（递归删除目录下所有文件和子目录）
     *
     * @param directoryId 目录ID
     * @return 删除结果
     */
    boolean deleteDirectory(Long directoryId);

    /**
     * 列出指定父目录下的直接子目录（懒加载，不含文件、不递归）。
     *
     * @param parentId 父目录ID（0表示根目录）
     * @return 子目录节点列表，每项含 hasChildren 标记
     */
    List<DirectoryNodeResponseVO> listChildDirectories(Long parentId);

    /**
     * 获取当前用户的目录树（仅文件夹，树形结构，一次性返回）。
     *
     * @return 目录树列表（根级目录为顶层节点）
     */
    List<DirectoryTreeResponseVO> listDirectoryTree();

}
