import type { App } from "vue";
import { createRouter, createWebHashHistory, type RouteRecordRaw } from "vue-router";
import { defaultMenus } from "@/store/menu";

const menuRouteComponents: Record<string, () => Promise<unknown>> = {
  "/files": () => import("@/views/system/file/index.vue"),
  "/teams": () => import("@/views/system/team/index.vue"),
  "/invitations": () => import("@/views/system/invitation/index.vue"),
  "/profile": () => import("@/views/system/profile/index.vue"),
  "/private": () => import("@/views/system/private/index.vue"),
  "/system-config": () => import("@/views/system/config/index.vue"),
  "/counter": () => import("@/views/counter/index.vue"),
};

function buildMenuRoutes(): RouteRecordRaw[] {
  return defaultMenus
    .filter((menu) => !!menuRouteComponents[menu.path])
    .map((menu) => ({
      path: menu.path.replace(/^\//, ""),
      name: menu.path.replace(/^\//, "").replace(/\/(.)/g, (_, c) => c.toUpperCase()),
      component: menuRouteComponents[menu.path],
      meta: { title: menu.title },
    }));
}

/**
 * 静态路由
 * */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: "/s/:shareToken",
    name: "ShareAccess",
    component: () => import("@/views/share/index.vue"),
    meta: { title: "分享文件" },
  },
  {
    path: "/",
    component: () => import("@/layouts/index.vue"),
    redirect: defaultMenus[0]?.path || "/login",
    children: [
      ...buildMenuRoutes(),
      {
        path: "teams/:teamId",
        name: "TeamDetail",
        component: () => import("@/views/system/team/detail/index.vue"),
        meta: { title: "团队详情", activeMenu: "/teams" },
      },
    ],
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/login/index.vue"),
    meta: { title: "登录" },
  },
  {
    path: "/404",
    name: "NotFound",
    component: () => import("@/views/error/404.vue"),
    meta: { title: "404" },
  },
];

/**
 * 创建 router
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: constantRoutes,
  // 刷新时，滚动条位置还原
  scrollBehavior: () => ({ left: 0, top: 0 }),
});

// 全局注册 router
export function setupRouter(app: App<Element>) {
  app.use(router);
}

export default router;
