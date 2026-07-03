<template>
  <el-dialog
    :model-value="modelValue"
    title="私密空间提醒"
    width="440px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="text-[var(--el-text-color-regular)]">
      <el-icon class="mr-1 text-[var(--el-color-warning)]"><Warning /></el-icon>
      {{ message }}
    </div>

    <template #footer>
      <el-button
        type="primary"
        @click="emit('update:modelValue', false)"
      >
        我知道了
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  defineOptions({
    name: "PrivateReminder",
    inheritAttrs: false,
  });

  defineProps<{
    modelValue: boolean;
    message: string;
  }>();

  const emit = defineEmits<{
    (e: "update:modelValue", v: boolean): void;
  }>();
</script>
