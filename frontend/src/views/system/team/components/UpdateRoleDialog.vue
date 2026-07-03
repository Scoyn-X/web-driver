<template>
  <el-dialog
    :model-value="modelValue"
    title="修改成员角色"
    width="420"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div
      v-if="member"
      class="mb-4 text-sm text-[var(--el-text-color-secondary)]"
    >
      当前成员：
      <UserCell
        :nickname="member?.nickname"
        :account-name="member?.accountName"
        :show-avatar="false"
        inline
      />
    </div>

    <el-form label-position="top">
      <el-form-item label="新角色">
        <el-radio-group v-model="form.role">
          <el-radio
            v-for="r in ASSIGNABLE_ROLES"
            :key="r"
            :value="r"
          >
            {{ roleLabel(r) }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!member"
        @click="handleSubmit"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import TeamAPI from "@/api/team.api";
  import type { UserBriefResponseVO } from "@/api/user.api";
  import { TeamRole, ASSIGNABLE_ROLES } from "@/enums/team.enum";
  import { roleLabel } from "@/views/system/team/team-tag";

  type TeamMember = UserBriefResponseVO & {
    id?: number | string;
    role?: string;
  };

  const props = defineProps<{
    modelValue: boolean;
    teamId: number;
    member: TeamMember | null;
  }>();

  const emit = defineEmits<{
    "update:modelValue": [v: boolean];
    updated: [];
  }>();

  const submitting = ref(false);
  const form = reactive<{ role: TeamRole }>({ role: TeamRole.Editor });

  watch(
    () => props.modelValue,
    (visible) => {
      if (!visible || !props.member) return;
      const matched = ASSIGNABLE_ROLES.find((r) => r === props.member?.role);
      form.role = matched ?? TeamRole.Editor;
    }
  );

  async function handleSubmit() {
    if (!props.member) return;
    if (props.member.role === form.role) {
      ElMessage.info("角色未变化");
      emit("update:modelValue", false);
      return;
    }
    submitting.value = true;
    try {
      const memberId = Number(props.member.id);
      if (!Number.isSafeInteger(memberId)) {
        ElMessage.error("成员信息异常，请刷新后重试");
        return;
      }
      await TeamAPI.updateMemberRole(props.teamId, memberId, {
        role: form.role,
      });
      ElMessage.success("角色已更新");
      emit("updated");
      emit("update:modelValue", false);
    } finally {
      submitting.value = false;
    }
  }
</script>
