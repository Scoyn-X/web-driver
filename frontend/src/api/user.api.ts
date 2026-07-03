/** User 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const USERS_BASE_URL = `${API_BASE}/users`;

// ==================== 数据模型 ====================

export interface CurrentUserResponseVO {
  /** 当前用户ID */
  userId?: number;
  /** 当前账户ID */
  accountId?: number;
  /** 当前账户名 */
  accountName?: string;
  /** 当前用户昵称 */
  nickname?: string;
  /** 当前用户邮箱 */
  email?: string;
  /** VIP 状态 */
  vipState?: "NORMAL" | "VIP";
  /** 个人配额 */
  personalQuota?: QuotaResponseVO;
  /** 私密空间提醒 */
  privateSpaceReminder?: string;
}

/** 团队邀请响应 */
export interface InvitationResponseVO {
  /** 邀请ID */
  id?: number;
  /** 团队ID */
  teamId?: number;
  /** 团队名称 */
  teamName?: string;
  /** 团队简介 */
  teamDescription?: string;
  /** 邀请人用户ID */
  inviterId?: number;
  /** 邀请人账户ID */
  inviterAccountId?: number;
  /** 邀请人用户名 */
  inviterName?: string;
  /** 邀请人账户名 */
  inviterAccountName?: string;
  /** 被邀请人用户ID */
  inviteeId?: number;
  /** 被邀请人账户ID */
  inviteeAccountId?: number;
  /** 被邀请人用户名 */
  inviteeName?: string;
  /** 被邀请人账户名 */
  inviteeAccountName?: string;
  /** 目标角色(Admin/Editor/Viewer) */
  targetRole?: string;
  /** 邀请状态(PENDING/ACCEPTED/REJECTED/REVOKED/EXPIRED/TEAM_DISSOLVED) */
  status?: string;
  /** 过期时间 */
  expireAt?: string;
  /** 创建时间 */
  createTime?: string;
}

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

export interface UserBriefResponseVO {
  /** 用户ID */
  userId?: number;
  /** 账户ID */
  accountId?: number;
  /** 主账户名 */
  accountName?: string;
  /** 用户昵称 */
  nickname?: string;
  /** 邮箱 */
  email?: string;
}

export interface UserVipResponseVO {
  /** 用户ID */
  userId?: number;
  /** VIP 状态 */
  vipState?: "NORMAL" | "VIP";
  /** 个人容量限制，VIP 为 null */
  personalQuotaLimit?: number;
  /** 团队容量限制，VIP 为 null */
  teamQuotaLimit?: number;
  /** 是否下载限速 */
  downloadLimited?: boolean;
  /** 单文件大小限制，VIP 为 null */
  singleFileLimit?: number;
}

export interface VipUpdateRequestVO {
  /** 是否切换为 VIP */
  vip: boolean;
  /** 切换原因 */
  reason?: string;
}

/** user 模块接口集合 */
export const UserAPI = {
  /**
   * 切换VIP状态
   *
   * `PUT /api/v1/users/{id}/vip`
   * @param id 路径参数
   * @param data 请求体
   */
  updateVip(id: number, data: VipUpdateRequestVO) {
    return request<UserVipResponseVO>({
      url: `${USERS_BASE_URL}/${id}/vip`,
      method: "put",
      data,
    });
  },

  /**
   * 搜索用户
   *
   * `GET /api/v1/users/search`
   * @param keyword 查询参数
   */
  searchUsers(keyword: string) {
    return request<UserBriefResponseVO[]>({
      url: `${USERS_BASE_URL}/search`,
      method: "get",
      params: { keyword },
    });
  },

  /**
   * 获取当前用户
   *
   * `GET /api/v1/users/me`
   */
  getCurrentUser() {
    return request<CurrentUserResponseVO>({
      url: `${USERS_BASE_URL}/me`,
      method: "get",
    });
  },

  /**
   * 列出我收到的团队邀请
   *
   * `GET /api/v1/users/me/team-invitations`
   * @param status 查询参数
   */
  listMyInvitations(status?: string) {
    return request<InvitationResponseVO[]>({
      url: `${USERS_BASE_URL}/me/team-invitations`,
      method: "get",
      params: { status },
    });
  },
};

export default UserAPI;
