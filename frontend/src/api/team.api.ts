/** Team 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const TEAM_BASE_URL = `${API_BASE}/team`;

// ==================== 数据模型 ====================

/** 目录路径面包屑节点 */
export interface BreadcrumbItemResponseVO {
  /** 目录ID（0表示根目录） */
  id?: number;
  /** 目录名称 */
  name?: string;
}

/** 创建团队邀请请求 */
export interface CreateInvitationRequestVO {
  /** 被邀请用户ID，兼容旧客户端；新客户端使用 inviteeAccountId */
  inviteeUserId?: number;
  /** 被邀请账户ID，发起邀请时必填 */
  inviteeAccountId?: number;
  /** 目标角色(Admin/Editor/Viewer)，发起邀请时必填 */
  roleCode?: string;
  /** 邀请有效期秒数，默认86400秒（24小时） */
  expireSeconds?: number;
  /** 邀请备注 */
  reason?: string;
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

/** 目录重命名请求 */
export interface DirectoryRenameRequestVO {
  /** 新目录名称 */
  name: string;
}

/** 文件复制请求 */
export interface FileCopyRequestVO {
  /** 目标目录ID（0表示根目录） */
  targetDirectoryId: number;
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

/** 团队邀请动作请求 */
export interface InvitationActionRequestVO {
  /** 邀请动作(INVITE/ACCEPT/REJECT/REVOKE) */
  action: "INVITE" | "ACCEPT" | "REJECT" | "REVOKE";
  /** 邀请ID，处理已有邀请时必填 */
  invitationId?: number;
  /** 被邀请用户ID，兼容旧客户端；新客户端使用 inviteeAccountId */
  inviteeUserId?: number;
  /** 被邀请账户ID，发起邀请时必填 */
  inviteeAccountId?: number;
  /** 目标角色(Admin/Editor/Viewer)，发起邀请时必填 */
  roleCode?: string;
  /** 邀请有效期秒数，默认86400秒（24小时） */
  expireSeconds?: number;
  /** 拒绝或撤销原因 */
  reason?: string;
}

/** 团队邀请动作响应 */
export interface InvitationActionResponseVO {
  /** 邀请动作 */
  action?: "INVITE" | "ACCEPT" | "REJECT" | "REVOKE";
  /** 邀请状态 */
  status?: "PENDING" | "ACCEPTED" | "REJECTED" | "REVOKED" | "EXPIRED" | "TEAM_DISSOLVED";
  /** 邀请信息 */
  invitation?: InvitationResponseVO;
  /** 接受邀请后生成的成员信息 */
  member?: TeamMemberResponseVO;
  /** 处理结果说明 */
  message?: string;
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

/** 修改成员角色请求 */
export interface MemberRoleUpdateRequestVO {
  /** 新角色(Admin/Editor/Viewer)，不能修改为Owner */
  role: string;
}

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

/** 创建分享请求 */
export interface ShareCreateRequestVO {
  /** 被分享的文件/目录ID */
  fileId: number;
  /** 访问方式(0-全公开 1-分享码访问) */
  accessType: number;
  /** 有效天数(1/7/30)，null 表示永久有效 */
  expireDays?: number;
}

/** 分享信息 */
export interface ShareInfoResponseVO {
  /** 分享记录ID */
  id?: number;
  /** 被分享的文件/目录ID */
  fileId?: number;
  /** 文件名；若文件已删除，显示 [已删除] */
  fileName?: string;
  /** 是否为目录；文件记录缺失时为 null */
  isDirectory?: boolean;
  /** 分享 token，前端拼接为 /s/{token} */
  shareToken?: string;
  /** 访问方式(0-全公开 1-分享码访问) */
  accessType?: number;
  /** 提取码（仅 accessType=1 时返回给创建者） */
  extractCode?: string;
  /** 过期时间，null 表示永久有效 */
  expireTime?: string;
  /** 当前状态文案：有效 / 已过期 / 已取消 */
  statusDesc?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 创建团队请求 */
export interface TeamCreateRequestVO {
  /** 团队名称 */
  name: string;
  /** 团队描述 */
  description?: string;
}

/** 解散团队请求 */
export interface TeamDissolveRequestVO {
  /** 解散原因 */
  reason?: string;
}

export interface TeamFileResponseVO {
  /** 文件或目录ID */
  id?: number;
  /** 团队ID */
  teamId?: number;
  /** 上传者ID */
  uploaderId?: number;
  /** 上传者名称 */
  uploaderName?: string;
  /** 文件原始名称 */
  originalName?: string;
  /** 文件大小 */
  fileSize?: number;
  /** MIME 类型 */
  mimeType?: string;
  /** 创建时间 */
  createTime?: string;
  /** 文件访问 URL */
  fileUrl?: string;
  /** 是否为目录 */
  isDirectory?: number;
  /** 父目录ID */
  parentId?: number;
  /** 祖先ID路径 */
  fullPath?: number[];
}

/** 团队成员响应 */
export interface TeamMemberResponseVO {
  /** 成员记录ID */
  id?: number;
  /** 团队ID */
  teamId?: number;
  /** 团队名称 */
  teamName?: string;
  /** 团队简介 */
  teamDescription?: string;
  /** 用户ID */
  userId?: number;
  /** 成员账户ID */
  accountId?: number;
  /** 成员账户名 */
  accountName?: string;
  /** 用户名 */
  username?: string;
  /** 用户邮箱 */
  email?: string;
  /** 团队角色(Owner/Admin/Editor/Viewer) */
  role?: string;
  /** 成员状态(ACTIVE-正常 REMOVED-已被移除 EXITED-已退出) */
  status?: string;
  /** 加入时间 */
  joinedAt?: string;
}

/** 私密空间提醒 */
export interface TeamPermissionPrivateSpaceReminderResponseVO {
  /** 是否展示提醒 */
  visible?: boolean;
  /** 提醒文案 */
  message?: string;
}

/** 团队权限响应 */
export interface TeamPermissionResponseVO {
  /** 团队ID */
  teamId: number;
  /** 团队名称 */
  teamName: string;
  /** 团队状态(ACTIVE/DISSOLVED) */
  teamStatus: string;
  /** 当前用户角色(Owner/Admin/Editor/Viewer) */
  role: string;
  /** 当前用户权限点列表 */
  permissions: string[];
  /** 配额状态(NORMAL/OVER_LIMIT) */
  quotaState: string;
  /** VIP状态(VIP/NORMAL) */
  vipState: string;
  /** 单文件大小限制（字节），VIP不限时为空 */
  singleFileLimit?: number;
  /** 下载是否限速 */
  downloadLimited: boolean;
  /** 私密空间提醒 */
  privateSpaceReminder?: TeamPermissionPrivateSpaceReminderResponseVO;
}

/** 团队配额响应 */
export interface TeamQuotaResponseVO {
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

/** 团队信息响应 */
export interface TeamResponseVO {
  /** 团队ID */
  id?: number;
  /** 团队名称 */
  name?: string;
  /** 团队描述 */
  description?: string;
  /** 团队Owner用户ID */
  ownerId?: number;
  /** 团队Owner账户ID */
  ownerAccountId?: number;
  /** 团队Owner用户名 */
  ownerName?: string;
  /** 团队状态(ACTIVE-正常 DISSOLVED-已解散) */
  status?: string;
  /** 当前用户在团队中的角色(Owner/Admin/Editor/Viewer) */
  role?: string;
  /** 团队配额信息 */
  quota?: TeamQuotaResponseVO;
  /** 创建时间 */
  createTime?: string;
}

export interface TeamTrashItemResponseVO {
  /** 文件或目录ID */
  id?: number;
  /** 文件原始名称 */
  originalName?: string;
  /** 完整人类可读路径 */
  path?: string;
  /** 删除者用户ID */
  deletedByUserId?: number;
  /** 删除者主账户ID */
  deletedByAccountId?: number;
  /** 删除者主账户名 */
  deletedByAccountName?: string;
  /** 删除者名称 */
  deletedByName?: string;
  /** 放入回收站时间 */
  deletedAt?: string;
  /** 回收站到期时间 */
  expireAt?: string;
  /** 文件大小 */
  fileSize?: number;
  /** 是否为目录 */
  isDirectory?: number;
  /** 回收站状态 */
  status?: string;
}

/** 修改团队信息请求 */
export interface TeamUpdateRequestVO {
  /** 团队名称 */
  name: string;
  /** 团队描述 */
  description?: string;
}

/** 个人文件转存团队请求 */
export interface TransferFromPersonalRequestVO {
  /** 来源个人文件或目录ID */
  sourceFileId: number;
  /** 目标团队目录ID（0表示根目录） */
  targetDirectoryId?: number;
  /** 兼容字段；B14d1 首版始终自动重命名，不执行覆盖 */
  conflictPolicy?: "RENAME" | "OVERWRITE";
}

/** 转让团队所有权请求 */
export interface TransferOwnerRequestVO {
  /** 目标成员ID，兼容旧客户端；新客户端使用 targetAccountId */
  targetMemberId?: number;
  /** 目标账户ID */
  targetAccountId?: number;
}

/** 团队文件转存个人空间请求 */
export interface TransferToPersonalRequestVO {
  /** 目标个人目录ID（0表示根目录） */
  targetDirectoryId?: number;
  /** 兼容字段；B14d2 首版始终自动重命名，不执行覆盖 */
  conflictPolicy?: "RENAME" | "OVERWRITE";
}

/** team 模块接口集合 */
export const TeamAPI = {
  /**
   * 移动团队文件
   *
   * `PUT /api/v1/team/{teamId}/files/{fileId}/move`
   * @param teamId 路径参数
   * @param fileId 路径参数
   * @param data 请求体 —— 文件/目录移动请求
   */
  moveTeamFile(teamId: number, fileId: number, data: FileMoveRequestVO) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/files/${fileId}/move`,
      method: "put",
      data,
    });
  },

  /**
   * 重命名团队目录
   *
   * `PUT /api/v1/team/{teamId}/directories/{directoryId}/rename`
   * @param teamId 路径参数
   * @param directoryId 路径参数
   * @param data 请求体 —— 目录重命名请求
   */
  renameTeamDirectory(teamId: number, directoryId: number, data: DirectoryRenameRequestVO) {
    return request<TeamFileResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/directories/${directoryId}/rename`,
      method: "put",
      data,
    });
  },

  /**
   * 列出团队文件
   *
   * `GET /api/v1/team/{teamId}/files`
   * @param teamId 路径参数
   * @param parentId 查询参数
   */
  listTeamFiles(teamId: number, parentId: number = 0) {
    return request<FileListResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/files`,
      method: "get",
      params: { parentId },
    });
  },

  /**
   * 上传团队文件
   *
   * `POST /api/v1/team/{teamId}/files`
   * @param teamId 路径参数
   * @param file 上传文件
   * @param parentId 查询参数
   */
  uploadTeamFile(teamId: number, file: File, parentId: number = 0) {
    const formData = new FormData();
    formData.append("file", file);
    return request<TeamFileResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/files`,
      method: "post",
      params: { parentId },
      data: formData,
    });
  },

  /**
   * 转存团队文件到个人空间
   *
   * `POST /api/v1/team/{teamId}/files/{fileId}/save-to-personal`
   * @param teamId 路径参数
   * @param fileId 路径参数
   * @param data 请求体 —— 团队文件转存个人空间请求
   * @returns 文件信息视图对象
   */
  transferToPersonal(teamId: number, fileId: number, data: TransferToPersonalRequestVO) {
    return request<FileInfoResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/files/${fileId}/save-to-personal`,
      method: "post",
      data,
    });
  },

  /**
   * 复制团队文件
   *
   * `POST /api/v1/team/{teamId}/files/{fileId}/copy`
   * @param teamId 路径参数
   * @param fileId 路径参数
   * @param data 请求体 —— 文件复制请求
   */
  copyTeamFile(teamId: number, fileId: number, data: FileCopyRequestVO) {
    return request<TeamFileResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/files/${fileId}/copy`,
      method: "post",
      data,
    });
  },

  /**
   * 转存个人文件到团队
   *
   * `POST /api/v1/team/{teamId}/files/from-personal`
   * @param teamId 路径参数
   * @param data 请求体 —— 个人文件转存团队请求
   */
  transferFromPersonal(teamId: number, data: TransferFromPersonalRequestVO) {
    return request<TeamFileResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/files/from-personal`,
      method: "post",
      data,
    });
  },

  /**
   * 列出团队目录
   *
   * `GET /api/v1/team/{teamId}/directories`
   * @param teamId 路径参数
   * @param parentId 查询参数
   */
  listDirectories(teamId: number, parentId: number = 0) {
    return request<DirectoryNodeResponseVO[]>({
      url: `${TEAM_BASE_URL}/${teamId}/directories`,
      method: "get",
      params: { parentId },
    });
  },

  /**
   * 创建团队目录
   *
   * `POST /api/v1/team/{teamId}/directories`
   * @param teamId 路径参数
   * @param data 请求体 —— 创建目录请求
   */
  createTeamDirectory(teamId: number, data: DirectoryCreateRequestVO) {
    return request<TeamFileResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/directories`,
      method: "post",
      data,
    });
  },

  /**
   * 获取团队文件详情
   *
   * `GET /api/v1/team/{teamId}/files/{fileId}`
   * @param teamId 路径参数
   * @param fileId 路径参数
   */
  getTeamFile(teamId: number, fileId: number) {
    return request<TeamFileResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/files/${fileId}`,
      method: "get",
    });
  },

  /**
   * 删除团队文件
   *
   * `DELETE /api/v1/team/{teamId}/files/{fileId}`
   * @param teamId 路径参数
   * @param fileId 路径参数
   */
  deleteToTrash(teamId: number, fileId: number) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/files/${fileId}`,
      method: "delete",
    });
  },

  /**
   * 下载团队文件
   *
   * `GET /api/v1/team/{teamId}/files/{fileId}/download`
   * @param teamId 路径参数
   * @param fileId 路径参数
   */
  downloadTeamFile(
    teamId: number,
    fileId: number,
    onProgress?: (e: ProgressEvent) => void,
    signal?: AbortSignal
  ) {
    return request<Blob>({
      url: `${TEAM_BASE_URL}/${teamId}/files/${fileId}/download`,
      method: "get",
      responseType: "blob",
      timeout: 0,
      onDownloadProgress: onProgress,
      signal,
    });
  },

  /**
   * 列出团队文件树
   *
   * `GET /api/v1/team/{teamId}/files/tree`
   * @param teamId 路径参数
   */
  listTeamFileTree(teamId: number) {
    return request<FileTreeResponseVO[]>({
      url: `${TEAM_BASE_URL}/${teamId}/files/tree`,
      method: "get",
    });
  },

  /**
   * 搜索团队文件
   *
   * `GET /api/v1/team/{teamId}/files/search`
   * @param teamId 路径参数
   * @param keyword 查询参数
   */
  searchTeamFiles(teamId: number, keyword: string) {
    return request<TeamFileResponseVO[]>({
      url: `${TEAM_BASE_URL}/${teamId}/files/search`,
      method: "get",
      params: { keyword },
    });
  },

  /**
   * 获取团队详情
   *
   * `GET /api/v1/team/{teamId}`
   * @param teamId 路径参数
   * @returns 团队信息响应
   */
  getTeamById(teamId: number) {
    return request<TeamResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}`,
      method: "get",
    });
  },

  /**
   * 修改团队资料
   *
   * `PUT /api/v1/team/{teamId}`
   * @param teamId 路径参数
   * @param data 请求体 —— 修改团队信息请求
   * @returns 团队信息响应
   */
  updateTeam(teamId: number, data: TeamUpdateRequestVO) {
    return request<TeamResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}`,
      method: "put",
      data,
    });
  },

  /**
   * 转让团队所有权
   *
   * `PUT /api/v1/team/{teamId}/owner/transfer`
   * @param teamId 路径参数
   * @param data 请求体 —— 转让团队所有权请求
   */
  transferOwner(teamId: number, data: TransferOwnerRequestVO) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/owner/transfer`,
      method: "put",
      data,
    });
  },

  /**
   * 修改成员角色
   *
   * `PUT /api/v1/team/{teamId}/members/{memberId}/role`
   * @param teamId 路径参数
   * @param memberId 路径参数
   * @param data 请求体 —— 修改成员角色请求
   * @returns 团队成员响应
   */
  updateMemberRole(teamId: number, memberId: number, data: MemberRoleUpdateRequestVO) {
    return request<TeamMemberResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/members/${memberId}/role`,
      method: "put",
      data,
    });
  },

  /**
   * 列出我的团队
   *
   * `GET /api/v1/team`
   */
  listUserTeams() {
    return request<TeamResponseVO[]>({
      url: TEAM_BASE_URL,
      method: "get",
    });
  },

  /**
   * 创建团队
   *
   * `POST /api/v1/team`
   * @param data 请求体 —— 创建团队请求
   * @returns 团队信息响应
   */
  createTeam(data: TeamCreateRequestVO) {
    return request<TeamResponseVO>({
      url: TEAM_BASE_URL,
      method: "post",
      data,
    });
  },

  /**
   * 转让团队所有权
   *
   * `POST /api/v1/team/{teamId}/transfer-owner`
   * @param teamId 路径参数
   * @param data 请求体 —— 转让团队所有权请求
   */
  transferOwnerPost(teamId: number, data: TransferOwnerRequestVO) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/transfer-owner`,
      method: "post",
      data,
    });
  },

  /**
   * 退出团队
   *
   * `POST /api/v1/team/{teamId}/leave`
   * @param teamId 路径参数
   */
  leaveTeam(teamId: number) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/leave`,
      method: "post",
    });
  },

  /**
   * 解散团队
   *
   * `POST /api/v1/team/{teamId}/dissolve`
   * @param teamId 路径参数
   * @param data 请求体 —— 解散团队请求
   */
  dissolveTeam(teamId: number, data?: TeamDissolveRequestVO) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/dissolve`,
      method: "post",
      data,
    });
  },

  /**
   * 获取团队配额
   *
   * `GET /api/v1/team/{teamId}/quota`
   * @param teamId 路径参数
   * @returns 团队配额响应
   */
  getTeamQuota(teamId: number) {
    return request<TeamQuotaResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/quota`,
      method: "get",
    });
  },

  /**
   * 列出团队成员
   *
   * `GET /api/v1/team/{teamId}/members`
   * @param teamId 路径参数
   */
  listMembers(teamId: number) {
    return request<TeamMemberResponseVO[]>({
      url: `${TEAM_BASE_URL}/${teamId}/members`,
      method: "get",
    });
  },

  /**
   * 移除成员
   *
   * `DELETE /api/v1/team/{teamId}/members/{memberId}`
   * @param teamId 路径参数
   * @param memberId 路径参数
   */
  removeMember(teamId: number, memberId: number) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/members/${memberId}`,
      method: "delete",
    });
  },

  /**
   * 退出团队
   *
   * `DELETE /api/v1/team/{teamId}/members/me`
   * @param teamId 路径参数
   */
  exitTeam(teamId: number) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/members/me`,
      method: "delete",
    });
  },

  /**
   * 拒绝邀请
   *
   * `PUT /api/v1/team/{teamId}/invitations/{invitationId}/reject`
   * @param teamId 路径参数
   * @param invitationId 路径参数
   * @returns 团队邀请动作响应
   */
  rejectInvitation(teamId: number, invitationId: number) {
    return request<InvitationActionResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/invitations/${invitationId}/reject`,
      method: "put",
    });
  },

  /**
   * 接受邀请
   *
   * `PUT /api/v1/team/{teamId}/invitations/{invitationId}/accept`
   * @param teamId 路径参数
   * @param invitationId 路径参数
   * @returns 团队邀请动作响应
   */
  acceptInvitation(teamId: number, invitationId: number) {
    return request<InvitationActionResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/invitations/${invitationId}/accept`,
      method: "put",
    });
  },

  /**
   * 列出团队邀请
   *
   * `GET /api/v1/team/{teamId}/invitations`
   * @param teamId 路径参数
   * @param status 查询参数
   */
  listTeamInvitations(teamId: number, status?: string) {
    return request<InvitationResponseVO[]>({
      url: `${TEAM_BASE_URL}/${teamId}/invitations`,
      method: "get",
      params: { status },
    });
  },

  /**
   * 发起邀请
   *
   * `POST /api/v1/team/{teamId}/invitations`
   * @param teamId 路径参数
   * @param data 请求体 —— 创建团队邀请请求
   * @returns 团队邀请动作响应
   */
  createInvitation(teamId: number, data: CreateInvitationRequestVO) {
    return request<InvitationActionResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/invitations`,
      method: "post",
      data,
    });
  },

  /**
   * 统一处理团队邀请动作
   *
   * `POST /api/v1/team/{teamId}/invitations/actions`
   * @param teamId 路径参数
   * @param data 请求体 —— 团队邀请动作请求
   * @returns 团队邀请动作响应
   */
  handleInvitationAction(teamId: number, data: InvitationActionRequestVO) {
    return request<InvitationActionResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/invitations/actions`,
      method: "post",
      data,
    });
  },

  /**
   * 列出我收到的团队邀请
   *
   * `GET /api/v1/team/invitations/received`
   * @param status 查询参数
   */
  listReceivedInvitations(status?: string) {
    return request<InvitationResponseVO[]>({
      url: `${TEAM_BASE_URL}/invitations/received`,
      method: "get",
      params: { status },
    });
  },

  /**
   * 恢复团队回收站文件/目录
   *
   * `POST /api/v1/team/{teamId}/trash/{trashId}/restore`
   * @param teamId 路径参数
   * @param trashId 路径参数
   * @param conflictPolicy 查询参数
   */
  restoreTrash(teamId: number, trashId: number, conflictPolicy?: "RENAME" | "OVERWRITE") {
    return request<TeamFileResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/trash/${trashId}/restore`,
      method: "post",
      params: { conflictPolicy },
    });
  },

  /**
   * 列出团队回收站文件/目录
   *
   * `GET /api/v1/team/{teamId}/trash`
   * @param teamId 路径参数
   */
  listTrash(teamId: number) {
    return request<TeamTrashItemResponseVO[]>({
      url: `${TEAM_BASE_URL}/${teamId}/trash`,
      method: "get",
    });
  },

  /**
   * 永久删除回收站文件
   *
   * `DELETE /api/v1/team/{teamId}/trash/{trashId}`
   * @param teamId 路径参数
   * @param trashId 路径参数
   */
  permanentlyDeleteTrash(teamId: number, trashId: number) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/trash/${trashId}`,
      method: "delete",
    });
  },

  /**
   * 列出团队全部分享
   *
   * `GET /api/v1/team/{teamId}/shares`
   * @param teamId 路径参数
   */
  listTeamShares(teamId: number) {
    return request<ShareInfoResponseVO[]>({
      url: `${TEAM_BASE_URL}/${teamId}/shares`,
      method: "get",
    });
  },

  /**
   * 创建团队分享
   *
   * `POST /api/v1/team/{teamId}/shares`
   * @param teamId 路径参数
   * @param data 请求体 —— 创建分享请求
   * @returns 分享信息
   */
  createTeamShare(teamId: number, data: ShareCreateRequestVO) {
    return request<ShareInfoResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/shares`,
      method: "post",
      data,
    });
  },

  /**
   * 获取团队分享详情
   *
   * `GET /api/v1/team/{teamId}/shares/{shareId}`
   * @param teamId 路径参数
   * @param shareId 路径参数
   * @returns 分享信息
   */
  getTeamShare(teamId: number, shareId: number) {
    return request<ShareInfoResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/shares/${shareId}`,
      method: "get",
    });
  },

  /**
   * 取消团队分享
   *
   * `DELETE /api/v1/team/{teamId}/shares/{shareId}`
   * @param teamId 路径参数
   * @param shareId 路径参数
   */
  cancelTeamShare(teamId: number, shareId: number) {
    return request<void>({
      url: `${TEAM_BASE_URL}/${teamId}/shares/${shareId}`,
      method: "delete",
    });
  },

  /**
   * 获取团队权限
   *
   * `GET /api/v1/team/{teamId}/permissions`
   * @param teamId 路径参数
   * @returns 团队权限响应
   */
  getTeamPermissions(teamId: number) {
    return request<TeamPermissionResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/permissions`,
      method: "get",
    });
  },

  /**
   * 获取团队菜单
   *
   * `GET /api/v1/team/{teamId}/menus`
   * @param teamId 路径参数
   * @returns 菜单树响应
   */
  listTeamMenus(teamId: number) {
    return request<MenuTreeResponseVO>({
      url: `${TEAM_BASE_URL}/${teamId}/menus`,
      method: "get",
    });
  },
};

export default TeamAPI;
