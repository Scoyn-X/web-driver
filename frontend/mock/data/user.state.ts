/**
 * 用户目录的 Mock 状态，被 auth / team 等模块共享。
 *
 * dev server 重启即重置。所有种子用户使用统一的默认密码，便于本地联调。
 */
import { createSequence } from "../shared";

export interface MockUser {
  userId: number;
  accountName: string;
  password: string;
  nickname: string;
  email: string;
  accountType: string;
  vipState: "NORMAL" | "VIP";
}

/** 未携带有效 token 时回退到的演示用户 */
export const DEMO_USER_ID = 1;

/** 所有种子用户的默认密码 */
export const DEFAULT_PASSWORD = "123456";

const seedUsers: MockUser[] = [
  { userId: 1, accountName: "demo", nickname: "Demo", email: "demo@example.com" },
  { userId: 2, accountName: "alice", nickname: "Alice", email: "alice@example.com" },
  { userId: 3, accountName: "bob", nickname: "Bob", email: "bob@example.com" },
  { userId: 4, accountName: "carol", nickname: "Carol", email: "carol@example.com" },
  { userId: 5, accountName: "dave", nickname: "Dave", email: "dave@example.com" },
].map((u) => ({ ...u, password: DEFAULT_PASSWORD, accountType: "personal", vipState: "NORMAL" }));

export const users = new Map<number, MockUser>(seedUsers.map((u) => [u.userId, u]));

const userSequence = createSequence(Math.max(...Array.from(users.keys())));

/** 新增一个用户并返回其完整记录 */
export function registerUser(
  input: Omit<MockUser, "userId" | "vipState"> & { vipState?: "NORMAL" | "VIP" }
): MockUser {
  const userId = userSequence.next();
  const user: MockUser = { vipState: "NORMAL", ...input, userId };
  users.set(userId, user);
  return user;
}

/** 按账号名精确查找用户 */
export function findUserByAccount(accountName: string): MockUser | undefined {
  return Array.from(users.values()).find((u) => u.accountName === accountName);
}

/** 关键字搜索用户：匹配账号 / 昵称 / 邮箱，排除指定用户自身 */
export function searchUsers(keyword: string, excludeUserId: number): MockUser[] {
  const kw = keyword.trim().toLowerCase();
  if (!kw) return [];
  return Array.from(users.values()).filter(
    (u) =>
      u.userId !== excludeUserId &&
      [u.accountName, u.nickname, u.email].some((field) => field.toLowerCase().includes(kw))
  );
}

/** 用户展示名：优先昵称，回退账号名 */
export function displayName(userId: number): string {
  const user = users.get(userId);
  return user?.nickname || user?.accountName || "未知用户";
}

/** 切换用户 VIP 状态，返回新值；用户不存在返回 null */
export function setVipState(userId: number, vip: boolean): "NORMAL" | "VIP" | null {
  const user = users.get(userId);
  if (!user) return null;
  user.vipState = vip ? "VIP" : "NORMAL";
  return user.vipState;
}

/**
 * 从请求头解析当前登录用户。
 * 登录接口下发的 token 形如 `mock-token-<userId>`，请求拦截器以
 * `Authorization: Bearer mock-token-<userId>` 携带；解析失败时回退演示用户。
 */
export function currentUserId(req: {
  headers?: Record<string, string | string[] | undefined>;
}): number {
  const raw = req.headers?.authorization ?? req.headers?.Authorization;
  const matched = String(raw ?? "").match(/mock-token-(\d+)/);
  if (matched) {
    const id = Number(matched[1]);
    if (users.has(id)) return id;
  }
  return DEMO_USER_ID;
}
