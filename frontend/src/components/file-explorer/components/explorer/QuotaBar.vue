<template>
  <div class="relative w-full">
    <el-progress
      :show-text="false"
      :percentage="usagePercent"
      :stroke-width="strokeWidth"
      :color="progressColor"
    />
    <div class="quota-label">{{ quotaText }}</div>
  </div>
</template>

<script setup lang="ts">
  import QuotaAPI from "@/api/quota.api";
  import TeamAPI from "@/api/team.api";
  import { formatSize } from "@/utils/format-size";

  /**
   * 配额进度条：
   * - 传入 used + total 时进入受控模式，直接渲染外部传入的字节数；
   * - 否则调用个人配额接口 (`QuotaAPI.getQuotaInfo`) 自动获取。
   */
  const props = withDefaults(
    defineProps<{
      used?: number;
      total?: number;
      scope?: "personal" | "team";
      teamId?: number;
      strokeWidth?: number;
    }>(),
    { scope: "personal", strokeWidth: 20 }
  );

  const fetchedUsed = ref(0);
  const fetchedTotal = ref(0);
  const fetchedUsedText = ref("");
  const fetchedTotalText = ref("");

  const isControlled = computed(() => props.used !== undefined && props.total !== undefined);

  const usedBytes = computed(() => (isControlled.value ? (props.used ?? 0) : fetchedUsed.value));
  const totalBytes = computed(() => (isControlled.value ? (props.total ?? 0) : fetchedTotal.value));
  const unlimited = computed(() => totalBytes.value >= Number.MAX_SAFE_INTEGER);

  const usagePercent = computed(() => {
    if (unlimited.value) return 0;
    if (!totalBytes.value) return 0;
    return Math.min(100, Number(((usedBytes.value / totalBytes.value) * 100).toFixed(2)));
  });

  const progressColor = computed(() =>
    usagePercent.value > 90 ? "var(--el-color-danger)" : "var(--el-color-primary)"
  );

  const quotaText = computed(() => {
    const usedText = isControlled.value
      ? formatSize(usedBytes.value)
      : fetchedUsedText.value || formatSize(usedBytes.value);
    const totalText = unlimited.value
      ? "不限容量"
      : isControlled.value
        ? formatSize(totalBytes.value)
        : fetchedTotalText.value || formatSize(totalBytes.value);
    return `${usedText} / ${totalText}`;
  });

  async function fetchQuota() {
    if (isControlled.value) return;
    try {
      const res =
        props.scope === "team" && props.teamId
          ? await TeamAPI.getTeamQuota(props.teamId)
          : await QuotaAPI.getQuotaInfo();
      fetchedUsed.value = Number(res.usedSpace ?? 0);
      fetchedTotal.value = Number(res.totalQuota ?? 0);
      fetchedUsedText.value = res.usedSpaceFormatted ?? "";
      fetchedTotalText.value = res.totalQuotaFormatted ?? "";
    } catch {
      ElMessage.error("获取配额失败");
    }
  }

  onMounted(() => {
    if (!isControlled.value) fetchQuota();
  });

  watch([() => props.scope, () => props.teamId, () => props.used, () => props.total], () => {
    if (isControlled.value) return;
    if (props.scope === "team" && !props.teamId) return;
    fetchQuota();
  });

  defineExpose({
    fetchQuota,
  });
</script>

<style scoped>
  .quota-label {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: var(--el-text-color-primary);
    pointer-events: none;
  }
</style>
