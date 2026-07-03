/** Auth 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const AUTH_BASE_URL = `${API_BASE}/auth`;

// ==================== 数据模型 ====================

/** 用户登录请求 */
export interface LoginRequestVO {
  /** 账户名 */
  accountName: string;
  /** 密码 */
  password: string;
}

/** 用户登录响应 */
export interface LoginResponseVO {
  /** JWT Token */
  token?: string;
  /** 用户ID */
  userId?: number;
  /** 账户ID */
  accountId?: number;
  /** 用户昵称 */
  nickname?: string;
  /** 账户名 */
  accountName?: string;
}

/** 用户注册请求 */
export interface RegisterRequestVO {
  /** 用户昵称 */
  nickname: string;
  /** 账户名 */
  accountName: string;
  /** 密码 */
  password: string;
  /** 邮箱 */
  email: string;
  /** 账户类型 */
  accountType: "personal" | "work" | "team";
}

/** auth 模块接口集合 */
export const AuthAPI = {
  /**
   * 用户注册
   *
   * `POST /api/v1/auth/register`
   * @param data 请求体 —— 用户注册请求
   */
  register(data: RegisterRequestVO) {
    return request<void>({
      url: `${AUTH_BASE_URL}/register`,
      method: "post",
      data,
    });
  },

  /**
   * 用户登录
   *
   * `POST /api/v1/auth/login`
   * @param data 请求体 —— 用户登录请求
   * @returns 用户登录响应
   */
  login(data: LoginRequestVO) {
    return request<LoginResponseVO>({
      url: `${AUTH_BASE_URL}/login`,
      method: "post",
      data,
    });
  },
};

export default AuthAPI;
