<!-- 通用分段切换按钮组：沉浸式无边框风格。每个选项的内容通过以 value 命名的具名插槽自定义。 -->
<template>
  <div class="switch-group">
    <button
      v-for="option in options"
      :key="String(option.value)"
      type="button"
      class="switch-group__item"
      :class="{ 'is-active': modelValue === option.value }"
      :disabled="option.disabled"
      @click="emit('update:modelValue', option.value)"
    >
      <slot
        :name="String(option.value)"
        :option="option"
        :active="modelValue === option.value"
      />
    </button>
  </div>
</template>

<script lang="ts">
  /** 分段切换按钮的单个选项 */
  export interface SwitchOption<V extends string | number = string | number> {
    /** 选项值，同时作为对应内容插槽的名称 */
    value: V;
    /** 是否禁用该选项 */
    disabled?: boolean;
  }
</script>

<script setup lang="ts" generic="T extends string | number">
  defineProps<{
    /** 当前选中值（v-model） */
    modelValue: T;
    /** 选项列表 */
    options: SwitchOption<T>[];
  }>();

  const emit = defineEmits<{
    "update:modelValue": [value: T];
  }>();
</script>

<style scoped>
  /* 轨道：以 border-color-lighter 为底色的浅色凹槽 */
  .switch-group {
    display: inline-flex;
    gap: 2px;
    padding: 3px;
    background: var(--el-fill-color);
    border-radius: 6px;
  }

  .switch-group__item {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 5px 7px;
    color: var(--el-text-color-regular);
    cursor: pointer;
    background: transparent;
    border: none;
    border-radius: 4px;
    transition:
      color 0.18s ease,
      background-color 0.18s ease,
      box-shadow 0.18s ease;
  }

  .switch-group__item:disabled {
    color: var(--el-text-color-disabled);
    cursor: not-allowed;
    background: transparent;
    box-shadow: none;
  }

  .switch-group__item:not(.is-active):hover {
    color: var(--el-text-color-primary);
  }

  /* 选中项：略微浮起的白色滑块 */
  .switch-group__item.is-active {
    color: var(--el-color-primary);
    background: var(--el-bg-color);
    box-shadow:
      0 1px 2px 0 rgb(0 0 0 / 6%),
      0 1px 3px 0 rgb(0 0 0 / 10%);
  }
</style>
