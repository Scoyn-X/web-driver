/** Visit 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const VISITS_BASE_URL = `${API_BASE}/visits`;

/** visit 模块接口集合 */
export const VisitAPI = {
  /**
   * 获取访问次数
   *
   * `GET /api/v1/visits`
   */
  getVisitCount() {
    return request<number>({
      url: VISITS_BASE_URL,
      method: "get",
    });
  },

  /**
   * 增加访问次数
   *
   * `POST /api/v1/visits`
   */
  incrementVisitCount() {
    return request<number>({
      url: VISITS_BASE_URL,
      method: "post",
    });
  },

  /**
   * 重置访问次数
   *
   * `DELETE /api/v1/visits`
   */
  resetVisitCount() {
    return request<void>({
      url: VISITS_BASE_URL,
      method: "delete",
    });
  },
};

export default VisitAPI;
