/**
 * 团队空间 mock，对应 `src/api/generated/team.api.ts` 的团队 CRUD、成员、邀请部分。
 *
 * 团队存储（files/dirs/trash/shares/permissions/quota）在 `team-storage.mock.ts` 中。
 * 当前用户由请求头 token 解析（见 data/user.state.ts）。
 */
import { defineMock } from "vite-plugin-mock-dev-server";
import { badRequest, conflict, daysFromNow, forbidden, notFound, now, ok } from "./shared";
import { currentUserId, users } from "./data/user.state";
import {
  ASSIGNABLE_ROLES,
  type InvitationStatus,
  type MockInvitation,
  type MockTeam,
  type TeamRole,
  getMembers,
  invitationSequence,
  invitations,
  isDissolved,
  isManager,
  members,
  refreshExpiredInvitations,
  roleOf,
  teamSequence,
  teams,
  teamsOf,
  toInvitationView,
  toMemberView,
  toTeamView,
} from "./data/team.state";

const GB = 1024 ** 3;
const DEFAULT_INVITE_DAYS = 7;

/** 找出团队下指定状态的某用户邀请 */
function findInvitation(teamId: number, invitationId: number): MockInvitation | undefined {
  const inv = invitations.get(invitationId);
  return inv && inv.teamId === teamId ? inv : undefined;
}

/** 把邀请从 pending 推进到终态，复用一致的失效校验 */
function transitionInvitation(
  inv: MockInvitation,
  next: Exclude<InvitationStatus, "pending">
): { ok: true } | { ok: false; reason: ReturnType<typeof conflict> } {
  if (inv.status !== "pending") {
    return { ok: false, reason: conflict("该邀请已处理或已失效") };
  }
  inv.status = next;
  return { ok: true };
}

export default defineMock([
  /* ---------------------------- 团队 ---------------------------- */

  /** 我加入的团队列表（不含已解散） */
  {
    url: "/dev-api/api/v1/team",
    method: "GET",
    body: (req) => {
      const me = currentUserId(req);
      return ok(
        teamsOf(me)
          .filter((t) => !t.dissolved)
          .map((t) => toTeamView(t, me))
      );
    },
  },

  /** 创建团队（创建者自动成为 Owner） */
  {
    url: "/dev-api/api/v1/team",
    method: "POST",
    body: (req) => {
      const me = currentUserId(req);
      const { name, description } = (req.body || {}) as { name?: string; description?: string };
      if (!name || !name.trim()) return badRequest("团队名不能为空");

      const team: MockTeam = {
        id: teamSequence.next(),
        name: name.trim(),
        description: description?.trim() || "",
        totalQuota: 2 * GB,
        usedSpace: 0,
        createTime: now(),
        dissolved: false,
      };
      teams.set(team.id, team);
      members.set(team.id, [{ userId: me, role: "Owner", joinTime: now() }]);
      return ok(toTeamView(team, me));
    },
  },

  /** 我收到的邀请 */
  {
    url: "/dev-api/api/v1/team/invitations/received",
    method: "GET",
    body: (req) => {
      refreshExpiredInvitations();
      const me = currentUserId(req);
      const list = Array.from(invitations.values())
        .filter((inv) => inv.inviteeAccountId === me)
        .sort((a, b) => (a.createTime < b.createTime ? 1 : -1));
      return ok(list.map(toInvitationView));
    },
  },

  /** 团队详情 */
  {
    url: "/dev-api/api/v1/team/:id",
    method: "GET",
    body: (req) => {
      const me = currentUserId(req);
      const team = teams.get(Number(req.params.id));
      if (!team) return notFound("团队不存在");
      if (!roleOf(team.id, me)) return forbidden("无权限访问该团队");
      return ok(toTeamView(team, me));
    },
  },

  /** 修改团队资料（仅 Owner / Admin） */
  {
    url: "/dev-api/api/v1/team/:id",
    method: "PUT",
    body: (req) => {
      const me = currentUserId(req);
      const team = teams.get(Number(req.params.id));
      if (!team) return notFound("团队不存在");
      if (team.dissolved) return conflict("团队已解散");
      if (!isManager(roleOf(team.id, me))) return forbidden("没有修改团队资料的权限");

      const { name, description } = (req.body || {}) as { name?: string; description?: string };
      if (!name || !name.trim()) return badRequest("团队名不能为空");
      team.name = name.trim();
      team.description = description?.trim() || "";
      return ok(toTeamView(team, me));
    },
  },

  /** 解散团队（仅 Owner，标记 dissolved 而非物理删除，保留邀请历史） */
  {
    url: "/dev-api/api/v1/team/:id/dissolve",
    method: "POST",
    body: (req) => {
      const me = currentUserId(req);
      const teamId = Number(req.params.id);
      const team = teams.get(teamId);
      if (!team) return notFound("团队不存在");
      if (team.dissolved) return conflict("团队已解散");
      if (roleOf(teamId, me) !== "Owner") return forbidden("只有 Owner 可以解散团队");

      team.dissolved = true;
      members.set(teamId, []);
      invitations.forEach((inv) => {
        if (inv.teamId === teamId && inv.status === "pending") inv.status = "revoked";
      });
      return ok(null);
    },
  },

  /** 退出团队（Owner 需先转让所有权） */
  {
    url: "/dev-api/api/v1/team/:id/leave",
    method: "POST",
    body: (req) => {
      const me = currentUserId(req);
      const teamId = Number(req.params.id);
      if (isDissolved(teamId)) return conflict("团队已解散");
      const role = roleOf(teamId, me);
      if (!role) return forbidden("你不是该团队成员");
      if (role === "Owner") return conflict("Owner 不能直接退出，请先转让所有权或解散团队");

      members.set(
        teamId,
        getMembers(teamId).filter((m) => m.userId !== me)
      );
      return ok(null);
    },
  },

  /** 转让所有权（仅 Owner，原 Owner 降级为 Admin） */
  {
    url: "/dev-api/api/v1/team/:id/transfer-owner",
    method: "POST",
    body: (req) => {
      const me = currentUserId(req);
      const teamId = Number(req.params.id);
      if (isDissolved(teamId)) return conflict("团队已解散");
      if (roleOf(teamId, me) !== "Owner") return forbidden("只有 Owner 可以转让所有权");

      const { targetUserId } = (req.body || {}) as { targetUserId?: number };
      const list = getMembers(teamId);
      const target = list.find((m) => m.userId === targetUserId);
      if (!target) return badRequest("目标用户不是团队成员");
      if (target.userId === me) return badRequest("不能转让给自己");

      list.forEach((m) => {
        if (m.userId === me) m.role = "Admin";
        if (m.userId === targetUserId) m.role = "Owner";
      });
      return ok(null);
    },
  },

  /* ---------------------------- 成员 ---------------------------- */

  /** 团队成员列表 */
  {
    url: "/dev-api/api/v1/team/:id/members",
    method: "GET",
    body: (req) => {
      const me = currentUserId(req);
      const teamId = Number(req.params.id);
      if (!teams.has(teamId)) return notFound("团队不存在");
      if (!roleOf(teamId, me)) return forbidden("无权限访问该团队");
      return ok(getMembers(teamId).map(toMemberView));
    },
  },

  /** 移除成员（Owner / Admin，且不能移除 Owner） */
  {
    url: "/dev-api/api/v1/team/:id/members/:memberId",
    method: "DELETE",
    body: (req) => {
      const me = currentUserId(req);
      const teamId = Number(req.params.id);
      if (isDissolved(teamId)) return conflict("团队已解散");
      const memberId = Number(req.params.memberId);
      if (!isManager(roleOf(teamId, me))) return forbidden("没有移除成员的权限");

      const target = getMembers(teamId).find((m) => m.userId === memberId);
      if (!target) return notFound("成员不存在");
      if (target.role === "Owner") return conflict("不能移除 Owner");

      members.set(
        teamId,
        getMembers(teamId).filter((m) => m.userId !== memberId)
      );
      return ok(null);
    },
  },

  /** 修改成员角色（仅 Owner，不能改 Owner，且只能改为可分配角色） */
  {
    url: "/dev-api/api/v1/team/:id/members/:memberId/role",
    method: "PUT",
    body: (req) => {
      const me = currentUserId(req);
      const teamId = Number(req.params.id);
      if (isDissolved(teamId)) return conflict("团队已解散");
      const memberId = Number(req.params.memberId);
      const { role } = (req.body || {}) as { role?: TeamRole };
      if (roleOf(teamId, me) !== "Owner") return forbidden("只有 Owner 可以修改成员角色");
      if (!role || !ASSIGNABLE_ROLES.includes(role)) return badRequest("非法的角色");

      const target = getMembers(teamId).find((m) => m.userId === memberId);
      if (!target) return notFound("成员不存在");
      if (target.role === "Owner") return conflict("不能修改 Owner 的角色");

      target.role = role;
      return ok(null);
    },
  },

  /* ---------------------------- 邀请 ---------------------------- */

  /** 团队的邀请列表（Owner / Admin），支持 status 过滤 */
  {
    url: "/dev-api/api/v1/team/:id/invitations",
    method: "GET",
    body: (req) => {
      const me = currentUserId(req);
      const teamId = Number(req.params.id);
      if (!teams.has(teamId)) return notFound("团队不存在");
      if (!isManager(roleOf(teamId, me))) return forbidden("没有查看邀请的权限");

      refreshExpiredInvitations();
      const filter = req.query.status ? String(req.query.status) : null;
      const list = Array.from(invitations.values())
        .filter((inv) => inv.teamId === teamId)
        .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
        .map(toInvitationView)
        .filter((view) => !filter || view.status === filter);
      return ok(list);
    },
  },

  /** 发起邀请（细粒度，Owner / Admin） */
  {
    url: "/dev-api/api/v1/team/:id/invitations",
    method: "POST",
    body: (req) => {
      const me = currentUserId(req);
      const teamId = Number(req.params.id);
      if (isDissolved(teamId)) return conflict("团队已解散");
      if (!isManager(roleOf(teamId, me))) return forbidden("没有邀请成员的权限");

      const { inviteeId, targetRole } = (req.body || {}) as {
        inviteeId?: number;
        targetRole?: TeamRole;
      };
      const result = createInvitation({ teamId, inviterId: me, inviteeId, targetRole });
      return result.ok ? ok(toInvitationView(result.inv)) : result.error;
    },
  },

  /** 统一邀请动作：INVITE / ACCEPT / REJECT / REVOKE */
  {
    url: "/dev-api/api/v1/team/:id/invitations/actions",
    method: "POST",
    body: (req) => {
      refreshExpiredInvitations();
      const me = currentUserId(req);
      const teamId = Number(req.params.id);
      const body = (req.body || {}) as {
        action?: "INVITE" | "ACCEPT" | "REJECT" | "REVOKE";
        invitationId?: number;
        inviteeAccountId?: number;
        roleCode?: TeamRole;
        reason?: string;
      };

      switch (body.action) {
        case "INVITE": {
          if (isDissolved(teamId)) return conflict("团队已解散");
          if (!isManager(roleOf(teamId, me))) return forbidden("没有邀请成员的权限");
          const result = createInvitation({
            teamId,
            inviterId: me,
            inviteeId: body.inviteeAccountId,
            targetRole: body.roleCode,
          });
          return result.ok ? ok(toInvitationView(result.inv)) : result.error;
        }
        case "ACCEPT":
        case "REJECT": {
          if (!body.invitationId) return badRequest("缺少 invitationId");
          const inv = findInvitation(teamId, body.invitationId);
          if (!inv) return notFound("邀请不存在");
          if (inv.inviteeAccountId !== me) return forbidden("只能处理自己的邀请");
          const next = body.action === "ACCEPT" ? "accepted" : "rejected";
          const t = transitionInvitation(inv, next);
          if (!t.ok) return t.reason;
          if (next === "accepted") joinTeamFromInvitation(inv);
          return ok(null);
        }
        case "REVOKE": {
          if (!body.invitationId) return badRequest("缺少 invitationId");
          const inv = findInvitation(teamId, body.invitationId);
          if (!inv) return notFound("邀请不存在");
          if (!isManager(roleOf(teamId, me)) && inv.inviterUserId !== me) {
            return forbidden("没有撤回该邀请的权限");
          }
          const t = transitionInvitation(inv, "revoked");
          return t.ok ? ok(null) : t.reason;
        }
        default:
          return badRequest("非法的 action");
      }
    },
  },

  /** 接受邀请 */
  {
    url: "/dev-api/api/v1/team/:id/invitations/:invitationId/accept",
    method: "PUT",
    body: (req) => {
      refreshExpiredInvitations();
      const me = currentUserId(req);
      const inv = findInvitation(Number(req.params.id), Number(req.params.invitationId));
      if (!inv) return notFound("邀请不存在");
      if (inv.inviteeAccountId !== me) return forbidden("只能处理自己的邀请");
      const t = transitionInvitation(inv, "accepted");
      if (!t.ok) return t.reason;
      joinTeamFromInvitation(inv);
      return ok(null);
    },
  },

  /** 拒绝邀请 */
  {
    url: "/dev-api/api/v1/team/:id/invitations/:invitationId/reject",
    method: "PUT",
    body: (req) => {
      refreshExpiredInvitations();
      const me = currentUserId(req);
      const inv = findInvitation(Number(req.params.id), Number(req.params.invitationId));
      if (!inv) return notFound("邀请不存在");
      if (inv.inviteeAccountId !== me) return forbidden("只能处理自己的邀请");
      const t = transitionInvitation(inv, "rejected");
      return t.ok ? ok(null) : t.reason;
    },
  },
]);

/** 校验并创建邀请；返回成功的 inv 或失败响应 */
function createInvitation(input: {
  teamId: number;
  inviterId: number;
  inviteeId?: number;
  targetRole?: TeamRole;
}): { ok: true; inv: MockInvitation } | { ok: false; error: ReturnType<typeof badRequest> } {
  const { teamId, inviterId, inviteeId, targetRole } = input;
  if (!inviteeId || !users.has(inviteeId)) {
    return { ok: false, error: badRequest("被邀请用户不存在") };
  }
  if (!targetRole || !ASSIGNABLE_ROLES.includes(targetRole)) {
    return { ok: false, error: badRequest("非法的角色") };
  }
  if (getMembers(teamId).some((m) => m.userId === inviteeId)) {
    return { ok: false, error: conflict("该用户已是团队成员") };
  }
  refreshExpiredInvitations();
  const hasPending = Array.from(invitations.values()).some(
    (inv) => inv.teamId === teamId && inv.inviteeAccountId === inviteeId && inv.status === "pending"
  );
  if (hasPending) return { ok: false, error: conflict("该用户已有待处理的邀请") };

  const inv: MockInvitation = {
    id: invitationSequence.next(),
    teamId,
    inviterUserId: inviterId,
    inviteeAccountId: inviteeId,
    role: targetRole,
    status: "pending",
    createTime: now(),
    expireTime: daysFromNow(DEFAULT_INVITE_DAYS),
  };
  invitations.set(inv.id, inv);
  return { ok: true, inv };
}

/** 接受邀请后把被邀请人加入团队 */
function joinTeamFromInvitation(inv: MockInvitation): void {
  const list = getMembers(inv.teamId);
  if (list.some((m) => m.userId === inv.inviteeAccountId)) return;
  list.push({ userId: inv.inviteeAccountId, role: inv.role, joinTime: now() });
  members.set(inv.teamId, list);
}
