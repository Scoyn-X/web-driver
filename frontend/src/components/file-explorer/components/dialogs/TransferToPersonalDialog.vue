<template>
  <el-dialog
    :model-value="modelValue"
    title="转存到个人空间"
    width="520"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div class="mb-3 text-[14px] font-600 text-[var(--el-text-color-primary)] break-all">
      {{ file?.originalName || "-" }}
    </div>

    <el-form label-position="top">
      <el-form-item label="目标个人目录">
        <FolderSelector
          v-model="targetDirectoryId"
          scope="personal"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        @click="handleConfirm"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import type { FileInfoResponseVO as FileInfo } from "@/api/file.api";
  import TeamAPI from "@/api/team.api";
  import FolderSelector from "../common/FolderSelector.vue";

  const props = withDefaults(
    defineProps<{
      modelValue: boolean;
      file: FileInfo | null;
      teamId?: number;
      targetDirectoryId?: number;
    }>(),
    { targetDirectoryId: 0 }
  );

  const emit = defineEmits<{
    (e: "update:modelValue", value: boolean): void;
    (e: "changed"): void;
  }>();

  const targetDirectoryId = ref(props.targetDirectoryId);

  watch(
    () => props.modelValue,
    (visible) => {
      if (visible) {
        targetDirectoryId.value = props.targetDirectoryId ?? 0;
      }
    }
  );

  async function handleConfirm() {
    if (!props.file) {
      ElMessage.warning("请选择文件");
      return;
    }
    if (!props.teamId) {
      ElMessage.warning("团队信息缺失");
      return;
    }

    try {
      await TeamAPI.transferToPersonal(props.teamId, props.file.id, {
        targetDirectoryId: targetDirectoryId.value,
      });
      ElMessage.success("转存成功");
      emit("changed");
      emit("update:modelValue", false);
    } catch {
      ElMessage.error("转存失败");
    }
  }
</script>
