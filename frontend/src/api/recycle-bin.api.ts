/** RecycleBin 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const RECYCLE_BIN_BASE_URL = `${API_BASE}/recycle-bin`;

// ==================== 数据模型 ====================

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

/** recycle-bin 模块接口集合 */
export const RecycleBinAPI = {
  /**
   * 恢复个人回收站文件/目录
   *
   * `POST /api/v1/recycle-bin/{id}/restore`
   * @param id 路径参数
   */
  restore(id: number) {
    return request<void>({
      url: `${RECYCLE_BIN_BASE_URL}/${id}/restore`,
      method: "post",
    });
  },

  /**
   * 列出个人回收站文件/目录
   *
   * `GET /api/v1/recycle-bin`
   */
  list() {
    return request<RecycleBinItemResponseVO[]>({
      url: RECYCLE_BIN_BASE_URL,
      method: "get",
    });
  },

  /**
   * 永久删除回收站文件
   *
   * `DELETE /api/v1/recycle-bin/{id}`
   * @param id 路径参数
   */
  permanentDelete(id: number) {
    return request<void>({
      url: `${RECYCLE_BIN_BASE_URL}/${id}`,
      method: "delete",
    });
  },
};

export default RecycleBinAPI;
