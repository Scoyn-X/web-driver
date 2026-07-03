/**
 * 老接口兼容 mock，对应 `src/api/invitation.api.ts`。
 *
 * Generated API 已把邀请流程迁到 `/team/{id}/invitations/*`，但老 invitation.api.ts
 * 仍保留在 src/api 顶层。这里只补三条直连路由以满足 mock 覆盖检查，不再扩展。
 */
import { defineMock } from "vite-plugin-mock-dev-server";
import { conflict, forbidden, notFound, ok } from "./shared";
import { currentUserId } from "./data/user.state";
import {
  getMembers,
  invitations,
  members,
  refreshExpiredInvitations,
  toInvitationView,
} from "./data/team.state";
import { now } from "./shared";

export default defineMock([
  /** 列出我收到的邀请（按创建时间倒序） */
  {
    url: "/dev-api/api/v1/invitations",
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

  /** 接受邀请：校验通过后把自己加入团队 */
  {
    url: "/dev-api/api/v1/invitations/:id/accept",
    method: "POST",
    body: (req) => {
      refreshExpiredInvitations();
      const me = currentUserId(req);
      const inv = invitations.get(Number(req.params.id));
      if (!inv) return notFound("邀请不存在");
      if (inv.inviteeAccountId !== me) return forbidden("只能处理自己的邀请");
      if (inv.status !== "pending") return conflict("该邀请已处理或已失效");

      inv.status = "accepted";
      const list = getMembers(inv.teamId);
      if (!list.some((m) => m.userId === me)) {
        list.push({ userId: me, role: inv.role, joinTime: now() });
        members.set(inv.teamId, list);
      }
      return ok(null);
    },
  },

  /** 拒绝邀请 */
  {
    url: "/dev-api/api/v1/invitations/:id/reject",
    method: "POST",
    body: (req) => {
      refreshExpiredInvitations();
      const me = currentUserId(req);
      const inv = invitations.get(Number(req.params.id));
      if (!inv) return notFound("邀请不存在");
      if (inv.inviteeAccountId !== me) return forbidden("只能处理自己的邀请");
      if (inv.status !== "pending") return conflict("该邀请已处理或已失效");

      inv.status = "rejected";
      return ok(null);
    },
  },
]);
