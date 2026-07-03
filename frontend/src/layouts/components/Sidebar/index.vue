<template>
  <div
    class="sidebar"
    :class="{ 'is-collapsed': !isOpen, 'is-dragging': isDragging }"
    :style="{ width: sidebarPx + 'px' }"
  >
    <el-menu
      :default-active="activeMenuPath"
      router
      class="sidebar-menu"
      :collapse="!isOpen"
      :collapse-transition="false"
    >
      <el-menu-item
        v-for="item in menuStore.menus"
        :key="item.path"
        :index="item.path"
      >
        <el-icon><component :is="item.icon" /></el-icon>
        <template #title>{{ item.title }}</template>
      </el-menu-item>
    </el-menu>

    <div class="sidebar-footer">
      <button
        type="button"
        class="collapse-btn"
        :title="isOpen ? '收起菜单' : '展开菜单'"
        @click="appStore.toggleSidebar()"
      >
        <el-icon :size="18">
          <Fold v-if="isOpen" />
          <Expand v-else />
        </el-icon>
      </button>
    </div>

    <ResizeHandle
      v-if="isOpen"
      v-model:width="appStore.sidebarWidth"
      v-model:dragging="isDragging"
      :min="160"
      :max="360"
    />
  </div>
</template>

<script setup lang="ts">
  import { useMenuStore } from "@/store/menu";
  import { useAppStore } from "@/store/app";
  import ResizeHandle from "../ResizeHandle.vue";

  const route = useRoute();
  const menuStore = useMenuStore();
  const appStore = useAppStore();

  const activeMenuPath = computed(() => {
    const meta = route.meta as { activeMenu?: string };
    return meta?.activeMenu || route.path;
  });
  const isOpen = computed(() => appStore.sidebar.opened);
  const isDragging = ref(false);
  const sidebarPx = computed(() => (isOpen.value ? appStore.sidebarWidth : 54));
</script>

<style scoped>
  .sidebar {
    position: relative;
    display: flex;
    flex-shrink: 0;
    flex-direction: column;
    overflow: hidden;
    background-color: var(--el-fill-color-blank);
    border-right: 1px solid var(--el-border-color-light);
    transition: width 0.25s;
  }

  .sidebar.is-dragging {
    transition: none;
  }

  .sidebar-menu {
    flex: 1;
    overflow-x: hidden;
    overflow-y: auto;
    border-right: none !important;
  }

  .sidebar-menu:not(.el-menu--collapse) {
    width: 100%;
  }

  .sidebar-footer {
    display: flex;
    flex-shrink: 0;
    justify-content: flex-end;
    padding: 8px;
  }

  .sidebar.is-collapsed .sidebar-footer {
    justify-content: center;
    padding: 8px 0;
  }

  .collapse-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    padding: 0;
    color: var(--el-text-color-regular);
    cursor: pointer;
    background: transparent;
    border: none;
    border-radius: 6px;
    transition: background-color 0.2s;
  }

  .collapse-btn:hover {
    color: var(--el-color-primary);
    background: var(--el-fill-color-light);
  }
</style>
