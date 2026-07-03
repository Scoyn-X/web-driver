import { useTeamStore } from "./team";

interface LoginResult {
  token: string;
  userId: number;
  accountName: string;
  nickname: string;
}

interface UserState {
  token: string | null;
  userId: number | null;
  accountName: string;
  nickname: string;
}

export const useUserStore = defineStore("user", {
  state: (): UserState => ({
    token: localStorage.getItem("token"),
    userId: localStorage.getItem("userId") ? Number(localStorage.getItem("userId")) : null,
    accountName: localStorage.getItem("accountName") || "",
    nickname: localStorage.getItem("nickname") || "",
  }),
  getters: {
    /** 是否已登录 */
    isLoggedIn: (state) => !!state.token,
    /** 显示名称：优先昵称，其次账户名 */
    displayName: (state) => state.nickname || state.accountName || "用户",
  },
  actions: {
    setUserInfo(res: LoginResult) {
      this.token = res.token;
      this.userId = res.userId;
      this.accountName = res.accountName;
      this.nickname = res.nickname;
      localStorage.setItem("token", res.token);
      localStorage.setItem("userId", String(res.userId));
      localStorage.setItem("accountName", res.accountName);
      localStorage.setItem("nickname", res.nickname);
    },
    clearUserInfo() {
      useTeamStore().reset();
      this.token = null;
      this.userId = null;
      this.accountName = "";
      this.nickname = "";
      localStorage.removeItem("token");
      localStorage.removeItem("userId");
      localStorage.removeItem("accountName");
      localStorage.removeItem("nickname");
    },
  },
});
