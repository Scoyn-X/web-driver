<template>
  <section class="team-list-page">
    <header class="flex-x-between mb-5 flex-shrink-0 items-start gap-3">
      <div>
        <h2 class="m-0 text-xl font-semibold">我的团队</h2>
        <p class="mt-1.5 mb-0 text-[13px] text-[var(--el-text-color-secondary)]">
          查看你加入的团队，或创建一个新的协作空间。
        </p>
      </div>
      <div class="flex-y-center gap-2">
        <SwitchGroup
          v-model="viewMode"
          :options="viewOptions"
        >
          <template #grid>
            <el-tooltip content="网格">
              <el-icon><Grid /></el-icon>
            </el-tooltip>
          </template>
          <template #list>
            <el-tooltip content="列表">
              <el-icon><List /></el-icon>
            </el-tooltip>
          </template>
        </SwitchGroup>
        <el-button
          type="primary"
          icon="Plus"
          @click="createDialogVisible = true"
        >
          创建团队
        </el-button>
      </div>
    </header>

    <div
      v-loading="loading"
      class="min-h-[240px] flex-1 overflow-y-auto"
    >
      <el-empty
        v-if="!loading && teams.length === 0"
        description="你还没有加入任何团队"
      ></el-empty>

      <!-- 列表视图 -->
      <el-table
        v-else-if="viewMode === 'list'"
        :data="teams"
        height="100%"
      >
        <el-table-column
          prop="name"
          label="团队名称"
          min-width="180"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="font-semibold cursor-pointer hover:text-[var(--el-color-primary)]">
              {{ row.name }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          prop="role"
          label="我的角色"
          width="100"
          align="center"
          :formatter="(_r: any, _c: any, v: string) => (v ? roleLabel(v) : '-')"
        />
        <el-table-column
          prop="ownerName"
          label="Owner"
          width="120"
          align="center"
        />
        <el-table-column
          label="成员"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            {{ memberCounts[row.id] ?? "-" }}
          </template>
        </el-table-column>
        <el-table-column
          prop="createTime"
          label="创建时间"
          width="160"
          align="center"
          :formatter="(_r: any, _c: any, v: string) => (v ? formatTimestamp(v) : '-')"
        />
        <el-table-column
          label="操作"
          fixed="right"
          width="80"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="enterTeam(row.id)"
            >
              进入
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 网格视图 -->
      <div
        v-else
        class="team-grid"
      >
        <div
          v-for="team in teams"
          :key="team.id"
          class="team-item"
          @click="enterTeam(team.id)"
        >
          <div class="flex flex-col gap-1.5 flex-1 min-w-0">
            <span class="text-[15px] font-semibold truncate">{{ team.name }}</span>
            <div class="flex-y-center gap-3 text-[13px] text-[var(--el-text-color-secondary)]">
              <span>Owner：{{ team.ownerName }}</span>
              <span>{{ memberCounts[team.id] ?? "-" }} 名成员</span>
              <span>{{ formatTimestamp(team.createTime) }}</span>
            </div>
          </div>
          <div class="w-[160px] flex-shrink-0">
            <QuotaBar
              scope="team"
              :team-id="team.id"
              :used="team.quota?.usedSpace ?? team.usedSpace ?? 0"
              :total="team.quota?.totalQuota ?? team.totalQuota ?? 0"
              :stroke-width="12"
            />
          </div>
        </div>
      </div>
    </div>

    <CreateTeamDialog
      v-model="createDialogVisible"
      @created="onTeamCreated"
    />
  </section>
</template>

<script setup lang="ts">
  import TeamAPI from "@/api/team.api";
  import { useTeamStore } from "@/store/team";
  import { formatTimestamp } from "@/utils/format-time";
  import { roleLabel } from "@/views/system/team/team-tag";

  defineOptions({
    name: "TeamList",
    inheritAttrs: false,
  });

  const router = useRouter();
  const teamStore = useTeamStore();

  const teams = computed(() => teamStore.teams);
  const loading = ref(!teamStore.teamsLoaded);
  const createDialogVisible = ref(false);
  const viewMode = ref<"grid" | "list">("grid");
  const viewOptions = [{ value: "grid" as const }, { value: "list" as const }];
  const memberCounts = ref<Record<number, number>>({});

  async function fetchTeams() {
    loading.value = true;
    try {
      const res = await TeamAPI.listUserTeams();
      const list = Array.isArray(res) ? res : ((res as any).records ?? (res as any).list ?? []);
      teamStore.setTeams(list);
      const counts: Record<number, number> = {};
      await Promise.all(
        list.map(async (t: any) => {
          const members = await TeamAPI.listMembers(t.id);
          counts[t.id] = (members as any)?.length ?? 0;
        })
      );
      memberCounts.value = counts;
    } finally {
      loading.value = false;
    }
  }

  function enterTeam(teamId: number) {
    router.push(`/teams/${teamId}`);
  }

  function onTeamCreated() {
    createDialogVisible.value = false;
    fetchTeams();
  }

  fetchTeams();
</script>

<style scoped>
  .team-list-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 20px 24px;
  }

  .team-grid {
    display: flex;
    flex-direction: column;
    gap: 1px;
    border: 1px solid var(--el-border-color-lighter);
  }

  .team-item {
    display: flex;
    gap: 16px;
    align-items: center;
    padding: 14px 16px;
    cursor: pointer;
    background: var(--el-fill-color-blank);
    transition: background 0.1s;
  }

  .team-item:hover {
    background: var(--el-fill-color-light);
  }
</style>
