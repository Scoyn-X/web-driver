<template>
  <el-dialog
    :model-value="modelValue"
    title="转让团队 Owner"
    width="440px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
    >
      <el-form-item
        label="新 Owner"
        prop="targetAccountId"
      >
        <el-select
          v-model="form.targetAccountId"
          v-loading="loading"
          placeholder="请选择团队成员"
          clearable
        >
          <el-option
            v-for="m in members"
            :key="m.accountId"
            :label="memberLabel(m)"
            :value="m.accountId"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <div class="text-[13px] text-[var(--el-text-color-secondary)]">
      转让后你将失去 Owner 权限，变为 Admin 角色。此操作不可撤销。
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="danger"
        :loading="submitting"
        @click="handleSubmit"
      >
        确认转让
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import type { FormRules } from "element-plus";
  import TeamAPI from "@/api/team.api";

  type TeamMemberOption = {
    accountId: number;
    userId?: number | string;
    accountName?: string;
    nickname?: string;
  };

  defineOptions({
    name: "TransferOwner",
    inheritAttrs: false,
  });

  const props = defineProps<{
    modelValue: boolean;
    teamId: number;
  }>();

  const emit = defineEmits<{
    (e: "update:modelValue", v: boolean): void;
    (e: "transferred"): void;
  }>();

  const formRef = ref();
  const submitting = ref(false);
  const loading = ref(false);
  const members = ref<TeamMemberOption[]>([]);
  const form = reactive({ targetAccountId: null as number | null });

  const rules: FormRules = {
    targetAccountId: [{ required: true, message: "请选择新 Owner", trigger: "change" }],
  };

  function memberLabel(member: TeamMemberOption) {
    const name = member?.nickname || member?.accountName || `用户${member?.userId ?? ""}`;
    return member?.accountName && member?.nickname ? `${name} (@${member.accountName})` : name;
  }

  async function fetchMembers() {
    if (!props.teamId) return;
    loading.value = true;
    try {
      const list = await TeamAPI.listMembers(props.teamId);
      members.value = list
        .map((member) => ({ ...member, accountId: Number(member.accountId) }))
        .filter((member) => Number.isSafeInteger(member.accountId));
    } finally {
      loading.value = false;
    }
  }

  watch(
    () => props.modelValue,
    (visible) => {
      if (visible) {
        form.targetAccountId = null;
        fetchMembers();
        nextTick(() => formRef.value?.clearValidate());
      }
    }
  );

  async function handleSubmit() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid || !form.targetAccountId) return;
    submitting.value = true;
    const payload = { targetAccountId: form.targetAccountId };
    try {
      await TeamAPI.transferOwner(props.teamId, payload);
      ElMessage.success("Owner 已转让");
      emit("transferred");
      emit("update:modelValue", false);
    } finally {
      submitting.value = false;
    }
  }
</script>
