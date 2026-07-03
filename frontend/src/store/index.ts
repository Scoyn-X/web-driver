import type { App } from "vue";

const store = createPinia();

export function setupStore(app: App<Element>) {
  app.use(store);
}

export { store };

// 导出所有 store
export { useUserStore } from "./user";
export { useMenuStore } from "./menu";
export { useSettingsStore } from "./settings";
export { useAppStore } from "./app";
export { useTagsViewStore } from "./tags-view";
export { useTeamStore } from "./team";
