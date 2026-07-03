import { defineMock } from "vite-plugin-mock-dev-server";
import { badRequest, formatSize, notFound, ok } from "./shared";
import { currentUserId, searchUsers, setVipState, users } from "./data/user.state";
import { quotaInfo, TOTAL_QUOTA } from "./data/file.state";
import { invitations, refreshExpiredInvitations, toInvitationView } from "./data/team.state";

const VIP_TOTAL_QUOTA = 100 * 1024 ** 3;

export default defineMock([
  /** 当前登录用户 */
  {
    url: "/dev-api/api/v1/users/me",
    method: "GET",
    body: (req) => {
      const me = currentUserId(req);
      const user = users.get(me);
      if (!user) return notFound("用户不存在");
      return ok({
        userId: user.userId,
        accountName: user.accountName,
        nickname: user.nickname,
        email: user.email,
        vipState: user.vipState,
        personalQuota: quotaInfo(),
        privateSpaceReminder: "",
      });
    },
  },

  /** 切换 VIP 状态 */
  {
    url: "/dev-api/api/v1/users/:id/vip",
    method: "PUT",
    body: (req) => {
      const id = Number(req.params.id);
      const { vip } = (req.body || {}) as { vip?: boolean };
      if (typeof vip !== "boolean") return badRequest("vip 字段必须为布尔值");
      const newState = setVipState(id, vip);
      if (!newState) return notFound("用户不存在");
      const isVip = newState === "VIP";
      const totalQuota = isVip ? VIP_TOTAL_QUOTA : TOTAL_QUOTA;
      return ok({
        userId: id,
        vipState: newState,
        personalQuotaLimit: isVip ? null : totalQuota,
        teamQuotaLimit: isVip ? null : 5 * 1024 ** 3,
        downloadLimited: !isVip,
        singleFileLimit: isVip ? null : 100 * 1024 * 1024,
        totalQuotaFormatted: formatSize(totalQuota),
      });
    },
  },

  /** 我收到的团队邀请（支持 status 过滤） */
  {
    url: "/dev-api/api/v1/users/me/team-invitations",
    method: "GET",
    body: (req) => {
      refreshExpiredInvitations();
      const me = currentUserId(req);
      const filter = req.query.status ? String(req.query.status) : null;
      const list = Array.from(invitations.values())
        .filter((inv) => inv.inviteeAccountId === me)
        .sort((a, b) => (a.createTime < b.createTime ? 1 : -1))
        .map(toInvitationView)
        .filter((view) => !filter || view.status === filter);
      return ok(list);
    },
  },

  /** 用户搜索（账号 / 昵称 / 邮箱模糊匹配，排除自己） */
  {
    url: "/dev-api/api/v1/users/search",
    method: "GET",
    body: (req) => {
      const me = currentUserId(req);
      const keyword = String(req.query.keyword || "");
      const result = searchUsers(keyword, me).map((u) => ({
        userId: u.userId,
        accountName: u.accountName,
        nickname: u.nickname,
        email: u.email,
      }));
      return ok(result);
    },
  },
]);
