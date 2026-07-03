<template>
  <section class="config-section">
    <h3 class="section-title">容量与限速</h3>

    <el-form-item
      label="普通用户总配额（字节）"
      prop="normalTotalQuota"
    >
      <el-input-number
        v-model="normalTotalQuotaProxy"
        :min="1"
        :step="1024 * 1024"
        controls-position="right"
      />
      <span class="hint-text">当前约 {{ displaySize(normalTotalQuota) }}</span>
    </el-form-item>

    <el-form-item
      label="单文件大小限制（字节）"
      prop="normalSingleFileLimit"
    >
      <el-input-number
        v-model="normalSingleFileLimitProxy"
        :min="1"
        :step="1024 * 1024"
        controls-position="right"
      />
      <span class="hint-text">当前约 {{ displaySize(normalSingleFileLimit) }}</span>
    </el-form-item>

    <el-form-item
      label="下载限速触发阈值（字节）"
      prop="downloadThrottleThreshold"
    >
      <el-input-number
        v-model="downloadThrottleThresholdProxy"
        :min="1"
        :step="1024 * 1024"
        controls-position="right"
      />
      <span class="hint-text">当前约 {{ displaySize(downloadThrottleThreshold) }}</span>
    </el-form-item>

    <el-form-item
      label="普通用户下载限速（字节/秒）"
      prop="normalDownloadBytesPerSecond"
    >
      <el-input-number
        v-model="normalDownloadBytesPerSecondProxy"
        :min="1"
        :step="1024"
        controls-position="right"
      />
      <span class="hint-text">当前约 {{ displaySpeed(normalDownloadBytesPerSecond) }}</span>
    </el-form-item>
  </section>
</template>

<script setup lang="ts">
  import { formatSize } from "@/utils/format-size";

  defineOptions({
    name: "StorageConfigTab",
    inheritAttrs: false,
  });

  const props = defineProps<{
    normalTotalQuota: number;
    normalSingleFileLimit: number;
    downloadThrottleThreshold: number;
    normalDownloadBytesPerSecond: number;
  }>();

  const emit = defineEmits<{
    (e: "update:normalTotalQuota", value: number): void;
    (e: "update:normalSingleFileLimit", value: number): void;
    (e: "update:downloadThrottleThreshold", value: number): void;
    (e: "update:normalDownloadBytesPerSecond", value: number): void;
  }>();

  const normalTotalQuotaProxy = computed({
    get: () => props.normalTotalQuota,
    set: (value: number) => emit("update:normalTotalQuota", value),
  });

  const normalSingleFileLimitProxy = computed({
    get: () => props.normalSingleFileLimit,
    set: (value: number) => emit("update:normalSingleFileLimit", value),
  });

  const downloadThrottleThresholdProxy = computed({
    get: () => props.downloadThrottleThreshold,
    set: (value: number) => emit("update:downloadThrottleThreshold", value),
  });

  const normalDownloadBytesPerSecondProxy = computed({
    get: () => props.normalDownloadBytesPerSecond,
    set: (value: number) => emit("update:normalDownloadBytesPerSecond", value),
  });

  function displaySize(value: number) {
    if (!value || value <= 0) return "-";
    return formatSize(value);
  }

  function displaySpeed(value: number) {
    if (!value || value <= 0) return "-";
    return `${formatSize(value)}/s`;
  }
</script>

<style scoped>
  .config-section {
    padding: 4px 0 0;
  }

  .section-title {
    margin: 0 0 16px;
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .hint-text {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
</style>
