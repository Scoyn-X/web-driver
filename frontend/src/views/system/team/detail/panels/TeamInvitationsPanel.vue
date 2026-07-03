<template>
  <div class="panel-container">
    <div class="invitation-header">
      <div class="invitation-header__actions">
        <el-button
          v-hasPerm="['member:invite']"
          text
          icon="Plus"
          @click="dialogVisible = true"
        >
          邀请新成员
        </el-button>
        <el-button
          text
          icon="Refresh"
          @click="fetchInvitations"
        >
          刷新
        </el-button>
      </div>
    </div>
    <el-table
      v-loading="loading"
      :data="invitations"
    >
      <template #empty>
        <el-empty description="暂无邀请" />
      </template>
      <el-table-column
        width="48"
        align="center"
      >
        <template #header>
          <el-checkbox
            :model-value="allSelected"
            :indeterminate="selectionIndeterminate"
            @change="toggleAll"
          />
        </template>
        <template #default="{ row }">
          <el-checkbox
            :model-value="isSelected(row.id)"
            @click.stop
            @change="(checked) => toggleRow(row.id, checked)"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="被邀请人"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <UserCell
            :nickname="row.inviteeName"
            :account-name="row.inviteeAccountName"
            :size="28"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="邀请人"
        min-width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <UserCell
            :nickname="row.inviterName"
            :account-name="row.inviterAccountName"
            :size="28"
          />
        </template>
      </el-table-column>
      <el-table-column
        label="角色"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          {{ roleLabel(row.targetRole || row.role) || "-" }}
        </template>
      </el-table-column>
      <el-table-column
        label="状态"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            :type="invitationStatusColor(row.status)"
            size="small"
          >
            {{ invitationStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createTime"
        label="邀请时间"
        width="160"
        align="center"
        :formatter="(row: any) => fmtTime(row.createTime)"
      />
      <el-table-column
        prop="expireAt"
        label="过期时间"
        width="160"
        align="center"
        :formatter="(_r: any, _c: any, v: string) => fmtTime(v)"
      />
      <el-table-column
        label="操作"
        fixed="right"
        width="150"
        align="center"
      >
        <template #default="{ row }">
          <div class="operation-cell">
            <el-button
              link
              type="primary"
              icon="View"
              @click="openFlow(row)"
            >
              流程
            </el-button>
            <el-button
              v-if="(row.status || '').toString().toLowerCase() === 'pending'"
              link
              type="danger"
              icon="Close"
              @click="revoke(row.id)"
            >
              撤销
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <InviteMemberDialog
      v-model="dialogVisible"
      :team-id="teamId"
      @sent="onSent"
    />

    <el-dialog
      v-model="flowVisible"
      title="邀请流程"
      width="1180px"
      destroy-on-close
    >
      <div
        v-loading="flowLoading"
        class="flow-dialog"
      >
        <BpmnViewer
          v-if="flowXml"
          :xml="flowXml"
          :active-node-ids="flowActiveNodeIds"
          :completed-node-ids="flowCompletedNodeIds"
          :nodes="flowNodes"
          :height="400"
          :timeline-width="420"
        />
        <el-empty
          v-else
          description="暂无可渲染的流程图"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
  import InvitationsAPI, { type ProcessNodeInfoResponseVO } from "@/api/invitations.api";
  import TeamAPI from "@/api/team.api";
  import {
    invitationStatusColor,
    invitationStatusLabel,
    roleLabel,
  } from "@/views/system/team/team-tag";
  import InviteMemberDialog from "@/views/system/team/components/InviteMemberDialog.vue";

  defineOptions({
    name: "TeamInvitations",
    inheritAttrs: false,
  });

  const props = defineProps<{
    teamId: number;
  }>();

  const emit = defineEmits<{
    (e: "changed"): void;
  }>();

  const invitations = ref<any[]>();
  const loading = ref(false);
  const dialogVisible = ref(false);
  const flowVisible = ref(false);
  const flowLoading = ref(false);
  const flowXml = ref("");
  const flowActiveNodeIds = ref<string[]>([]);
  const flowCompletedNodeIds = ref<string[]>([]);
  const flowNodes = ref<ProcessNodeInfoResponseVO[]>([]);
  const selectedIds = ref<number[]>([]);

  const allSelected = computed(
    () => invitations.value?.length > 0 && selectedIds.value.length === invitations.value.length
  );
  const selectionIndeterminate = computed(
    () =>
      selectedIds.value.length > 0 && selectedIds.value.length < (invitations.value?.length ?? 0)
  );

  function fmtTime(value: any) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    const year = date.getFullYear();
    const month = `${date.getMonth() + 1}`.padStart(2, "0");
    const day = `${date.getDate()}`.padStart(2, "0");
    const hours = `${date.getHours()}`.padStart(2, "0");
    const minutes = `${date.getMinutes()}`.padStart(2, "0");
    const seconds = `${date.getSeconds()}`.padStart(2, "0");
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
  }

  async function fetchInvitations() {
    if (!props.teamId) return;
    loading.value = true;
    try {
      invitations.value = await TeamAPI.listTeamInvitations(props.teamId);
      selectedIds.value = [];
    } finally {
      loading.value = false;
    }
  }

  function isSelected(id: number) {
    return selectedIds.value.includes(id);
  }

  function toggleRow(id: number, checked: boolean) {
    selectedIds.value = checked
      ? Array.from(new Set([...selectedIds.value, id]))
      : selectedIds.value.filter((item) => item !== id);
  }

  function toggleAll(checked: boolean) {
    selectedIds.value = checked ? (invitations.value?.map((item) => item.id) ?? []) : [];
  }

  async function openFlow(invitation: any) {
    flowVisible.value = true;
    flowLoading.value = true;
    flowXml.value = "";
    flowActiveNodeIds.value = [];
    flowCompletedNodeIds.value = [];
    flowNodes.value = [];

    try {
      const diagram = await InvitationsAPI.getProcessDiagram(invitation.id);
      const backendXml = diagram.bpmnXml || "";
      flowXml.value = backendXml;
      flowActiveNodeIds.value = diagram.activeNodeIds || [];
      flowCompletedNodeIds.value = diagram.completedNodeIds || [];
      flowNodes.value = diagram.nodes || [];

      if (!backendXml) {
        ElMessage.warning("后端未返回可渲染的流程图 XML");
      }
    } catch {
      ElMessage.error("获取流程图失败");
    } finally {
      flowLoading.value = false;
    }
  }

  async function revoke(invitationId: number) {
    try {
      await ElMessageBox.confirm("确认撤销该邀请？", "提示", {
        type: "warning",
        confirmButtonText: "撤销",
        cancelButtonText: "取消",
      });
      const payload = { action: "REVOKE" as const, invitationId };
      await TeamAPI.handleInvitationAction(props.teamId, payload);
      ElMessage.success("已撤销");
      await fetchInvitations();
      emit("changed");
    } catch (error: any) {
      if (error === "cancel" || error === "close") return;
      ElMessage.error("撤销失败");
    }
  }

  function onSent() {
    dialogVisible.value = false;
    fetchInvitations();
    emit("changed");
  }

  watch(() => props.teamId, fetchInvitations);

  defineExpose({ refresh: fetchInvitations });
</script>

<style scoped>
  .panel-container {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
    padding: 4px 12px;
  }

  .invitation-header {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    padding: 6px 12px;
    margin: 0 -12px;
    background: var(--el-fill-color-blank);
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .invitation-header__actions {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    align-items: center;
  }

  .invitation-header :deep(.el-button) {
    padding: 6px 10px;
    margin: 0;
    color: var(--el-text-color-regular);
    background: transparent;
    border: none;
    border-radius: 4px;
  }

  .operation-cell {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
    justify-content: center;
  }
</style>
