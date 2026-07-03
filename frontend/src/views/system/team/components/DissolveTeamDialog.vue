<template>
  <el-dialog
    :model-value="modelValue"
    title="解散团队"
    width="480px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="mb-4 text-[var(--el-text-color-regular)]">
      确认解散该团队？所有团队数据（文件、分享、邀请记录等）将被永久清除，此操作
      <strong>不可恢复</strong>
      。
    </div>

    <el-form
      :model="form"
      label-width="80px"
    >
      <el-form-item label="解散原因">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          placeholder="可选，填写解散原因"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="danger"
        :loading="submitting"
        @click="handleSubmit"
      >
        确认解散
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import TeamAPI from "@/api/team.api";

  defineOptions({
    name: "DissolveTeam",
    inheritAttrs: false,
  });

  const props = defineProps<{
    modelValue: boolean;
    teamId: number;
  }>();

  const emit = defineEmits<{
    (e: "update:modelValue", v: boolean): void;
    (e: "dissolved"): void;
  }>();

  const submitting = ref(false);
  const form = reactive({ reason: "" });

  async function handleSubmit() {
    submitting.value = true;
    try {
      const payload = form.reason.trim() ? { reason: form.reason.trim() } : undefined;
      await TeamAPI.dissolveTeam(props.teamId, payload);
      ElMessage.success("团队已解散");
      emit("dissolved");
      emit("update:modelValue", false);
    } finally {
      submitting.value = false;
    }
  }
</script>
