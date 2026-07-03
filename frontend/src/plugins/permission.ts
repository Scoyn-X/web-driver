import NProgress from "@/utils/nprogress";
import router from "@/router";
import { useUserStore } from "@/store/user";

/** 免登录白名单 */
const WHITE_LIST = ["/login", "/404"];

function isWhiteListPath(path: string) {
  if (WHITE_LIST.includes(path)) return true;
  return path.startsWith("/s/");
}

/** 路由守卫 */
export function setupPermission() {
  router.beforeEach(async (to, _from, next) => {
    NProgress.start();

    const userStore = useUserStore();
    const token = userStore.token;

    if (token) {
      // 已登录：访问登录页时重定向到主页
      if (to.path === "/login") {
        next("/files");
      } else if (to.matched.length === 0) {
        next("/404");
      } else {
        next();
      }
    } else {
      // 未登录：白名单放行，其余跳转登录页
      if (isWhiteListPath(to.path)) {
        next();
      } else {
        next(`/login?redirect=${to.path}`);
      }
    }
  });

  router.afterEach(() => {
    NProgress.done();
  });
}
