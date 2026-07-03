<template>
  <section class="team-detail">
    <header class="team-detail__header">
      <div class="team-detail__title-row">
        <el-button
          text
          icon="ArrowLeft"
          class="-ml-2"
          @click="goBack"
        >
          我的团队
        </el-button>
        <el-divider
          direction="vertical"
          class="team-detail__title-divider"
        />
        <div class="team-detail__title-content">
          <template v-if="team">
            <span class="team-detail__name">{{ team.name }}</span>
          </template>
          <el-skeleton
            v-else
            animated
            class="team-detail__title-skeleton"
          >
            <template #template>
              <el-skeleton-item
                variant="text"
                class="team-detail__title-skeleton-item"
              />
            </template>
          </el-skeleton>
        </div>
      </div>
      <div
        v-if="canLeaveTeam"
        class="team-detail__actions"
      >
        <el-button
          plain
          type="danger"
          icon="SwitchButton"
          @click="handleLeaveTeam"
        >
          退出团队
        </el-button>
      </div>
    </header>

    <div class="team-detail__tabbar">
      <ImmersiveTabs
        v-model="activeTab"
        :tabs="visibleTabs"
        @change="onTabChange"
      >
        <template #files>
          <el-icon class="mr-1"><Folder /></el-icon>
          文件
        </template>
        <template #members>
          <el-icon class="mr-1"><User /></el-icon>
          成员
        </template>
        <template #invitations>
          <el-icon class="mr-1"><Message /></el-icon>
          邀请
        </template>
        <template #shares>
          <el-icon class="mr-1"><Share /></el-icon>
          分享
        </template>
        <template #recycle>
          <el-icon class="mr-1"><Delete /></el-icon>
          回收站
        </template>
        <template #settings>
          <el-icon class="mr-1"><Setting /></el-icon>
          设置
        </template>
      </ImmersiveTabs>
    </div>

    <div class="team-detail__body">
      <template v-if="contextReady">
        <FileExplorer
          v-show="activeTab === 'files'"
          ref="fileExplorerRef"
          scope="team"
          :team-id="teamId"
          :quota-used="team?.quota?.usedSpace ?? team?.usedSpace ?? 0"
          :quota-total="team?.quota?.totalQuota ?? team?.totalQuota ?? 0"
          @changed="refresh"
        />
        <TeamMembersPanel
          v-show="activeTab === 'members'"
          ref="membersPanelRef"
          :team-id="teamId"
          @changed="refresh"
        />
        <TeamInvitationsPanel
          v-show="activeTab === 'invitations'"
          ref="invitationsPanelRef"
          :team-id="teamId"
          @changed="refresh"
        />
        <TeamSharesPanel
          v-show="activeTab === 'shares'"
          ref="sharePanelRef"
          :team-id="teamId"
          @changed="refresh"
        />
        <RecycleBinPanel
          v-show="activeTab === 'recycle'"
          ref="recyclePanelRef"
          scope="team"
          :team-id="teamId"
          @changed="refresh"
        />
        <TeamSettingsPanel
          v-show="activeTab === 'settings'"
          :team-id="teamId"
          @changed="refresh"
        />
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
  import ImmersiveTabs, { type ImmersiveTab } from "@/components/ImmersiveTab.vue";
  import TeamAPI from "@/api/team.api";
  import { useTeamStore } from "@/store/team";

  defineOptions({
    name: "TeamDetail",
    inheritAttrs: false,
  });

  import TeamMembersPanel from "./panels/TeamMembersPanel.vue";
  import TeamInvitationsPanel from "./panels/TeamInvitationsPanel.vue";
  import TeamSharesPanel from "./panels/TeamSharesPanel.vue";
  import TeamSettingsPanel from "./panels/TeamSettingsPanel.vue";

  type TeamTab = "files" | "members" | "invitations" | "shares" | "recycle" | "settings";
  type TeamTabItem = ImmersiveTab & { perm?: string | string[] };

  const route = useRoute();
  const router = useRouter();
  const teamStore = useTeamStore();

  const teamId = computed(() => Number(route.params.teamId));
  const team = computed(() => teamStore.detailsByTeamId[teamId.value] ?? null);
  const activeTab = ref<TeamTab>("files");
  const contextReady = ref(false);

  const fileExplorerRef = ref();
  const sharePanelRef = ref();
  const recyclePanelRef = ref();
  const invitationsPanelRef = ref();
  const membersPanelRef = ref();

  function onTabChange(name: string) {
    if (name === "files") {
      fileExplorerRef.value?.refresh();
    } else if (name === "shares") {
      sharePanelRef.value?.refresh();
    } else if (name === "recycle") {
      recyclePanelRef.value?.fetchRecycle();
    } else if (name === "invitations") {
      invitationsPanelRef.value?.refresh();
    } else if (name === "members") {
      membersPanelRef.value?.refresh();
    }
  }

  const tabs: TeamTabItem[] = [
    { name: "files", label: "文件", perm: "file:list" },
    {
      name: "members",
      label: "成员",
      perm: ["team:manage", "member:invite", "member:remove", "role:update"],
    },
    { name: "invitations", label: "邀请", perm: "member:invite" },
    { name: "shares", label: "分享", perm: "share:manage" },
    { name: "recycle", label: "回收站", perm: "trash:list" },
    { name: "settings", label: "设置", perm: ["team:manage", "owner:transfer", "team:dissolve"] },
  ];

  function hasTabPerm(required?: string | string[]) {
    if (!required) return true;
    if (hasPermission("*:*:*")) return true;
    return Array.isArray(required)
      ? required.some((perm) => hasPermission(perm))
      : hasPermission(required);
  }

  function hasPermission(permission: string) {
    const perms = teamStore.permsOf(teamId.value);
    return perms.includes("*:*:*") || perms.includes(permission);
  }

  const visibleTabs = computed(() => tabs.filter((tab) => hasTabPerm(tab.perm)));
  const canLeaveTeam = computed(() => {
    const currentTeam = team.value;
    return !!currentTeam && currentTeam.role !== "Owner" && hasPermission("file:list");
  });

  async function refresh(id = teamId.value) {
    if (!id) return;
    try {
      const detail = await TeamAPI.getTeamById(id);
      teamStore.setDetail(id, detail);
    } catch (error) {
      handleTeamLoadError(id);
      throw error;
    }
  }

  async function loadPermissions(id = teamId.value) {
    if (!id) return;
    try {
      const perm = await TeamAPI.getTeamPermissions(id);
      const perms =
        (perm as any).permissions ?? (perm as any).perms ?? (Array.isArray(perm) ? perm : []);
      teamStore.setPerms(id, perms);
    } catch (error) {
      handleTeamLoadError(id);
      throw error;
    }
  }

  async function loadTeamContext(id: number) {
    contextReady.value = false;
    await refresh(id);
    await loadPermissions(id);
    // 确保 activeTab 在权限加载后在 visibleTabs 中有效
    if (!visibleTabs.value.some((t) => t.name === activeTab.value)) {
      activeTab.value = "files";
    }
    contextReady.value = true;
    nextTick(() => onTabChange(activeTab.value));
  }

  function handleTeamLoadError(id: number) {
    teamStore.clearTeam(id);
    if (router.currentRoute.value.name !== "TeamList") {
      router.replace("/teams");
    }
  }

  watch(visibleTabs, (list) => {
    if (!list.length) return;
    if (!list.some((tab) => tab.name === activeTab.value)) {
      activeTab.value = (list.some((t) => t.name === "files") ? "files" : list[0].name) as TeamTab;
    }
  });

  function goBack() {
    router.push("/teams");
  }

  async function handleLeaveTeam() {
    try {
      await ElMessageBox.confirm("确认退出该团队？退出后你将立即失去访问权限。", "退出团队", {
        confirmButtonText: "退出",
        cancelButtonText: "取消",
        confirmButtonClass: "el-button--danger",
      });
      await TeamAPI.leaveTeam(teamId.value);
      ElMessage.success("已退出团队");
      teamStore.clearTeam(teamId.value);
      teamStore.teamsLoaded = false;
      router.push("/teams");
    } catch (error: any) {
      if (error === "cancel" || error === "close") return;
    }
  }

  watch(
    teamId,
    (id) => {
      if (!id) return;
      teamStore.clearTeam(id);
      loadTeamContext(id).catch(() => {
        // 错误提示由请求拦截器统一处理，这里只阻止旧团队缓存继续渲染。
      });
    },
    { immediate: true }
  );
</script>

<style scoped>
  .team-detail {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
    background: var(--el-fill-color-blank);
  }

  .team-detail__header {
    display: flex;
    flex-shrink: 0;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px 12px;
  }

  .team-detail__title-row {
    display: flex;
    gap: 8px;
    align-items: center;
    min-width: 0;
  }

  .team-detail__title-row :deep(.el-button) {
    display: inline-flex;
    align-items: center;
    height: 32px;
    line-height: 1;
  }

  .team-detail__title-divider {
    flex-shrink: 0;
    align-self: center;
    height: 20px;
    margin: 0;
  }

  .team-detail__title-content {
    display: flex;
    align-items: center;
    min-width: 0;
  }

  .team-detail__name {
    margin: 0;
    margin-left: 12px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 16px;
    font-weight: 600;
    line-height: 1;
    white-space: nowrap;
  }

  .team-detail__title-skeleton {
    display: block;
    width: 180px;
  }

  .team-detail__title-skeleton-item {
    width: 100%;
    height: 18px;
  }

  .team-detail__actions {
    display: flex;
    flex-shrink: 0;
    gap: 8px;
    align-items: center;
  }

  .team-detail__tabbar {
    display: flex;
    flex-shrink: 0;
    align-items: flex-end;
    padding: 0 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .team-detail__body {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
    overflow: auto;
  }
</style>
