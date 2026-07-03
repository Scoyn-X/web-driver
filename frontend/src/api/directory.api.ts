/** Directory 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const DIRECTORIES_BASE_URL = `${API_BASE}/directories`;

// ==================== 数据模型 ====================

/** 创建目录请求 */
export interface DirectoryCreateRequestVO {
  /** 目录名称 */
  name: string;
  /** 父目录ID（0表示根目录） */
  parentId?: number;
}

/** 目录树节点视图对象 */
export interface DirectoryNodeResponseVO {
  /** 目录ID */
  id?: number;
  /** 目录名称 */
  name?: string;
  /** 父目录ID（0表示根目录） */
  parentId?: number;
  /** 是否包含子目录 */
  hasChildren?: boolean;
}

/** 目录重命名请求 */
export interface DirectoryRenameRequestVO {
  /** 新目录名称 */
  name: string;
}

/** 目录树节点响应 */
export interface DirectoryTreeResponseVO {
  /** 目录ID */
  id?: number;
  /** 目录名称 */
  name?: string;
  /** 父目录ID（0表示根目录） */
  parentId?: number;
  /** 子目录列表 */
  children?: unknown;
}

/** 文件信息视图对象 */
export interface FileInfoResponseVO {
  /** 文件ID */
  id?: number;
  /** 文件原始名称 */
  originalName?: string;
  /** 文件大小（字节） */
  fileSize?: number;
  /** 文件MIME类型 */
  mimeType?: string;
  /** 上传时间 */
  createTime?: string;
  /** 文件访问URL */
  fileUrl?: string;
  /** 是否为目录（0=文件 1=目录） */
  isDirectory?: number;
  /** 父目录ID（0表示根目录） */
  parentId?: number;
  /** 祖先ID路径 */
  fullPath?: unknown[];
}

/** directory 模块接口集合 */
export const DirectoryAPI = {
  /**
   * 重命名个人目录
   *
   * `PUT /api/v1/directories/{id}/rename`
   * @param id 路径参数
   * @param data 请求体 —— 目录重命名请求
   * @returns 文件信息视图对象
   */
  renameDirectory(id: number, data: DirectoryRenameRequestVO) {
    return request<FileInfoResponseVO>({
      url: `${DIRECTORIES_BASE_URL}/${id}/rename`,
      method: "put",
      data,
    });
  },

  /**
   * 列出个人目录
   *
   * `GET /api/v1/directories`
   * @param parentId 查询参数
   */
  listChildDirectories(parentId: number = 0) {
    return request<DirectoryNodeResponseVO[]>({
      url: DIRECTORIES_BASE_URL,
      method: "get",
      params: { parentId },
    });
  },

  /**
   * 新建个人目录
   *
   * `POST /api/v1/directories`
   * @param data 请求体 —— 创建目录请求
   * @returns 文件信息视图对象
   */
  createDirectory(data: DirectoryCreateRequestVO) {
    return request<FileInfoResponseVO>({
      url: DIRECTORIES_BASE_URL,
      method: "post",
      data,
    });
  },

  /**
   * 列出个人目录树
   *
   * `GET /api/v1/directories/tree`
   */
  listDirectoryTree() {
    return request<DirectoryTreeResponseVO[]>({
      url: `${DIRECTORIES_BASE_URL}/tree`,
      method: "get",
    });
  },

  /**
   * 删除个人目录
   *
   * `DELETE /api/v1/directories/{id}`
   * @param id 路径参数
   */
  deleteDirectory(id: number) {
    return request<void>({
      url: `${DIRECTORIES_BASE_URL}/${id}`,
      method: "delete",
    });
  },
};

export default DirectoryAPI;
