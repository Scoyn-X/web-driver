<template>
  <div class="team-members-panel">
    <el-table
      v-loading="loading"
      :data="members"
      row-key="id"
    >
      <template #empty>
        <el-empty description="暂无成员" />
      </template>
      <el-table-column
        label="成员"
        min-width="220"
      >
        <template #default="{ row }">
          <UserCell
            :nickname="row.nickname || row.username"
            :account-name="row.accountName"
          />
        </template>
      </el-table-column>

      <el-table-column
        label="角色"
        width="120"
      >
        <template #default="{ row }">
          {{ roleLabel(row.role) }}
        </template>
      </el-table-column>

      <el-table-column
        label="加入时间"
        width="180"
      >
        <template #default="{ row }">
          {{ formatTimestamp(memberJoinTime(row)) }}
        </template>
      </el-table-column>

      <el-table-column
        label="操作"
        width="160"
        align="center"
      >
        <template #default="{ row }">
          <template v-if="Number(row.userId) !== currentUserId && !isOwnerRole(row.role)">
            <el-button
              v-hasPerm="['role:update']"
              link
              icon="edit"
              type="primary"
              @click="openEdit(row)"
            >
              改角色
            </el-button>
            <el-button
              v-hasPerm="['member:remove']"
              link
              icon="delete"
              type="danger"
              @click="confirmRemove(row)"
            >
              移除
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <UpdateRoleDialog
      v-model="dialogVisible"
      :team-id="teamId"
      :member="editingMember"
      @updated="onUpdated"
    />
  </div>
</template>

<script setup lang="ts">
  import TeamAPI from "@/api/team.api";
  import type { UserBriefResponseVO } from "@/api/user.api";
  import { formatTimestamp } from "@/utils/format-time";
  import { roleLabel } from "@/views/system/team/team-tag";
  import { useUserStore } from "@/store/user";

  type TeamMember = UserBriefResponseVO & {
    id?: number | string;
    userId?: number | string;
    accountId?: number | string;
    role?: string;
    joinTime?: string;
    joinedAt?: string;
    username?: string;
  };

  const props = defineProps<{
    teamId: number;
  }>();

  const emit = defineEmits<{
    changed: [];
  }>();

  const members = ref<TeamMember[]>([]);
  const loading = ref(false);
  const dialogVisible = ref(false);
  const editingMember = ref<TeamMember | null>(null);
  const currentUserId = useUserStore().userId;

  function memberJoinTime(member: TeamMember) {
    return member.joinTime || member.joinedAt || "";
  }

  function isOwnerRole(role?: string) {
    return (role || "").toString().toLowerCase() === "owner";
  }

  async function fetchMembers() {
    if (!props.teamId) return;
    loading.value = true;
    try {
      const res = await TeamAPI.listMembers(props.teamId);
      const list = Array.isArray(res) ? res : ((res as any)?.records ?? (res as any)?.list ?? []);
      members.value = list;
    } finally {
      loading.value = false;
    }
  }

  function openEdit(member: TeamMember) {
    editingMember.value = member;
    dialogVisible.value = true;
  }

  function onUpdated() {
    fetchMembers();
    emit("changed");
  }

  async function confirmRemove(member: TeamMember) {
    try {
      await ElMessageBox.confirm(
        `确认将「${member.nickname || member.accountName || `用户${member.userId}`}」移出团队？被移除后该成员将立即失去访问权限。`,
        "移除成员",
        { type: "warning", confirmButtonText: "移除", cancelButtonText: "取消" }
      );
    } catch {
      return;
    }
    try {
      const memberId = Number(member.id);
      if (!Number.isSafeInteger(memberId)) {
        ElMessage.error("成员信息异常，请刷新后重试");
        return;
      }
      await TeamAPI.removeMember(props.teamId, memberId);
      ElMessage.success("已移除");
      fetchMembers();
      emit("changed");
    } catch {
      // 错误已由 axios 拦截器统一提示
    }
  }

  watch(() => props.teamId, fetchMembers);

  defineExpose({ refresh: fetchMembers });
</script>

<style scoped>
  .team-members-panel {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
    padding: 4px 12px;
  }
</style>

<style>
  .team-detail__body .el-table {
    --el-table-border-color: transparent;
  }
  .team-detail__body .el-table__inner-wrapper::before {
    display: none;
  }
</style>
