/** Quota 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const QUOTA_BASE_URL = `${API_BASE}/quota`;

// ==================== 数据模型 ====================

/** 配额信息视图对象 */
export interface QuotaResponseVO {
  /** 总配额（字节） */
  totalQuota?: number;
  /** 已使用空间（字节） */
  usedSpace?: number;
  /** 剩余空间（字节） */
  remainingSpace?: number;
  /** 总配额格式化展示 */
  totalQuotaFormatted?: string;
  /** 已使用空间格式化展示 */
  usedSpaceFormatted?: string;
  /** 剩余空间格式化展示 */
  remainingSpaceFormatted?: string;
}

/** quota 模块接口集合 */
export const QuotaAPI = {
  /**
   * 获取当前用户配额信息
   *
   * `GET /api/v1/quota`
   * @returns 配额信息视图对象
   */
  getQuotaInfo() {
    return request<QuotaResponseVO>({
      url: QUOTA_BASE_URL,
      method: "get",
    });
  },
};

export default QuotaAPI;
