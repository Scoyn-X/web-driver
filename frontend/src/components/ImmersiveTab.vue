<!--
  ImmersiveTabs —— 沉浸式标签页（浏览器标签风格）。
  全局组件，供文件管理、团队管理等多处复用。

  用法（标签内容支持以 name 命名的具名插槽，未提供插槽时回退到 label）：
    <ImmersiveTabs v-model="active" :tabs="[{ name: 'a' }]">
      <template #a>标签A</template>
    </ImmersiveTabs>
-->
<template>
  <div
    class="im-tabs"
    role="tablist"
  >
    <button
      v-for="(tab, index) in tabs"
      :key="tab.name"
      ref="itemRefs"
      type="button"
      role="tab"
      :tabindex="modelValue === tab.name ? 0 : -1"
      :aria-selected="modelValue === tab.name"
      class="im-tabs__item"
      :class="{ 'is-active': modelValue === tab.name }"
      @click="select(tab.name)"
      @keydown="(event) => onKeydown(event, index)"
    >
      <span>
        <slot
          :name="tab.name"
          :tab="tab"
          :active="modelValue === tab.name"
        >
          {{ tab.label }}
        </slot>
      </span>
      <span
        v-if="typeof tab.count === 'number'"
        class="im-tabs__count"
      >
        {{ tab.count }}
      </span>
    </button>
  </div>
</template>

<script setup lang="ts">
  /** 单个标签定义 */
  export interface ImmersiveTab {
    /** 唯一标识，作为 v-model 的值 */
    name: string;
    /** 展示文案（未提供同名插槽时作为兜底文本） */
    label?: string;
    /** 可选数量徽标 */
    count?: number;
  }

  const props = defineProps<{
    modelValue: string;
    tabs: ImmersiveTab[];
  }>();

  const emit = defineEmits<{
    "update:modelValue": [name: string];
    change: [name: string];
  }>();

  const itemRefs = ref<HTMLButtonElement[]>([]);

  function select(name: string) {
    if (name === props.modelValue) return;
    emit("update:modelValue", name);
    emit("change", name);
  }

  function onKeydown(event: KeyboardEvent, index: number) {
    if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") return;
    event.preventDefault();
    const offset = event.key === "ArrowRight" ? 1 : -1;
    const nextIndex = (index + offset + props.tabs.length) % props.tabs.length;
    select(props.tabs[nextIndex].name);
    itemRefs.value[nextIndex]?.focus();
  }
</script>

<style scoped>
  .im-tabs {
    display: flex;
    gap: 4px;
    align-items: flex-end;
  }

  .im-tabs__item :deep(.el-icon) {
    display: inline-flex;
    align-items: center;
    vertical-align: -0.1em;
  }

  .im-tabs__item {
    position: relative;
    display: inline-flex;
    gap: 4px;
    align-items: center;
    height: 38px;
    padding: 0 18px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
    cursor: pointer;
    background: transparent;
    border: 1px solid transparent;
    border-bottom: none;
    border-radius: 6px 6px 0 0;
    transition:
      color 0.15s ease,
      background-color 0.15s ease,
      border-color 0.15s ease;
  }

  .im-tabs__item:hover {
    color: var(--el-text-color-primary);
  }

  .im-tabs__item.is-active {
    z-index: 1;
    margin-bottom: -1px;
    color: var(--el-text-color-primary);
    background: var(--el-fill-color-blank);
    border-color: var(--el-border-color-light);
  }

  .im-tabs__item:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: -3px;
    border-radius: 6px;
  }

  .im-tabs__count {
    min-width: 18px;
    padding: 0 5px;
    font-size: 12px;
    line-height: 18px;
    text-align: center;
    background: var(--el-fill-color);
    border-radius: 9px;
  }

  .im-tabs__item.is-active .im-tabs__count {
    color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }
</style>
