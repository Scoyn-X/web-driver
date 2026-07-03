/** Personal 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const PERSONAL_BASE_URL = `${API_BASE}/personal`;

// ==================== 数据模型 ====================

/** 目录路径面包屑节点 */
export interface BreadcrumbItemResponseVO {
  /** 目录ID（0表示根目录） */
  id?: number;
  /** 目录名称 */
  name?: string;
}

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

/** 私密空间密码请求 */
export interface PrivatePasswordRequestVO {
  /** 旧密码，修改密码时必填 */
  oldPassword?: string;
  /** 新密码 */
  password: string;
}

/** 私密空间解锁请求 */
export interface PrivateSessionRequestVO {
  /** 私密空间密码 */
  password: string;
}

/** 私密空间解锁响应 */
export interface PrivateSessionResponseVO {
  /** 解锁截止时间 */
  unlockedUntil?: string;
}

/** 私密空间状态响应 */
export interface PrivateSpaceStatusResponseVO {
  /** 私密空间状态 */
  state?: "DISABLED" | "ACTIVE" | "GRACE_PERIOD" | "LOCKED" | "EXPIRED";
  /** 解锁截止时间 */
  unlockedUntil?: string;
  /** 降级宽限期截止时间 */
  graceExpireAt?: string;
  /** 提醒文案 */
  reminderMessage?: string;
}

/** 回收站列表项视图对象 */
export interface RecycleBinItemResponseVO {
  /** 文件/目录ID（用于恢复或永久删除） */
  id?: number;
  /** 原始名称 */
  originalName?: string;
  /** 完整人类可读路径，含自身名称 */
  path?: string;
  /** 文件大小（字节），目录为0 */
  fileSize?: number;
  /** 是否为目录（0=文件 1=目录） */
  isDirectory?: number;
  /** 删除者用户ID */
  deletedByUserId?: number;
  /** 删除者主账户ID */
  deletedByAccountId?: number;
  /** 删除者主账户名 */
  deletedByAccountName?: string;
  /** 删除者名称 */
  deletedByName?: string;
  /** 放入回收站的时间 */
  deletedAt?: string;
  /** 回收站到期时间 */
  expireAt?: string;
}

/** personal 模块接口集合 */
export const PersonalAPI = {
  /**
   * 移动个人目录
   *
   * `PUT /api/v1/personal/directories/{directoryId}/move`
   * @param directoryId 路径参数
   * @param data 请求体 —— 文件/目录移动请求
   */
  moveDirectory(directoryId: number, data: FileMoveRequestVO) {
    return request<void>({
      url: `${PERSONAL_BASE_URL}/directories/${directoryId}/move`,
      method: "put",
      data,
    });
  },

  /**
   * 设置或修改私密空间密码
   *
   * `PUT /api/v1/personal/private-space/password`
   * @param data 请求体 —— 私密空间密码请求
   */
  updatePassword(data: PrivatePasswordRequestVO) {
    return request<void>({
      url: `${PERSONAL_BASE_URL}/private-space/password`,
      method: "put",
      data,
    });
  },

  /**
   * 首次设置私密空间密码
   *
   * `PUT /api/v1/personal/private-space/password`
   * @param data 请求体 —— 私密空间密码请求
   */
  setPrivateSpacePassword(data: PrivatePasswordRequestVO) {
    return request<void>({
      url: `${PERSONAL_BASE_URL}/private-space/password`,
      method: "put",
      data,
    });
  },

  /**
   * 移动私密空间文件
   *
   * `PUT /api/v1/personal/private-space/files/{id}/move`
   * @param id 路径参数
   * @param data 请求体 —— 文件/目录移动请求
   */
  movePrivateFile(id: number, data: FileMoveRequestVO) {
    return request<void>({
      url: `${PERSONAL_BASE_URL}/private-space/files/${id}/move`,
      method: "put",
      data,
    });
  },

  /**
   * 恢复私密空间回收站文件
   *
   * `POST /api/v1/personal/private-space/trash/{id}/restore`
   * @param id 路径参数
   * @param conflictPolicy 查询参数
   * @returns 文件信息视图对象
   */
  restorePrivateTrash(id: number, conflictPolicy?: "RENAME" | "OVERWRITE") {
    return request<FileInfoResponseVO>({
      url: `${PERSONAL_BASE_URL}/private-space/trash/${id}/restore`,
      method: "post",
      params: { conflictPolicy },
    });
  },

  /**
   * 解锁私密空间会话
   *
   * `POST /api/v1/personal/private-space/session`
   * @param data 请求体 —— 私密空间解锁请求
   * @returns 私密空间解锁响应
   */
  unlock(data: PrivateSessionRequestVO) {
    return request<PrivateSessionResponseVO>({
      url: `${PERSONAL_BASE_URL}/private-space/session`,
      method: "post",
      data,
    });
  },

  /**
   * 解锁私密空间会话
   *
   * `POST /api/v1/personal/private-space/session`
   * @param data 请求体 —— 私密空间解锁请求
   * @returns 私密空间解锁响应
   */
  unlockPrivateSpace(data: PrivateSessionRequestVO) {
    return request<PrivateSessionResponseVO>({
      url: `${PERSONAL_BASE_URL}/private-space/session`,
      method: "post",
      data,
    });
  },

  /**
   * 列出私密空间文件
   *
   * `GET /api/v1/personal/private-space/files`
   * @param parentId 查询参数
   */
  listPrivateFiles(parentId: number = 0) {
    return request<FileListResponseVO>({
      url: `${PERSONAL_BASE_URL}/private-space/files`,
      method: "get",
      params: { parentId },
    });
  },

  /**
   * 上传私密空间文件
   *
   * `POST /api/v1/personal/private-space/files`
   * @param parentId 查询参数
   * @returns 文件信息视图对象
   */
  uploadPrivateFile(file: File, parentId: number = 0) {
    const formData = new FormData();
    formData.append("file", file);
    return request<FileInfoResponseVO>({
      url: `${PERSONAL_BASE_URL}/private-space/files`,
      method: "post",
      params: { parentId },
      data: formData,
    });
  },

  /**
   * 列出私密空间目录
   *
   * `GET /api/v1/personal/private-space/directories`
   * @param parentId 查询参数
   */
  listPrivateDirectories(parentId: number = 0) {
    return request<DirectoryNodeResponseVO[]>({
      url: `${PERSONAL_BASE_URL}/private-space/directories`,
      method: "get",
      params: { parentId },
    });
  },

  /**
   * 新建私密空间目录
   *
   * `POST /api/v1/personal/private-space/directories`
   * @param data 请求体 —— 创建目录请求
   * @returns 文件信息视图对象
   */
  createPrivateDirectory(data: DirectoryCreateRequestVO) {
    return request<FileInfoResponseVO>({
      url: `${PERSONAL_BASE_URL}/private-space/directories`,
      method: "post",
      data,
    });
  },

  /**
   * 列出私密空间回收站文件
   *
   * `GET /api/v1/personal/private-space/trash`
   */
  listPrivateTrash() {
    return request<RecycleBinItemResponseVO[]>({
      url: `${PERSONAL_BASE_URL}/private-space/trash`,
      method: "get",
    });
  },

  /**
   * 获取私密空间状态
   *
   * `GET /api/v1/personal/private-space/status`
   * @returns 私密空间状态响应
   */
  getStatus() {
    return request<PrivateSpaceStatusResponseVO>({
      url: `${PERSONAL_BASE_URL}/private-space/status`,
      method: "get",
    });
  },

  /**
   * 获取私密空间状态
   *
   * `GET /api/v1/personal/private-space/status`
   * @returns 私密空间状态响应
   */
  getPrivateSpaceStatus() {
    return request<PrivateSpaceStatusResponseVO>({
      url: `${PERSONAL_BASE_URL}/private-space/status`,
      method: "get",
    });
  },

  /**
   * 获取私密空间文件详情
   *
   * `GET /api/v1/personal/private-space/files/{id}`
   * @param id 路径参数
   * @returns 文件信息视图对象
   */
  getPrivateFile(id: number) {
    return request<FileInfoResponseVO>({
      url: `${PERSONAL_BASE_URL}/private-space/files/${id}`,
      method: "get",
    });
  },

  /**
   * 删除私密空间文件
   *
   * `DELETE /api/v1/personal/private-space/files/{id}`
   * @param id 路径参数
   */
  deletePrivateFile(id: number) {
    return request<void>({
      url: `${PERSONAL_BASE_URL}/private-space/files/${id}`,
      method: "delete",
    });
  },

  /**
   * 下载私密空间文件
   *
   * `GET /api/v1/personal/private-space/files/{id}/download`
   * @param id 路径参数
   */
  downloadPrivateFile(id: number, onProgress?: (e: ProgressEvent) => void, signal?: AbortSignal) {
    return request<Blob>({
      url: `${PERSONAL_BASE_URL}/private-space/files/${id}/download`,
      method: "get",
      responseType: "blob",
      timeout: 0,
      onDownloadProgress: onProgress,
      signal,
    });
  },

  /**
   * 永久删除私密空间回收站文件
   *
   * `DELETE /api/v1/personal/private-space/trash/{id}`
   * @param id 路径参数
   */
  permanentlyDeletePrivateTrash(id: number) {
    return request<void>({
      url: `${PERSONAL_BASE_URL}/private-space/trash/${id}`,
      method: "delete",
    });
  },
};

export default PersonalAPI;
