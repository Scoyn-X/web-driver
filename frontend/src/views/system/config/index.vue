<template>
  <section class="system-config-page">
    <header class="flex-x-between mb-5 flex-shrink-0 items-start gap-3">
      <div>
        <h2 class="m-0 text-xl font-semibold">系统配置</h2>
        <p class="mt-1.5 mb-0 text-[13px] text-[var(--el-text-color-secondary)]">
          维护回收站、私密空间宽限期与储存容量相关参数。
        </p>
      </div>
      <div class="flex-y-center gap-2">
        <el-button
          :loading="loading"
          @click="fetchConfig"
        >
          刷新
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          @click="onSave"
        >
          保存配置
        </el-button>
      </div>
    </header>

    <div
      v-loading="loading"
      class="system-config-content"
    >
      <header class="system-config-tabbar">
        <ImmersiveTab
          v-model="activeTab"
          :tabs="tabs"
        >
          <template #recycle>
            <el-icon class="mr-1"><Delete /></el-icon>
            回收站
          </template>
          <template #grace>
            <el-icon class="mr-1"><Timer /></el-icon>
            宽限期
          </template>
          <template #storage>
            <el-icon class="mr-1"><FolderOpened /></el-icon>
            储存容量
          </template>
        </ImmersiveTab>
      </header>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="180px"
        class="system-config-form"
      >
        <RecycleConfigTab
          v-show="activeTab === 'recycle'"
          v-model:trashRetentionSeconds="form.trashRetentionSeconds"
          v-model:cleanupIntervalSeconds="form.cleanupIntervalSeconds"
          :cleanup-loading="cleanupLoading"
          @trigger-cleanup="onTriggerCleanup"
        />

        <GracePeriodConfig
          v-show="activeTab === 'grace'"
          v-model="form.privateGracePeriodSeconds"
        />

        <StorageConfigTab
          v-show="activeTab === 'storage'"
          v-model:normal-total-quota="form.normalTotalQuota"
          v-model:normal-single-file-limit="form.normalSingleFileLimit"
          v-model:download-throttle-threshold="form.downloadThrottleThreshold"
          v-model:normal-download-bytes-per-second="form.normalDownloadBytesPerSecond"
        />
      </el-form>
    </div>
  </section>
</template>

<script setup lang="ts">
  import { Delete, FolderOpened, Timer } from "@element-plus/icons-vue";
  import type { FormInstance, FormRules } from "element-plus";
  import {
    SystemAPI,
    type SystemConfigResponseVO,
    type SystemConfigUpdateRequestVO,
  } from "@/api/system.api";
  import ImmersiveTab from "@/components/ImmersiveTab.vue";
  import GracePeriodConfig from "./components/GracePeriodTab.vue";
  import RecycleConfigTab from "./components/RecycleConfigTab.vue";
  import StorageConfigTab from "./components/StorageConfigTab.vue";

  defineOptions({
    name: "SystemConfigPage",
    inheritAttrs: false,
  });

  interface ConfigForm {
    trashRetentionSeconds: number;
    cleanupIntervalSeconds: number;
    privateGracePeriodSeconds: number;
    normalTotalQuota: number;
    normalSingleFileLimit: number;
    downloadThrottleThreshold: number;
    normalDownloadBytesPerSecond: number;
  }

  const tabs = [
    { name: "recycle", label: "回收站" },
    { name: "grace", label: "宽限期" },
    { name: "storage", label: "储存容量" },
  ];

  const formRef = ref<FormInstance>();
  const loading = ref(false);
  const saving = ref(false);
  const cleanupLoading = ref(false);
  const activeTab = ref("recycle");

  const form = reactive<ConfigForm>({
    trashRetentionSeconds: 30 * 24 * 60 * 60,
    cleanupIntervalSeconds: 60 * 60,
    privateGracePeriodSeconds: 7 * 24 * 60 * 60,
    normalTotalQuota: 10 * 1024 * 1024 * 1024,
    normalSingleFileLimit: 100 * 1024 * 1024,
    downloadThrottleThreshold: 100 * 1024 * 1024,
    normalDownloadBytesPerSecond: 5 * 1024 * 1024,
  });

  const rules: FormRules<ConfigForm> = {
    trashRetentionSeconds: [{ required: true, message: "请输入保留秒数", trigger: "blur" }],
    cleanupIntervalSeconds: [
      { required: true, message: "请输入清理任务间隔秒数", trigger: "blur" },
    ],
    privateGracePeriodSeconds: [{ required: true, message: "请输入宽限期", trigger: "blur" }],
    normalTotalQuota: [{ required: true, message: "请输入总配额", trigger: "blur" }],
    normalSingleFileLimit: [{ required: true, message: "请输入单文件限制", trigger: "blur" }],
    downloadThrottleThreshold: [{ required: true, message: "请输入限速阈值", trigger: "blur" }],
    normalDownloadBytesPerSecond: [{ required: true, message: "请输入下载限速", trigger: "blur" }],
  };

  function applyConfig(config: SystemConfigResponseVO) {
    form.trashRetentionSeconds = config.trashRetentionSeconds ?? form.trashRetentionSeconds;
    form.cleanupIntervalSeconds = config.cleanupIntervalSeconds ?? form.cleanupIntervalSeconds;
    form.privateGracePeriodSeconds =
      config.privateGracePeriodSeconds ?? form.privateGracePeriodSeconds;
    form.normalTotalQuota = config.normalTotalQuotaBytes ?? form.normalTotalQuota;
    form.normalSingleFileLimit = config.normalSingleFileLimitBytes ?? form.normalSingleFileLimit;
    form.downloadThrottleThreshold =
      config.downloadThrottleThresholdBytes ?? form.downloadThrottleThreshold;
    form.normalDownloadBytesPerSecond =
      config.normalDownloadBytesPerSecond ?? form.normalDownloadBytesPerSecond;
  }

  async function fetchConfig() {
    loading.value = true;
    try {
      const config = await SystemAPI.getConfig();
      applyConfig(config);
    } finally {
      loading.value = false;
    }
  }

  function toPayload(): SystemConfigUpdateRequestVO {
    return {
      trashRetentionSeconds: Number(form.trashRetentionSeconds),
      cleanupIntervalSeconds: Number(form.cleanupIntervalSeconds),
      privateGracePeriodSeconds: Number(form.privateGracePeriodSeconds),
      normalTotalQuota: Number(form.normalTotalQuota),
      normalSingleFileLimit: Number(form.normalSingleFileLimit),
      downloadThrottleThreshold: Number(form.downloadThrottleThreshold),
      normalDownloadBytesPerSecond: Number(form.normalDownloadBytesPerSecond),
    };
  }

  async function onSave() {
    const valid = await formRef.value?.validate().catch(() => false);
    if (!valid) return;

    saving.value = true;
    try {
      const updated = await SystemAPI.updateConfig(toPayload());
      applyConfig(updated);
      ElMessage.success("系统配置已保存");
    } finally {
      saving.value = false;
    }
  }

  async function onTriggerCleanup() {
    try {
      await ElMessageBox.confirm("确认立即触发一次回收站清理任务吗？", "触发清理", {
        type: "warning",
        confirmButtonText: "确认触发",
        cancelButtonText: "取消",
      });
    } catch (error: any) {
      if (error === "cancel" || error === "close") return;
      throw error;
    }

    cleanupLoading.value = true;
    try {
      await SystemAPI.triggerCleanup();
      ElMessage.success("已触发清理任务");
    } finally {
      cleanupLoading.value = false;
    }
  }

  onMounted(fetchConfig);
</script>

<style scoped>
  .system-config-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 20px 24px;
    overflow-y: auto;
  }

  .system-config-content {
    max-width: 920px;
  }

  .system-config-tabbar {
    display: flex;
    flex-shrink: 0;
    align-items: flex-end;
    padding: 0 12px;
    margin-bottom: 16px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .system-config-form :deep(.el-form-item__content) {
    gap: 12px;
  }
</style>
