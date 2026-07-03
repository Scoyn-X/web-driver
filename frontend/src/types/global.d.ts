declare global {
  /**
   * 响应数据
   */
  interface ApiResponse<T = any> {
    code: string;
    data: T;
    msg: string;
  }

  /**
   * 应用设置
   */
  interface AppSettings {
    title: string;
    version: string;
    showSettings: boolean;
    showTagsView: boolean;
    showAppLogo: boolean;
    layout: "left" | "top" | "mix";
    themeColor: string;
    theme: import("@/enums/settings/theme.enum").ThemeMode;
    size: string;
    showWatermark: boolean;
    watermarkContent: string;
    sidebarColorScheme: "classic-blue" | "minimal-white";
  }

  /**
   * 标签视图
   */
  interface TagView {
    name: string;
    title: string;
    path: string;
    fullPath: string;
    affix?: boolean;
    keepAlive?: boolean;
    query?: any;
  }
}
export {};
