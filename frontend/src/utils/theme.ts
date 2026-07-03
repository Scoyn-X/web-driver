import { ThemeMode } from "@/enums";

function hexToRgb(hex: string): [number, number, number] {
  const bigint = parseInt(hex.slice(1), 16);
  return [(bigint >> 16) & 255, (bigint >> 8) & 255, bigint & 255];
}

function rgbToHex(r: number, g: number, b: number): string {
  return `#${((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1)}`;
}

/** 加深颜色 */
export function getDarkColor(color: string, level: number): string {
  const rgb = hexToRgb(color);
  for (let i = 0; i < 3; i++) rgb[i] = Math.round(20.5 * level + rgb[i] * (1 - level));
  return rgbToHex(rgb[0], rgb[1], rgb[2]);
}

/** 变浅颜色 */
export function getLightColor(color: string, level: number): string {
  const rgb = hexToRgb(color);
  for (let i = 0; i < 3; i++) rgb[i] = Math.round(255 * level + rgb[i] * (1 - level));
  return rgbToHex(rgb[0], rgb[1], rgb[2]);
}

/** 生成主题色变体 */
export function generateThemeColors(primary: string, theme: ThemeMode) {
  const isDarkTheme = theme === ThemeMode.DARK;
  const colors: Record<string, string> = { primary };

  for (let i = 1; i <= 9; i++) {
    colors[`primary-light-${i}`] = isDarkTheme
      ? getDarkColor(primary, i / 10)
      : getLightColor(primary, i / 10);
  }

  colors["primary-dark-2"] = isDarkTheme ? getDarkColor(primary, 0.3) : getLightColor(primary, 0.2);

  return colors;
}

/** 应用主题色到 CSS 变量 */
export function applyTheme(colors: Record<string, string>) {
  const el = document.documentElement;
  Object.entries(colors).forEach(([key, value]) => {
    el.style.setProperty(`--el-color-${key}`, value);
  });
}

/** 切换暗黑模式 */
export function toggleDarkMode(isDark: boolean) {
  if (isDark) {
    document.documentElement.classList.add(ThemeMode.DARK);
  } else {
    document.documentElement.classList.remove(ThemeMode.DARK);
  }
}

/** 切换侧边栏配色 */
export function toggleSidebarColor(isBlueSidebar: boolean) {
  if (isBlueSidebar) {
    document.documentElement.classList.add("sidebar-color-blue");
  } else {
    document.documentElement.classList.remove("sidebar-color-blue");
  }
}
