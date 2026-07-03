<template>
  <div class="explorer-path">
    <div class="flex shrink-0 items-center">
      <el-tooltip
        content="后退"
        size="small"
      >
        <el-button
          text
          class="w-7 h-7 p-0 mr-1"
          icon="ArrowLeft"
          :disabled="!canGoBack"
          @click="emit('goBack')"
        />
      </el-tooltip>
      <el-tooltip content="前进">
        <el-button
          text
          class="w-7 h-7 p-0 mr-1"
          icon="ArrowRight"
          :disabled="!canGoForward"
          @click="emit('goForward')"
        />
      </el-tooltip>
    </div>

    <div class="ml-2 overflow-hidden">
      <el-breadcrumb class="explorer-breadcrumb">
        <el-breadcrumb-item>
          <el-link
            :underline="false"
            class="inline-block max-w-[180px] overflow-hidden whitespace-nowrap align-bottom"
            @click="emit('navigateDirectory', 0)"
          >
            {{ rootLabel }}
          </el-link>
        </el-breadcrumb-item>
        <el-breadcrumb-item
          v-for="item in activeDirectoryTrail"
          :key="item.id"
        >
          <el-link
            :underline="false"
            class="inline-block max-w-[180px] overflow-hidden whitespace-nowrap align-bottom"
            @click="emit('navigateDirectory', item.id!)"
          >
            {{ item.name }}
          </el-link>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="flex shrink-0 gap-5 items-center ml-auto">
      <span>已选择 {{ selectedCount }} 项</span>
      <el-input
        :model-value="keyword"
        class="ex-search"
        clearable
        placeholder="搜索文件..."
        @keyup.enter="emit('search')"
        @clear="emit('search')"
        @update:model-value="(v) => emit('update:keyword', v)"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </div>
  </div>

  <div class="explorer-toolbar">
    <el-button
      v-if="!readonly"
      text
      icon="Upload"
      @click="emit('upload')"
    >
      上传文件
    </el-button>
    <el-button
      v-if="!readonly"
      text
      icon="FolderAdd"
      @click="emit('createDir')"
    >
      新建文件夹
    </el-button>
    <el-button
      text
      icon="Refresh"
      @click="emit('refresh')"
    >
      刷新
    </el-button>

    <SwitchGroup
      class="ml-2"
      :model-value="viewMode"
      :options="viewOptions"
      @update:model-value="(v: ViewMode) => emit('update:viewMode', v)"
    >
      <template #list>
        <el-tooltip content="列表">
          <el-icon><Menu /></el-icon>
        </el-tooltip>
      </template>
      <template #grid>
        <el-tooltip content="图标">
          <el-icon><Grid /></el-icon>
        </el-tooltip>
      </template>
    </SwitchGroup>

    <div class="flex items-center gap-2 min-w-[220px] max-w-[300px] ml-auto">
      <span class="text-[13px] whitespace-nowrap">储存空间</span>
      <QuotaCard
        ref="quotaRef"
        :scope="scope"
        :team-id="teamId"
        :used="quotaUsed"
        :total="quotaTotal"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
  import type { BreadcrumbItemResponseVO } from "@/api/file.api";
  import type { ViewMode } from "../../types";
  import type { SwitchOption } from "@/components/SwitchGroup.vue";
  import QuotaCard from "./QuotaBar.vue";

  const props = withDefaults(
    defineProps<{
      canGoBack: boolean;
      canGoForward: boolean;
      activeDirectoryTrail: BreadcrumbItemResponseVO[];
      selectedCount: number;
      keyword: string;
      viewMode: ViewMode;
      refreshTrigger: number;
      scope?: "personal" | "team";
      rootLabel?: string;
      quotaUsed?: number;
      quotaTotal?: number;
      teamId?: number;
      readonly?: boolean;
    }>(),
    { rootLabel: "我的网盘" }
  );

  const emit = defineEmits<{
    goBack: [];
    goForward: [];
    navigateDirectory: [id: number];
    search: [];
    "update:keyword": [v: string];
    upload: [];
    createDir: [];
    transferToTeam: [];
    refresh: [];
    "update:viewMode": [v: ViewMode];
  }>();

  /** 视图切换选项：列表 / 图标 */
  const viewOptions: SwitchOption<ViewMode>[] = [{ value: "list" }, { value: "grid" }];

  const quotaRef = ref<InstanceType<typeof QuotaCard> | null>(null);

  watch(
    () => props.refreshTrigger,
    () => {
      quotaRef.value?.fetchQuota();
    }
  );
</script>

<style scoped>
  .explorer-path {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    padding: 8px 12px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
    background: var(--el-fill-color-blank);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .explorer-toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    padding: 6px 10px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  /* 沉浸式搜索框：去边框、去投影，仅以浅色填充区分 */
  .ex-search {
    width: 260px;
  }

  .ex-search :deep(.el-input__wrapper) {
    background-color: var(--el-fill-color);
    border-radius: 6px;
    box-shadow: none;
  }

  .ex-search :deep(.el-input__wrapper:hover),
  .ex-search :deep(.el-input__wrapper.is-focus) {
    background-color: var(--el-fill-color);
    box-shadow: none;
  }
</style>

<style scoped>
  .explorer-breadcrumb :deep(.el-breadcrumb__item) {
    max-width: 180px;
    vertical-align: bottom;
  }

  .explorer-toolbar :deep(.el-button) {
    padding: 6px 10px;
    margin: 0;
    color: var(--el-text-color-regular);
    background: transparent;
    border: none;
    border-radius: 4px;
  }
</style>
