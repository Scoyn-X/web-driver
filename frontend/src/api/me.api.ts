/** Me 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const ME_BASE_URL = `${API_BASE}/me`;

// ==================== 数据模型 ====================

/** 菜单节点响应 */
export interface MenuNodeResponseVO {
  /** 菜单节点ID */
  id?: number;
  /** 父菜单节点ID */
  parentId?: number;
  /** 菜单标题 */
  title?: string;
  /** 前端路由路径 */
  path?: string;
  /** 前端组件标识 */
  componentKey?: string;
  /** 菜单图标 */
  icon?: string;
  /** 排序值 */
  sort?: number;
  /** 子菜单节点 */
  children?: unknown;
}

/** 菜单树响应 */
export interface MenuTreeResponseVO {
  /** 菜单节点列表 */
  menus?: MenuNodeResponseVO[];
}

/** me 模块接口集合 */
export const MeAPI = {
  /**
   * 获取我的菜单
   *
   * `GET /api/v1/me/menus`
   * @returns 菜单树响应
   */
  listMyMenus() {
    return request<MenuTreeResponseVO>({
      url: `${ME_BASE_URL}/menus`,
      method: "get",
    });
  },
};

export default MeAPI;
