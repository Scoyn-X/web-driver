/**
 * 团队 / 成员 / 邀请的 Mock 状态。dev server 重启即重置。
 *
 * 设计要点：
 * - 团队的 Owner 不单独存字段，统一由 members 中 role === "Owner" 的成员推导，避免数据不一致。
 * - 角色 / 状态使用字符串字面量，与 `src/enums/team.enum.ts` 的枚举值保持一致。
 */
import { createSequence, daysAgo, daysFromNow } from "../shared";
import { displayName, users } from "./user.state";

export type TeamRole = "Owner" | "Admin" | "Editor" | "Viewer";
export type TeamStatus = "ACTIVE" | "DISSOLVED";
export type InvitationStatus = "pending" | "accepted" | "rejected" | "expired" | "revoked";
export type InvitationStatusView =
  | "PENDING"
  | "ACCEPTED"
  | "REJECTED"
  | "REVOKED"
  | "EXPIRED"
  | "TEAM_DISSOLVED";

export interface MockTeam {
  id: number;
  name: string;
  description: string;
  totalQuota: number;
  usedSpace: number;
  createTime: string;
  dissolved: boolean;
}

export interface MockMember {
  userId: number;
  role: TeamRole;
  joinTime: string;
}

export interface MockInvitation {
  id: number;
  teamId: number;
  inviterUserId: number;
  inviteeAccountId: number;
  role: TeamRole;
  status: InvitationStatus;
  createTime: string;
  expireTime: string;
}

const GB = 1024 ** 3;

const _g = globalThis as any;

export const teams: Map<number, MockTeam> = (_g.__mockTeams ??= new Map([
  [
    1,
    {
      id: 1,
      name: "演示团队",
      description: "Phase A 演示用",
      totalQuota: 5 * GB,
      usedSpace: Math.round(1.2 * GB),
      createTime: daysAgo(7),
      dissolved: false,
    },
  ],
  [
    2,
    {
      id: 2,
      name: "外部协作组",
      description: "Alice 的团队",
      totalQuota: 2 * GB,
      usedSpace: Math.round(0.3 * GB),
      createTime: daysAgo(3),
      dissolved: false,
    },
  ],
]));

export const members: Map<number, MockMember[]> = (_g.__mockMembers ??= new Map([
  [
    1,
    [
      { userId: 1, role: "Owner", joinTime: daysAgo(7) },
      { userId: 2, role: "Editor", joinTime: daysAgo(5) },
      { userId: 3, role: "Viewer", joinTime: daysAgo(3) },
    ],
  ],
  [
    2,
    [
      { userId: 2, role: "Owner", joinTime: daysAgo(3) },
      { userId: 4, role: "Admin", joinTime: daysAgo(2) },
    ],
  ],
]));

export const invitations: Map<number, MockInvitation> = (_g.__mockInvitations ??= new Map([
  [
    1,
    {
      id: 1,
      teamId: 1,
      inviterUserId: 1,
      inviteeAccountId: 4,
      role: "Editor",
      status: "pending",
      createTime: daysAgo(1),
      expireTime: daysFromNow(6),
    },
  ],
  [
    2,
    {
      id: 2,
      teamId: 2,
      inviterUserId: 2,
      inviteeAccountId: 1,
      role: "Editor",
      status: "pending",
      createTime: daysAgo(1),
      expireTime: daysFromNow(6),
    },
  ],
  [
    3,
    {
      id: 3,
      teamId: 2,
      inviterUserId: 2,
      inviteeAccountId: 1,
      role: "Viewer",
      status: "expired",
      createTime: daysAgo(30),
      expireTime: daysAgo(23),
    },
  ],
]));

export const teamSequence = createSequence(Math.max(...Array.from(teams.keys())));
export const invitationSequence = createSequence(Math.max(...Array.from(invitations.keys())));

/** 可分配角色（不含 Owner，Owner 仅经转让流程产生） */
export const ASSIGNABLE_ROLES: TeamRole[] = ["Admin", "Editor", "Viewer"];

/** Owner / Admin 视为管理者，可管理成员与邀请 */
export const isManager = (role: TeamRole | null): boolean => role === "Owner" || role === "Admin";

/** 取团队成员列表（不存在则返回空数组） */
export const getMembers = (teamId: number): MockMember[] => members.get(teamId) || [];

/** 取某用户在团队中的角色，非成员返回 null */
export function roleOf(teamId: number, userId: number): TeamRole | null {
  return getMembers(teamId).find((m) => m.userId === userId)?.role ?? null;
}

/** 取某用户加入的全部团队 */
export function teamsOf(userId: number): MockTeam[] {
  return Array.from(teams.values()).filter((t) =>
    getMembers(t.id).some((m) => m.userId === userId)
  );
}

/** 团队是否已解散 */
export const isDissolved = (teamId: number): boolean => !!teams.get(teamId)?.dissolved;

/** 把团队聚合为对外视图（同时满足 TeamSummary 与 TeamDetail） */
export function toTeamView(team: MockTeam, viewerId: number) {
  const list = getMembers(team.id);
  const owner = list.find((m) => m.role === "Owner");
  return {
    id: team.id,
    name: team.name,
    description: team.description,
    myRole: roleOf(team.id, viewerId) ?? "Viewer",
    ownerName: owner ? displayName(owner.userId) : "-",
    memberCount: list.length,
    totalQuota: team.totalQuota,
    usedSpace: team.usedSpace,
    createTime: team.createTime,
    status: team.dissolved ? "DISSOLVED" : "ACTIVE",
  };
}

/** 把成员补全为对外视图（带账号 / 昵称） */
export function toMemberView(member: MockMember) {
  const user = users.get(member.userId);
  return {
    userId: member.userId,
    accountName: user?.accountName ?? "unknown",
    nickname: user?.nickname ?? displayName(member.userId),
    role: member.role,
    joinTime: member.joinTime,
  };
}

/** 把邀请补全为对外视图（带团队名 / 邀请人 / 被邀请人；status 输出大写枚举） */
export function toInvitationView(inv: MockInvitation) {
  const team = teams.get(inv.teamId);
  const status: InvitationStatusView = team?.dissolved
    ? "TEAM_DISSOLVED"
    : (inv.status.toUpperCase() as InvitationStatusView);
  return {
    id: inv.id,
    teamId: inv.teamId,
    teamName: team?.name ?? "-",
    inviterId: inv.inviterUserId,
    inviterName: displayName(inv.inviterUserId),
    inviteeId: inv.inviteeAccountId,
    inviteeName: displayName(inv.inviteeAccountId),
    targetRole: inv.role,
    status,
    expireAt: inv.expireTime,
    createTime: inv.createTime,
  };
}

/** 惰性刷新过期邀请：把已过期的 pending 邀请标记为 expired */
export function refreshExpiredInvitations(): void {
  const ts = Date.now();
  invitations.forEach((inv) => {
    if (inv.status === "pending" && new Date(inv.expireTime).getTime() < ts) {
      inv.status = "expired";
    }
  });
}
