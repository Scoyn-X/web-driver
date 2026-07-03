/** Permission 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const PERMISSIONS_BASE_URL = `${API_BASE}/permissions`;

// ==================== 数据模型 ====================

/** 系统权限点响应 */
export interface PermissionResponseVO {
  /** 权限点编码 */
  code: string;
  /** 权限点名称 */
  name: string;
  /** 权限分组 */
  group: string;
  /** 权限点说明 */
  description?: string;
}

/** permission 模块接口集合 */
export const PermissionAPI = {
  /**
   * 列出系统权限点
   *
   * `GET /api/v1/permissions`
   */
  listPermissions() {
    return request<PermissionResponseVO[]>({
      url: PERMISSIONS_BASE_URL,
      method: "get",
    });
  },
};

export default PermissionAPI;
