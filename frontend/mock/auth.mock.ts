/**
 * 认证相关 mock，对应 `src/api/auth.api.ts`。
 *
 * 登录成功下发的 token 形如 `mock-token-<userId>`，后续请求据此识别当前用户
 * （见 `data/user.state.ts` 的 currentUserId）。
 */
import { defineMock } from "vite-plugin-mock-dev-server";
import { badRequest, conflict, fail, ok } from "./shared";
import { findUserByAccount, registerUser } from "./data/user.state";

/** 简单邮箱格式校验 */
const isEmail = (value: string) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);

export default defineMock([
  /** 用户登录 */
  {
    url: "/dev-api/api/v1/auth/login",
    method: "POST",
    body: (req) => {
      const { accountName, password } = req.body || {};
      if (!accountName || !password) {
        return badRequest("账号和密码不能为空");
      }
      const user = findUserByAccount(String(accountName));
      if (!user) {
        return fail("用户不存在", "A0100");
      }
      if (user.password !== String(password)) {
        return fail("账号或密码错误", "A0101");
      }
      return ok({
        token: `mock-token-${user.userId}`,
        userId: user.userId,
        accountId: user.userId,
        nickname: user.nickname,
        accountName: user.accountName,
      });
    },
  },

  /** 用户注册 */
  {
    url: "/dev-api/api/v1/auth/register",
    method: "POST",
    body: (req) => {
      const { nickname, accountName, password, email, accountType } = req.body || {};
      if (!nickname || !accountName || !password || !email) {
        return badRequest("注册信息不完整");
      }
      if (String(password).length < 6) {
        return badRequest("密码长度不能少于 6 位");
      }
      if (!isEmail(String(email))) {
        return badRequest("邮箱格式不正确");
      }
      if (findUserByAccount(String(accountName))) {
        return conflict("账号已存在");
      }
      registerUser({
        accountName: String(accountName),
        password: String(password),
        nickname: String(nickname),
        email: String(email),
        accountType: String(accountType || "personal"),
      });
      return ok(null);
    },
  },
]);
