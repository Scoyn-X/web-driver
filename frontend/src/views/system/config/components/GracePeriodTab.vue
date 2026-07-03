<template>
  <section class="config-section">
    <h3 class="section-title">私密空间宽限期</h3>
    <p class="section-description">VIP 降级后，私密空间在宽限期内仍可继续访问。</p>

    <el-form-item
      label="宽限期时长"
      prop="privateGracePeriodSeconds"
    >
      <div class="duration-input-group">
        <el-input-number
          v-model="duration.value"
          :min="1"
          :step="1"
          controls-position="right"
          @update:model-value="onDurationValueChange"
        />
        <el-select
          v-model="duration.unit"
          class="duration-unit-select"
          @change="onDurationUnitChange"
        >
          <el-option
            v-for="option in durationUnitOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </div>
      <span class="hint-text">当前约 {{ hintText }}</span>
    </el-form-item>
  </section>
</template>

<script setup lang="ts">
  defineOptions({
    name: "GracePeriodConfig",
    inheritAttrs: false,
  });

  type DurationUnit = "second" | "minute" | "hour" | "day";

  interface DurationDisplay {
    value: number;
    unit: DurationUnit;
  }

  const props = withDefaults(
    defineProps<{
      modelValue?: number;
    }>(),
    {
      modelValue: 7 * 24 * 60 * 60,
    }
  );

  const emit = defineEmits<{
    (e: "update:modelValue", value: number): void;
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

  const duration = reactive<DurationDisplay>({
    value: 7,
    unit: "day",
  });

  const hintText = computed(() => formatDurationApprox(props.modelValue ?? 0));

  watch(
    () => props.modelValue,
    (value) => {
      syncDurationDisplay(value ?? 0);
    },
    { immediate: true }
  );

  function syncDurationDisplay(seconds: number) {
    const next = normalizeDuration(seconds);
    duration.value = next.value;
    duration.unit = next.unit;
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

  function onDurationValueChange(value: number | undefined) {
    if (value == null || Number.isNaN(value)) return;
    emit("update:modelValue", Math.max(1, Math.round(value * durationFactors[duration.unit])));
  }

  function onDurationUnitChange(unit: DurationUnit) {
    duration.unit = unit;
    duration.value = (props.modelValue ?? 0) / durationFactors[unit];
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
    margin: 0 0 8px;
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .section-description {
    margin: 0 0 16px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
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
