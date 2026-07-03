<template>
  <div class="flex flex-col flex-1 min-h-0 p-6 gap-6 overflow-y-auto">
    <section class="max-w-[480px]">
      <h3 class="text-[15px] font-semibold mb-4">基本信息</h3>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
      >
        <el-form-item
          label="团队名称"
          prop="name"
        >
          <el-input
            v-model="form.name"
            :disabled="!canManageTeam"
          />
        </el-form-item>
        <el-form-item
          label="团队描述"
          prop="description"
        >
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :disabled="!canManageTeam"
          />
        </el-form-item>
        <el-form-item v-if="canManageTeam">
          <el-button
            v-hasPerm="['team:manage']"
            type="primary"
            :loading="saving"
            @click="handleSave"
          >
            保存修改
          </el-button>
        </el-form-item>
      </el-form>
    </section>

    <div
      v-if="hasDangerActions"
      class="h-[1px] bg-[var(--el-border-color-lighter)]"
    />

    <section
      v-if="hasDangerActions"
      class="max-w-[480px]"
    >
      <h3 class="text-[15px] font-semibold mb-4">危险操作</h3>
      <div class="flex flex-col gap-4">
        <div
          v-if="canTransferOwner"
          class="flex-x-between"
        >
          <div>
            <div class="text-[var(--el-text-color-primary)]">转让团队 Owner</div>
            <div class="text-[13px] text-[var(--el-text-color-secondary)]">
              将 Owner 角色转让给另一位团队成员
            </div>
          </div>
          <el-button
            v-hasPerm="['owner:transfer']"
            text
            type="danger"
            @click="transferVisible = true"
          >
            转让
          </el-button>
        </div>

        <div
          v-if="canDissolveTeam"
          class="flex-x-between"
        >
          <div>
            <div class="text-[var(--el-text-color-danger)] font-semibold">解散团队</div>
            <div class="text-[13px] text-[var(--el-text-color-secondary)]">
              解散后所有团队数据将被清除，此操作不可恢复
            </div>
          </div>
          <el-button
            v-hasPerm="['team:dissolve']"
            text
            type="danger"
            @click="dissolveVisible = true"
          >
            解散团队
          </el-button>
        </div>
      </div>
    </section>

    <TransferOwnerDialog
      v-model="transferVisible"
      :team-id="teamId"
      @transferred="emit('changed')"
    />
    <DissolveTeamDialog
      v-model="dissolveVisible"
      :team-id="teamId"
      @dissolved="onDissolved"
    />
  </div>
</template>

<script setup lang="ts">
  import type { FormRules } from "element-plus";
  import TeamAPI from "@/api/team.api";
  import { useTeamStore } from "@/store/team";
  import TransferOwnerDialog from "@/views/system/team/components/TransferOwnerDialog.vue";
  import DissolveTeamDialog from "@/views/system/team/components/DissolveTeamDialog.vue";

  defineOptions({
    name: "TeamSettings",
    inheritAttrs: false,
  });

  const props = defineProps<{
    teamId: number;
  }>();

  const emit = defineEmits<{
    (e: "changed"): void;
  }>();

  const teamStore = useTeamStore();
  const router = useRouter();
  const formRef = ref();
  const saving = ref(false);
  const transferVisible = ref(false);
  const dissolveVisible = ref(false);
  const canManageTeam = computed(() => {
    const perms = teamStore.permsOf(props.teamId);
    return perms.includes("*:*:*") || perms.includes("team:manage");
  });
  const canTransferOwner = computed(() => hasPermission("owner:transfer"));
  const canDissolveTeam = computed(() => hasPermission("team:dissolve"));
  const hasDangerActions = computed(() => canTransferOwner.value || canDissolveTeam.value);

  function hasPermission(permission: string) {
    const perms = teamStore.permsOf(props.teamId);
    return perms.includes("*:*:*") || perms.includes(permission);
  }

  const form = reactive({
    name: "",
    description: "",
  });

  function syncFormFromDetail() {
    if (!props.teamId) return;
    const detail = teamStore.detailsByTeamId[props.teamId];
    form.name = detail?.name ?? "";
    form.description = detail?.description ?? "";
  }

  const rules: FormRules = {
    name: [{ required: true, message: "请输入团队名称", trigger: "blur" }],
  };

  watch(
    () => teamStore.detailsByTeamId[props.teamId],
    () => {
      syncFormFromDetail();
    },
    { immediate: true }
  );

  async function handleSave() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
    saving.value = true;
    try {
      await TeamAPI.updateTeam(props.teamId, {
        name: form.name.trim(),
        description: form.description.trim(),
      });
      ElMessage.success("已保存");
      emit("changed");
    } finally {
      saving.value = false;
    }
  }

  function onDissolved() {
    teamStore.teamsLoaded = false;
    dissolveVisible.value = false;
    router.push("/teams");
  }
</script>
