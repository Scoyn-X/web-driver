/** File 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const FILES_BASE_URL = `${API_BASE}/files`;

// ==================== 数据模型 ====================

/** 目录路径面包屑节点 */
export interface BreadcrumbItemResponseVO {
  /** 目录ID（0表示根目录） */
  id?: number;
  /** 目录名称 */
  name?: string;
}

/** 文件复制请求 */
export interface FileCopyRequestVO {
  /** 目标目录ID（0表示根目录） */
  targetDirectoryId: number;
}

export interface FileInfo {
  id?: number;
  name?: string;
  url?: string;
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

export interface FileListResponseVO {
  /** 从根到当前目录的面包屑（按层级顺序） */
  breadcrumb?: BreadcrumbItemResponseVO[];
  /** 当前目录下的文件与子目录混合列表 */
  items?: FileInfoResponseVO[];
}

/** 文件/目录移动请求 */
export interface FileMoveRequestVO {
  /** 目标目录ID（0表示根目录） */
  targetDirectoryId: number;
}

/** 文件树节点响应对象 */
export interface FileTreeResponseVO {
  /** 文件/目录ID */
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
  /** 祖先ID路径（从根到当前节点的父级ID链路） */
  fullPath?: unknown[];
  /** 子节点列表 */
  children?: unknown;
}

/** file 模块接口集合 */
export const FileAPI = {
  /**
   * 移动个人文件/目录
   *
   * `PUT /api/v1/files/{id}/move`
   * @param id 路径参数
   * @param data 请求体 —— 文件/目录移动请求
   * @returns 文件信息视图对象
   */
  moveFile(id: number, data: FileMoveRequestVO) {
    return request<FileInfoResponseVO>({
      url: `${FILES_BASE_URL}/${id}/move`,
      method: "put",
      data,
    });
  },

  /**
   * 列出个人文件/目录
   *
   * `GET /api/v1/files`
   * @param parentId 查询参数
   */
  listPersonalFiles(parentId: number = 0) {
    return request<FileListResponseVO>({
      url: FILES_BASE_URL,
      method: "get",
      params: { parentId },
    });
  },

  /**
   * 上传个人文件
   *
   * `POST /api/v1/files`
   * @param file 上传文件
   * @param parentId 查询参数
   */
  uploadFile(file: File, parentId: number = 0) {
    const formData = new FormData();
    formData.append("file", file);
    return request<FileInfo>({
      url: FILES_BASE_URL,
      method: "post",
      params: { parentId },
      data: formData,
    });
  },

  /**
   * 按路径删除个人文件
   *
   * `DELETE /api/v1/files`
   * @param filePath 查询参数
   */
  deleteFile(filePath: string) {
    return request<void>({
      url: FILES_BASE_URL,
      method: "delete",
      params: { filePath },
    });
  },

  /**
   * 复制个人文件
   *
   * `POST /api/v1/files/{id}/copy`
   * @param id 路径参数
   * @param data 请求体 —— 文件复制请求
   * @returns 文件信息视图对象
   */
  copyFile(id: number, data: FileCopyRequestVO) {
    return request<FileInfoResponseVO>({
      url: `${FILES_BASE_URL}/${id}/copy`,
      method: "post",
      data,
    });
  },

  /**
   * 按 ID 下载个人文件
   *
   * `GET /api/v1/files/{id}/download`
   * @param id 路径参数
   */
  downloadPersonalFileById(
    id: number,
    onProgress?: (e: ProgressEvent) => void,
    signal?: AbortSignal
  ) {
    return request<Blob>({
      url: `${FILES_BASE_URL}/${id}/download`,
      method: "get",
      responseType: "blob",
      timeout: 0,
      onDownloadProgress: onProgress,
      signal,
    });
  },

  /**
   * 按路径下载个人文件
   *
   * `GET /api/v1/files/{filePath}`
   * @param filePath 路径参数
   */
  downloadPersonalFileByPath(filePath: string) {
    return request<Blob>({
      url: `${FILES_BASE_URL}/${filePath}`,
      method: "get",
      responseType: "blob",
    });
  },

  /**
   * 列出个人文件树
   *
   * `GET /api/v1/files/tree`
   */
  listPersonalFileTree() {
    return request<FileTreeResponseVO[]>({
      url: `${FILES_BASE_URL}/tree`,
      method: "get",
    });
  },

  /**
   * 搜索个人文件
   *
   * `GET /api/v1/files/search`
   * @param keyword 查询参数
   */
  searchPersonalFiles(keyword: string) {
    return request<FileInfoResponseVO[]>({
      url: `${FILES_BASE_URL}/search`,
      method: "get",
      params: { keyword },
    });
  },

  /**
   * 删除个人文件
   *
   * `DELETE /api/v1/files/{id}`
   * @param id 路径参数
   */
  deleteFileById(id: number) {
    return request<void>({
      url: `${FILES_BASE_URL}/${id}`,
      method: "delete",
    });
  },
};

export default FileAPI;
