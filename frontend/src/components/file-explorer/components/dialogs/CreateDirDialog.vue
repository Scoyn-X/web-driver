<template>
  <el-dialog
    :model-value="modelValue"
    title="新建目录"
    width="520"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form label-position="top">
      <el-form-item label="目录名称">
        <el-input
          v-model="name"
          maxlength="50"
          placeholder="请输入目录名称"
        />
      </el-form-item>
      <el-form-item label="父目录">
        <FolderSelector
          v-model="parentId"
          :scope="scope"
          :team-id="teamId"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        @click="handleCreate"
      >
        创建
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import DirectoryAPI from "@/api/directory.api";
  import TeamAPI from "@/api/team.api";
  import { PersonalAPI } from "@/api/personal.api";
  import FolderSelector from "../common/FolderSelector.vue";

  const props = withDefaults(
    defineProps<{
      modelValue: boolean;
      parentDirectoryId: number;
      scope?: "personal" | "team" | "private";
      teamId?: number;
    }>(),
    { scope: "personal" }
  );

  const emit = defineEmits<{
    "update:modelValue": [v: boolean];
    changed: [];
  }>();

  const name = ref("");
  const parentId = ref(0);

  watch(
    () => props.modelValue,
    (visible) => {
      if (visible) {
        name.value = "";
        parentId.value = props.parentDirectoryId;
      }
    }
  );

  async function handleCreate() {
    const trimmed = name.value.trim();
    if (!trimmed) {
      ElMessage.warning("请输入目录名称");
      return;
    }
    const body = { name: trimmed, parentId: parentId.value };
    try {
      if (props.scope === "private") {
        await PersonalAPI.createPrivateDirectory(body);
      } else if (props.scope === "team" && props.teamId) {
        await TeamAPI.createTeamDirectory(props.teamId, body);
      } else {
        await DirectoryAPI.createDirectory(body);
      }
      ElMessage.success("目录创建成功");
      emit("update:modelValue", false);
      emit("changed");
    } catch {
      ElMessage.error("目录创建失败");
    }
  }
</script>
