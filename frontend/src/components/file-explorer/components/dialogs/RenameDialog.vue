<template>
  <el-dialog
    :model-value="modelValue"
    title="重命名目录"
    width="420"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form label-position="top">
      <el-form-item label="目录名称">
        <el-input
          v-model="inputName"
          maxlength="50"
          placeholder="请输入新目录名称"
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
  import DirectoryAPI from "@/api/directory.api";
  import TeamAPI from "@/api/team.api";

  const props = withDefaults(
    defineProps<{
      modelValue: boolean;
      directoryId: number | null;
      directoryName: string;
      scope?: "personal" | "team" | "private";
      teamId?: number;
    }>(),
    { scope: "personal" }
  );

  const emit = defineEmits<{
    "update:modelValue": [v: boolean];
    changed: [];
  }>();

  const inputName = ref("");

  watch(
    () => props.modelValue,
    (visible) => {
      if (visible) inputName.value = props.directoryName;
    }
  );

  async function handleConfirm() {
    const id = props.directoryId;
    const name = inputName.value.trim();
    if (!id) {
      ElMessage.warning("目录不存在");
      return;
    }
    if (!name) {
      ElMessage.warning("请输入目录名称");
      return;
    }
    const body = { name };
    try {
      if (props.scope === "private") {
        ElMessage.warning("私密空间暂不支持重命名");
        return;
      } else if (props.scope === "team" && props.teamId) {
        await TeamAPI.renameTeamDirectory(props.teamId, id, body);
      } else {
        await DirectoryAPI.renameDirectory(id, body);
      }
      ElMessage.success("重命名成功");
      emit("update:modelValue", false);
      emit("changed");
    } catch {
      ElMessage.error("重命名失败");
    }
  }
</script>
