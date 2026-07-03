import { defaultSettings } from "@/settings";
import { SidebarColor, ThemeMode } from "@/enums/settings/theme.enum";
import type { LayoutMode } from "@/enums/settings/layout.enum";
import { applyTheme, generateThemeColors, toggleDarkMode, toggleSidebarColor } from "@/utils/theme";
import { SETTINGS_KEYS } from "@/constants";

export const useSettingsStore = defineStore("setting", () => {
  // 非持久化
  const settingsVisible = ref(false);

  // 持久化设置
  const showTagsView = useStorage<boolean>(
    SETTINGS_KEYS.SHOW_TAGS_VIEW,
    defaultSettings.showTagsView
  );
  const showAppLogo = useStorage<boolean>(SETTINGS_KEYS.SHOW_APP_LOGO, defaultSettings.showAppLogo);
  const showWatermark = useStorage<boolean>(
    SETTINGS_KEYS.SHOW_WATERMARK,
    defaultSettings.showWatermark
  );
  const sidebarColorScheme = useStorage<string>(
    SETTINGS_KEYS.SIDEBAR_COLOR_SCHEME,
    defaultSettings.sidebarColorScheme
  );
  const layout = useStorage<LayoutMode>(SETTINGS_KEYS.LAYOUT, defaultSettings.layout as LayoutMode);
  const themeColor = useStorage<string>(SETTINGS_KEYS.THEME_COLOR, defaultSettings.themeColor);
  const theme = useStorage<ThemeMode>(SETTINGS_KEYS.THEME, defaultSettings.theme);

  // 监听主题变化
  watch(
    [theme, themeColor],
    ([newTheme, newThemeColor]) => {
      toggleDarkMode(newTheme === ThemeMode.DARK);
      const colors = generateThemeColors(newThemeColor, newTheme);
      applyTheme(colors);
    },
    { immediate: true }
  );

  // 监听侧边栏配色方案变化
  watch(
    [sidebarColorScheme],
    ([newScheme]) => {
      toggleSidebarColor(newScheme === SidebarColor.CLASSIC_BLUE);
    },
    { immediate: true }
  );

  function updateTheme(newTheme: ThemeMode) {
    theme.value = newTheme;
  }

  function updateThemeColor(newColor: string) {
    themeColor.value = newColor;
  }

  function updateSidebarColorScheme(newScheme: string) {
    sidebarColorScheme.value = newScheme;
  }

  function updateLayout(newLayout: LayoutMode) {
    layout.value = newLayout;
  }

  function resetSettings() {
    showTagsView.value = defaultSettings.showTagsView;
    showAppLogo.value = defaultSettings.showAppLogo;
    showWatermark.value = defaultSettings.showWatermark;
    sidebarColorScheme.value = defaultSettings.sidebarColorScheme;
    layout.value = defaultSettings.layout as LayoutMode;
    themeColor.value = defaultSettings.themeColor;
    theme.value = defaultSettings.theme;
  }

  return {
    settingsVisible,
    showTagsView,
    showAppLogo,
    showWatermark,
    sidebarColorScheme,
    layout,
    themeColor,
    theme,

    updateTheme,
    updateThemeColor,
    updateSidebarColorScheme,
    updateLayout,
    resetSettings,
  };
});
