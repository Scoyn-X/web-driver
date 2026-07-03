import { defineStore } from "pinia";

export interface MenuItem {
  path: string;
  title: string;
  icon: string;
}

export const defaultMenus: MenuItem[] = [
  { path: "/files", title: "文件管理", icon: "Folder" },
  { path: "/teams", title: "我的团队", icon: "UserFilled" },
  { path: "/invitations", title: "我的邀请", icon: "Message" },
  { path: "/profile", title: "个人中心", icon: "User" },
  { path: "/private", title: "私密空间", icon: "Lock" },
  { path: "/system-config", title: "系统配置", icon: "Setting" },
  { path: "/counter", title: "访问计数", icon: "DataLine" },
];

export const useMenuStore = defineStore("menu", {
  state: () => ({
    menus: defaultMenus as MenuItem[],
  }),
  actions: {
    setMenus(menus: MenuItem[]) {
      this.menus = menus;
    },
  },
});
