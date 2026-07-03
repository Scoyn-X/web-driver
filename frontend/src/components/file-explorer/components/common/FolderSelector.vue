<template>
  <div class="folder-selector">
    <div class="folder-selector__tree">
      <FileTree
        :active-directory-id="modelValue"
        :scope="scope"
        :team-id="teamId"
        @change-directory="onDirectoryChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
  import FileTree from "../explorer/FileTree.vue";

  withDefaults(
    defineProps<{
      modelValue: number;
      scope?: "personal" | "team" | "private";
      teamId?: number;
    }>(),
    { scope: "personal" }
  );

  const emit = defineEmits<{
    (e: "update:modelValue", value: number): void;
  }>();

  function onDirectoryChange(directoryId: number) {
    emit("update:modelValue", directoryId);
  }
</script>

<style scoped>
  .folder-selector {
    display: flex;
    flex-direction: column;
    gap: 8px;
    width: 100%;
  }

  .folder-selector__path {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .folder-selector__tree {
    max-height: 400px;
    padding: 8px;
    overflow: auto;
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;
  }
</style>
