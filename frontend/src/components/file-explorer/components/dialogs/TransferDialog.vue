<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="520"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <FolderSelector
      :model-value="targetDirectoryId"
      :scope="scope"
      :team-id="teamId"
      @update:model-value="(value) => emit('update:targetDirectoryId', value)"
    />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        @click="emit('confirm')"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import FolderSelector from "../common/FolderSelector.vue";

  const props = withDefaults(
    defineProps<{
      modelValue: boolean;
      action: "move" | "copy";
      targetDirectoryId: number;
      scope?: "personal" | "team" | "private";
      teamId?: number;
    }>(),
    { scope: "personal" }
  );

  const emit = defineEmits<{
    (e: "update:modelValue", value: boolean): void;
    (e: "update:targetDirectoryId", value: number): void;
    (e: "confirm"): void;
  }>();

  const dialogTitle = computed(() => (props.action === "move" ? "移动文件" : "复制文件"));
</script>
