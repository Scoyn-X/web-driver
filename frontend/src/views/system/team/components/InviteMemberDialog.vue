<template>
  <el-dialog
    :model-value="modelValue"
    title="邀请成员"
    width="480px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
    >
      <el-form-item
        label="搜索用户"
        prop="inviteeAccountId"
      >
        <el-select
          v-model="form.inviteeAccountId"
          filterable
          remote
          clearable
          placeholder="输入账号、昵称或邮箱"
          :remote-method="onSearch"
          :loading="searching"
          style="width: 100%"
        >
          <el-option
            v-for="u in candidates"
            :key="u.accountId"
            :value="u.accountId"
            :label="`${u.nickname} (@${u.accountName})`"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="角色"
        prop="role"
      >
        <el-select v-model="form.role">
          <el-option
            v-for="r in ASSIGNABLE_ROLES"
            :key="r"
            :value="r"
            :label="roleLabel(r)"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="有效期"
        prop="expireValue"
      >
        <div class="flex gap-2">
          <el-input-number
            v-model="expireValue"
            :min="1"
            :step="1"
            controls-position="right"
            style="width: 140px"
          />
          <el-select
            v-model="expireUnit"
            style="width: 100px"
          >
            <el-option
              label="秒"
              value="seconds"
            />
            <el-option
              label="分钟"
              value="minutes"
            />
            <el-option
              label="小时"
              value="hours"
            />
            <el-option
              label="天"
              value="days"
            />
          </el-select>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="handleSubmit"
      >
        发送邀请
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import type { FormInstance, FormRules } from "element-plus";
  import TeamAPI from "@/api/team.api";
  import UserAPI, { type UserBriefResponseVO } from "@/api/user.api";
  import { TeamRole, ASSIGNABLE_ROLES } from "@/enums/team.enum";
  import { roleLabel } from "@/views/system/team/team-tag";

  type SearchUserResponse = UserBriefResponseVO & { accountId?: number | string };
  type UserCandidate = Omit<UserBriefResponseVO, "accountId"> & { accountId: number };

  const props = defineProps<{
    modelValue: boolean;
    teamId: number;
  }>();

  const emit = defineEmits<{
    (e: "update:modelValue", v: boolean): void;
    (e: "sent"): void;
  }>();

  const formRef = ref<FormInstance | null>(null);
  const submitting = ref(false);
  const searching = ref(false);
  const candidates = ref<UserBriefResponseVO[]>([]);
  const expireValue = ref(24);
  const expireUnit = ref<"seconds" | "minutes" | "hours" | "days">("hours");
  const form = reactive<{
    inviteeAccountId: number | null;
    role: TeamRole;
  }>({
    inviteeAccountId: null,
    role: TeamRole.Editor,
  });

  const rules: FormRules = {
    inviteeAccountId: [{ required: true, message: "请选择被邀请用户", trigger: "change" }],
    role: [{ required: true, message: "请选择角色", trigger: "change" }],
  };

  watch(
    () => props.modelValue,
    (visible) => {
      if (visible) {
        form.inviteeAccountId = null;
        form.role = TeamRole.Editor;
        expireValue.value = 24;
        expireUnit.value = "hours";
        candidates.value = [];
        nextTick(() => formRef.value?.clearValidate());
      }
    }
  );

  async function onSearch(keyword: string) {
    if (!keyword) {
      candidates.value = [];
      return;
    }
    searching.value = true;
    try {
      candidates.value = ((await UserAPI.searchUsers(keyword)) as SearchUserResponse[])
        .map((user) => ({ ...user, accountId: Number(user.accountId) }))
        .filter((user): user is UserCandidate => Number.isSafeInteger(user.accountId));
    } finally {
      searching.value = false;
    }
  }

  async function handleSubmit() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid || !form.inviteeAccountId) return;
    submitting.value = true;
    try {
      const multipliers: Record<string, number> = {
        seconds: 1 / 3600,
        minutes: 1 / 60,
        hours: 1,
        days: 24,
      };
      const expireHours = expireValue.value * (multipliers[expireUnit.value] || 1);
      await TeamAPI.createInvitation(props.teamId, {
        inviteeAccountId: form.inviteeAccountId,
        roleCode: form.role,
        expireSeconds: expireHours * 3600,
      });
      ElMessage.success("邀请已发送");
      emit("sent");
    } finally {
      submitting.value = false;
    }
  }
</script>
