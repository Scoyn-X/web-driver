/** Role 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const ROLES_BASE_URL = `${API_BASE}/roles`;

// ==================== 数据模型 ====================

/** 团队角色选项响应 */
export interface RoleOptionResponseVO {
  /** 角色编码 */
  code: "Owner" | "Admin" | "Editor" | "Viewer";
  /** 角色名称 */
  name: string;
  /** 角色说明 */
  description?: string;
  /** 当前场景是否可分配 */
  assignable: boolean;
  /** 角色包含的权限点 */
  permissions: string[];
}

/** role 模块接口集合 */
export const RoleAPI = {
  /**
   * 列出系统角色
   *
   * `GET /api/v1/roles`
   */
  listRoleOptions() {
    return request<RoleOptionResponseVO[]>({
      url: ROLES_BASE_URL,
      method: "get",
    });
  },
};

export default RoleAPI;
