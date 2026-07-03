<template>
  <el-dialog
    :model-value="modelValue"
    title="上传个人文件到团队"
    width="540"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div class="mb-3 text-[14px] font-600 text-[var(--el-text-color-primary)] break-all">
      {{ file?.originalName || "-" }}
    </div>

    <el-form label-position="top">
      <el-form-item label="目标团队">
        <el-select
          v-model="selectedTeamId"
          class="w-full"
          filterable
          placeholder="请选择团队"
        >
          <el-option
            v-for="team in teamOptions"
            :key="team.id"
            :label="team.name"
            :value="team.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目标团队目录">
        <FolderSelector
          v-if="selectedTeamId"
          v-model="targetDirectoryId"
          scope="team"
          :team-id="selectedTeamId"
        />
        <el-empty
          v-else
          description="请先选择团队"
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
      targetDirectoryId?: number;
    }>(),
    { targetDirectoryId: 0 }
  );

  const emit = defineEmits<{
    (e: "update:modelValue", value: boolean): void;
    (e: "changed"): void;
  }>();

  const teamOptions = ref<Array<{ id: number; name: string }>>([]);
  const selectedTeamId = ref<number>(0);
  const targetDirectoryId = ref(props.targetDirectoryId);

  watch(
    () => props.modelValue,
    async (visible) => {
      if (!visible) return;
      targetDirectoryId.value = props.targetDirectoryId ?? 0;
      try {
        const teams = await TeamAPI.listUserTeams();
        teamOptions.value = (Array.isArray(teams) ? teams : [])
          .filter((team: any) => team?.id)
          .map((team: any) => ({
            id: Number(team.id),
            name: String(team.name || `团队 ${team.id}`),
          }));
        selectedTeamId.value = teamOptions.value[0]?.id || 0;
      } catch {
        teamOptions.value = [];
        selectedTeamId.value = 0;
      }
    }
  );

  watch(selectedTeamId, () => {
    targetDirectoryId.value = 0;
  });

  async function handleConfirm() {
    if (!props.file) {
      ElMessage.warning("请选择文件");
      return;
    }
    if (!selectedTeamId.value) {
      ElMessage.warning("请选择团队");
      return;
    }

    try {
      await TeamAPI.transferFromPersonal(selectedTeamId.value, {
        sourceFileId: props.file.id,
        targetDirectoryId: targetDirectoryId.value,
      });
      ElMessage.success("上传到团队成功");
      emit("changed");
      emit("update:modelValue", false);
    } catch {
      ElMessage.error("上传到团队失败");
    }
  }
</script>
