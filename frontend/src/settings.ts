import { LayoutMode, ComponentSize, SidebarColor, ThemeMode } from "./enums";

const { pkg } = __APP_INFO__;

export const defaultSettings: AppSettings = {
  title: "网盘管理系统",
  version: pkg.version,
  showSettings: true,
  showTagsView: true,
  showAppLogo: true,
  layout: LayoutMode.LEFT,
  theme: ThemeMode.LIGHT,
  size: ComponentSize.DEFAULT,
  themeColor: "#409eff",
  showWatermark: false,
  watermarkContent: pkg.name,
  sidebarColorScheme: SidebarColor.MINIMAL_WHITE,
};

/** 主题色预设 */
export const themeColorPresets = [
  "#4080FF",
  "#1890FF",
  "#409EFF",
  "#FA8C16",
  "#722ED1",
  "#13C2C2",
  "#52C41A",
  "#F5222D",
  "#2F54EB",
  "#EB2F96",
];
