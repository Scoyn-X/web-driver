/**
 * 团队角色、邀请状态在 el-tag 上的颜色与文案映射。
 * 入参为 string：兼容后端枚举扩展，未知值走 fallback；不依赖 @/enums/team.enum。
 */

type TagType = "primary" | "success" | "warning" | "danger" | "info";

export function roleColor(role: string): TagType {
  const key = (role || "").toLowerCase();
  switch (key) {
    case "owner":
      return "danger";
    case "admin":
      return "warning";
    case "editor":
      return "primary";
    case "viewer":
      return "info";
    default:
      return "info";
  }
}

export function roleLabel(role: string): string {
  const key = (role || "").toLowerCase();
  switch (key) {
    case "owner":
      return "拥有者";
    case "admin":
      return "管理员";
    case "editor":
      return "编辑者";
    case "viewer":
      return "只读者";
    default:
      return role;
  }
}

export function invitationStatusColor(status: string): TagType {
  const key = (status || "").toLowerCase();
  switch (key) {
    case "pending":
      return "primary";
    case "accepted":
      return "success";
    case "rejected":
      return "danger";
    case "expired":
      return "warning";
    case "revoked":
    case "team_dissolved":
    case "cancelled":
      return "info";
    default:
      return "info";
  }
}

export function invitationStatusLabel(status: string): string {
  const key = (status || "").toLowerCase();
  switch (key) {
    case "pending":
      return "待处理";
    case "accepted":
      return "已接受";
    case "rejected":
      return "已拒绝";
    case "expired":
      return "已过期";
    case "revoked":
      return "已撤销";
    case "team_dissolved":
      return "团队已解散";
    case "cancelled":
      return "已取消";
    default:
      return status;
  }
}
