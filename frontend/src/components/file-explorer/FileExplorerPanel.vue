<!-- 文件管理面板：沉浸式标签页外壳，承载「我的文件 / 分享管理 / 回收站」三个面板 -->
<template>
  <section class="file-manager">
    <header class="file-manager__tabbar">
      <ImmersiveTabs
        v-model="activeTab"
        :tabs="tabs"
        @change="onTabChange"
      >
        <template #files>
          <el-icon class="mr-1"><Folder /></el-icon>
          我的文件
        </template>
        <template #share>
          <el-icon class="mr-1"><Share /></el-icon>
          分享管理
        </template>
        <template #recycle>
          <el-icon class="mr-1"><Delete /></el-icon>
          回收站
        </template>
      </ImmersiveTabs>
    </header>

    <FileExplorer
      v-show="activeTab === 'files'"
      ref="explorerRef"
    />
    <SharePanel
      v-show="activeTab === 'share'"
      ref="shareRef"
      @changed="explorerRef?.refresh()"
    />
    <RecycleBinPanel
      v-show="activeTab === 'recycle'"
      ref="recycleRef"
      @changed="explorerRef?.refresh()"
    />
  </section>
</template>

<script setup lang="ts">
  import ImmersiveTabs from "@/components/ImmersiveTab.vue";
  import type { FileManagerTab } from "./types";
  import FileExplorer from "./components/explorer/FileExplorer.vue";
  import SharePanel from "./components/panels/SharePanel.vue";
  import RecycleBinPanel from "./components/panels/RecycleBinPanel.vue";

  const tabs: { name: FileManagerTab }[] = [
    { name: "files" },
    { name: "share" },
    { name: "recycle" },
  ];

  const activeTab = ref("files");

  const explorerRef = ref<InstanceType<typeof FileExplorer> | null>(null);
  const shareRef = ref<InstanceType<typeof SharePanel> | null>(null);
  const recycleRef = ref<InstanceType<typeof RecycleBinPanel> | null>(null);

  function onTabChange(name: string) {
    if (name === "share") {
      shareRef.value?.fetchShares();
      return;
    }
    if (name === "recycle") {
      recycleRef.value?.fetchRecycle();
      return;
    }
    explorerRef.value?.activate();
  }
</script>

<style scoped>
  .file-manager {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
    background: var(--el-fill-color-blank);
  }

  .file-manager__tabbar {
    display: flex;
    flex-shrink: 0;
    align-items: flex-end;
    padding: 6px 12px 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  /* —— 文件管理内表格的沉浸式重置（原全局 file-manager.scss，改为组件局部样式）—— */
  .file-manager :deep(.el-table__inner-wrapper)::before {
    display: none;
  }

  .file-manager :deep(.el-table__header-wrapper th),
  .file-manager :deep(.el-table__header tr),
  .file-manager :deep(.el-table__row > td) {
    border-bottom: none;
  }

  /* 文件名链接：统一使用常规文本色 */
  .file-manager :deep(.file-link) {
    font-weight: 500;
    color: var(--el-text-color-regular);
  }

  .file-manager :deep(.file-link--dir) {
    cursor: pointer;
  }

  .file-manager :deep(.file-link--dir:hover) {
    text-decoration: none;
  }
</style>
