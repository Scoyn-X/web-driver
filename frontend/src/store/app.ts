import { defaultSettings } from "@/settings";
import { DeviceEnum } from "@/enums/settings/device.enum";
import { SidebarStatus } from "@/enums/settings/layout.enum";

import zhCn from "element-plus/es/locale/lang/zh-cn";

export const useAppStore = defineStore("app", () => {
  const device = useStorage("device", DeviceEnum.DESKTOP);
  const size = useStorage("size", defaultSettings.size);

  const sidebarStatus = useStorage("sidebarStatus", SidebarStatus.OPENED);
  const sidebar = reactive({
    opened: sidebarStatus.value === SidebarStatus.OPENED,
    withoutAnimation: false,
  });
  const sidebarWidth = useStorage<number>("sidebarWidth", 200);

  const locale = computed(() => zhCn);

  function toggleSidebar() {
    sidebar.opened = !sidebar.opened;
    sidebarStatus.value = sidebar.opened ? SidebarStatus.OPENED : SidebarStatus.CLOSED;
  }

  function closeSideBar() {
    sidebar.opened = false;
    sidebarStatus.value = SidebarStatus.CLOSED;
  }

  function openSideBar() {
    sidebar.opened = true;
    sidebarStatus.value = SidebarStatus.OPENED;
  }

  function toggleDevice(val: string) {
    device.value = val;
  }

  function changeSize(val: string) {
    size.value = val;
  }

  return {
    device,
    sidebar,
    sidebarWidth,
    locale,
    size,
    toggleDevice,
    changeSize,
    toggleSidebar,
    closeSideBar,
    openSideBar,
  };
});
