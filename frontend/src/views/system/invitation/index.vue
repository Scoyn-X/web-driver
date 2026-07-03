<template>
  <section class="invitation-page">
    <header class="mb-5">
      <h2 class="m-0 text-xl font-semibold">我的邀请</h2>
      <p class="mt-1.5 mb-0 text-[13px] text-[var(--el-text-color-secondary)]">
        查看和处理收到的团队邀请
      </p>
    </header>

    <div class="invitation-page__tabbar">
      <ImmersiveTabs
        v-model="filter"
        :tabs="filterTabs"
      >
        <template #all>全部</template>
        <template #pending>待处理</template>
        <template #done>已处理</template>
      </ImmersiveTabs>
    </div>

    <el-table
      v-loading="loading"
      :data="filteredList"
    >
      <template #empty>
        <el-empty description="暂无邀请" />
      </template>
      <el-table-column
        prop="teamName"
        label="团队"
        min-width="160"
        show-overflow-tooltip
      />
      <el-table-column
        label="邀请人"
        width="200"
        show-overflow-tooltip
      >
        <template #default="{ row }">
          <UserCell
            v-if="row.inviterName"
            :nickname="row.inviterName"
            :account-name="row.inviterAccountName"
            :size="28"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column
        label="角色"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          {{ roleLabel(row.targetRole) }}
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
        prop="expireAt"
        label="过期时间"
        width="160"
        align="center"
        :formatter="(_r: any, _c: any, v: string) => (v ? fmtTime(v) : '-')"
      />
      <el-table-column
        label="操作"
        width="220"
        align="center"
      >
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            icon="View"
            @click="openFlow(row)"
          >
            流程
          </el-button>
          <template v-if="row.status === 'PENDING'">
            <el-button
              link
              type="primary"
              icon="Check"
              @click="handleAccept(row)"
            >
              接受
            </el-button>
            <el-button
              link
              type="danger"
              icon="Close"
              @click="handleReject(row)"
            >
              拒绝
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="flowVisible"
      title="邀请流程"
      width="1000px"
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
          :height="500"
          :timeline-width="350"
        />
        <el-empty
          v-else
          description="暂无可渲染的流程图"
        />
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
  import BpmnViewer from "@/components/BpmnViewer.vue";
  import InvitationsAPI, { type ProcessNodeInfoResponseVO } from "@/api/invitations.api";
  import ImmersiveTabs from "@/components/ImmersiveTab.vue";
  import UserAPI from "@/api/user.api";
  import TeamAPI from "@/api/team.api";
  import { formatTimestamp } from "@/utils/format-time";
  import {
    invitationStatusColor,
    invitationStatusLabel,
    roleLabel,
  } from "@/views/system/team/team-tag";

  defineOptions({
    name: "MyInvitations",
    inheritAttrs: false,
  });

  const filterTabs: { name: string }[] = [{ name: "all" }, { name: "pending" }, { name: "done" }];

  const filter = ref("all");
  const invitations = ref<any[]>();
  const loading = ref(false);
  const flowVisible = ref(false);
  const flowLoading = ref(false);
  const flowXml = ref("");
  const flowActiveNodeIds = ref<string[]>([]);
  const flowCompletedNodeIds = ref<string[]>([]);
  const flowNodes = ref<ProcessNodeInfoResponseVO[]>([]);

  const filteredList = computed(() => {
    if (!invitations.value) return [];
    if (filter.value === "pending") {
      return invitations.value.filter((i: any) => i.status === "PENDING");
    }
    if (filter.value === "done") {
      return invitations.value.filter((i: any) => i.status !== "PENDING");
    }
    return invitations.value;
  });

  function fmtTime(value: string) {
    return value ? formatTimestamp(value) : "-";
  }

  async function fetchInvitations() {
    loading.value = true;
    try {
      invitations.value = await UserAPI.listMyInvitations();
    } finally {
      loading.value = false;
    }
  }

  async function openFlow(row: any) {
    flowVisible.value = true;
    flowLoading.value = true;
    flowXml.value = "";
    flowActiveNodeIds.value = [];
    flowCompletedNodeIds.value = [];
    flowNodes.value = [];

    try {
      const diagram = await InvitationsAPI.getProcessDiagram(row.id);
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

  async function handleAccept(row: any) {
    try {
      await ElMessageBox.confirm(`确认加入团队「${row.teamName}」？`, "接受邀请", {
        type: "info",
        confirmButtonText: "接受",
        cancelButtonText: "取消",
      });
      await TeamAPI.acceptInvitation(row.teamId, row.id);
      ElMessage.success("已加入团队");
      await fetchInvitations();
    } catch (error: any) {
      if (error === "cancel" || error === "close") return;
      ElMessage.error("操作失败");
    }
  }

  async function handleReject(row: any) {
    try {
      await ElMessageBox.confirm(`确认拒绝「${row.teamName}」的邀请？`, "拒绝邀请", {
        type: "warning",
        confirmButtonText: "拒绝",
        cancelButtonText: "取消",
      });
      await TeamAPI.rejectInvitation(row.teamId, row.id);
      ElMessage.success("已拒绝");
      await fetchInvitations();
    } catch (error: any) {
      if (error === "cancel" || error === "close") return;
      ElMessage.error("操作失败");
    }
  }

  onMounted(fetchInvitations);
</script>

<style scoped>
  .invitation-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 20px 24px;
  }

  .invitation-page__tabbar {
    display: flex;
    flex-shrink: 0;
    align-items: flex-end;
    padding: 0 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
</style>
