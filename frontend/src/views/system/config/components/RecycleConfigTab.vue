<template>
  <section class="config-section">
    <h3 class="section-title">回收站策略</h3>

    <el-form-item
      label="回收站保留秒数"
      prop="trashRetentionSeconds"
    >
      <div class="duration-input-group">
        <el-input-number
          v-model="trashRetentionDuration.value"
          :min="1"
          :step="1"
          controls-position="right"
          @update:model-value="
            (value) => onDurationValueChange('trashRetentionSeconds', trashRetentionDuration, value)
          "
        />
        <el-select
          v-model="trashRetentionDuration.unit"
          class="duration-unit-select"
          @change="
            (unit) => onDurationUnitChange('trashRetentionSeconds', trashRetentionDuration, unit)
          "
        >
          <el-option
            v-for="option in durationUnitOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
      <span class="hint-text">{{ durationHint(trashRetentionSeconds) }}</span>
    </el-form-item>

    <el-form-item
      label="清理任务间隔（秒）"
      prop="cleanupIntervalSeconds"
    >
      <div class="duration-input-group">
        <el-input-number
          v-model="cleanupIntervalDuration.value"
          :min="1"
          :step="1"
          controls-position="right"
          @update:model-value="
            (value) =>
              onDurationValueChange('cleanupIntervalSeconds', cleanupIntervalDuration, value)
          "
        />
        <el-select
          v-model="cleanupIntervalDuration.unit"
          class="duration-unit-select"
          @change="
            (unit) => onDurationUnitChange('cleanupIntervalSeconds', cleanupIntervalDuration, unit)
          "
        >
          <el-option
            v-for="option in durationUnitOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
      <span class="hint-text">{{ durationHint(cleanupIntervalSeconds) }}</span>
    </el-form-item>

    <div class="tab-action-row">
      <el-button
        type="danger"
        :loading="cleanupLoading"
        @click="emit('trigger-cleanup')"
      >
        立即清理
      </el-button>
    </div>
  </section>
</template>

<script setup lang="ts">
  defineOptions({
    name: "RecycleConfigTab",
    inheritAttrs: false,
  });

  type DurationUnit = "second" | "minute" | "hour" | "day";

  interface DurationDisplay {
    value: number;
    unit: DurationUnit;
  }

  const props = defineProps<{
    trashRetentionSeconds: number;
    cleanupIntervalSeconds: number;
    cleanupLoading?: boolean;
  }>();

  const emit = defineEmits<{
    (e: "update:trashRetentionSeconds", value: number): void;
    (e: "update:cleanupIntervalSeconds", value: number): void;
    (e: "trigger-cleanup"): void;
  }>();

  const durationUnitOptions: Array<{ label: string; value: DurationUnit }> = [
    { label: "秒", value: "second" },
    { label: "分钟", value: "minute" },
    { label: "小时", value: "hour" },
    { label: "天", value: "day" },
  ];

  const durationFactors: Record<DurationUnit, number> = {
    second: 1,
    minute: 60,
    hour: 60 * 60,
    day: 24 * 60 * 60,
  };

  const trashRetentionDuration = reactive<DurationDisplay>({
    value: 30,
    unit: "day",
  });

  const cleanupIntervalDuration = reactive<DurationDisplay>({
    value: 60,
    unit: "minute",
  });

  watch(
    () => props.trashRetentionSeconds,
    (seconds) => {
      syncDurationDisplay(trashRetentionDuration, seconds);
    },
    { immediate: true }
  );

  watch(
    () => props.cleanupIntervalSeconds,
    (seconds) => {
      syncDurationDisplay(cleanupIntervalDuration, seconds);
    },
    { immediate: true }
  );

  function syncDurationDisplay(target: DurationDisplay, seconds: number) {
    const next = normalizeDuration(seconds);
    target.value = next.value;
    target.unit = next.unit;
  }

  function normalizeDuration(seconds: number): DurationDisplay {
    if (seconds % durationFactors.day === 0) {
      return { value: seconds / durationFactors.day, unit: "day" };
    }
    if (seconds % durationFactors.hour === 0) {
      return { value: seconds / durationFactors.hour, unit: "hour" };
    }
    if (seconds % durationFactors.minute === 0) {
      return { value: seconds / durationFactors.minute, unit: "minute" };
    }
    return { value: seconds, unit: "second" };
  }

  function onDurationValueChange(
    field: "trashRetentionSeconds" | "cleanupIntervalSeconds",
    target: DurationDisplay,
    value: number | undefined
  ) {
    if (value == null || Number.isNaN(value)) return;
    emit(`update:${field}`, Math.max(1, Math.round(value * durationFactors[target.unit])));
  }

  function onDurationUnitChange(
    field: "trashRetentionSeconds" | "cleanupIntervalSeconds",
    target: DurationDisplay,
    unit: DurationUnit
  ) {
    target.unit = unit;
    target.value = props[field] / durationFactors[unit];
  }

  function durationHint(seconds: number) {
    return `当前约 ${formatDurationApprox(seconds)}`;
  }

  function formatDurationApprox(seconds: number) {
    if (!seconds || seconds <= 0) return "-";

    const units: Array<[DurationUnit, string]> = [
      ["day", "天"],
      ["hour", "小时"],
      ["minute", "分钟"],
      ["second", "秒"],
    ];

    for (const [unit, label] of units) {
      const factor = durationFactors[unit];
      if (seconds >= factor) {
        const value = seconds / factor;
        const rounded = Math.round(value * 10) / 10;
        return `${Number.isInteger(rounded) ? rounded : rounded.toFixed(1)} ${label}`;
      }
    }

    return `${seconds} 秒`;
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

  .duration-input-group {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
  }

  .duration-unit-select {
    width: 108px;
  }

  .hint-text {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
</style>
