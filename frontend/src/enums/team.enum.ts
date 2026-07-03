/**
 * 团队空间相关的枚举：成员角色、邀请状态。
 * 与 `mock/data/team.state.ts` 保持字符串值一致，便于 mock 与真实接口互换。
 */

export enum TeamRole {
  Owner = "Owner",
  Admin = "Admin",
  Editor = "Editor",
  Viewer = "Viewer",
}

export enum InvitationStatus {
  Pending = "pending",
  Accepted = "accepted",
  Rejected = "rejected",
  Expired = "expired",
  Cancelled = "cancelled",
}

/** 可在邀请/成员编辑场景中分配的角色（不含 Owner，Owner 仅通过转让流程变更） */
export const ASSIGNABLE_ROLES: TeamRole[] = [TeamRole.Admin, TeamRole.Editor, TeamRole.Viewer];
