<template>
  <section class="private-space">
    <template v-if="state === 'loading'" />

    <template v-else-if="state === 'active'">
      <div
        v-if="isGrace"
        class="px-5 py-2 text-[13px] text-center text-[var(--el-color-warning)] bg-[var(--el-color-warning-light-9)]"
      >
        <el-icon class="mr-1"><Warning /></el-icon>
        {{ graceReminder }}
      </div>
      <header class="private-space__tabbar">
        <ImmersiveTabs
          v-model="activeTab"
          :tabs="tabs"
          @change="onTabChange"
        >
          <template #files>
            <el-icon class="mr-1"><Folder /></el-icon>
            我的文件
          </template>
          <template #recycle>
            <el-icon class="mr-1"><Delete /></el-icon>
            回收站
          </template>
        </ImmersiveTabs>
      </header>

      <FileExplorer
        v-show="activeTab === 'files'"
        ref="explorerRef"
        scope="private"
        :readonly="isGrace"
        :quota-used="info?.personalQuota?.usedSpace"
        :quota-total="info?.personalQuota?.totalQuota"
      />
      <RecycleBinPanel
        v-show="activeTab === 'recycle'"
        ref="recycleRef"
        scope="private"
        @changed="explorerRef?.refresh()"
      />
    </template>

    <div
      v-else
      class="private-placeholder"
    >
      <el-empty :description="placeholderText" />
      <el-button
        v-if="state === 'locked'"
        type="primary"
        @click="showPassword = true"
      >
        输入密码
      </el-button>
      <el-button
        v-if="state === 'disabled'"
        type="primary"
        @click="showSetup = true"
      >
        设置密码
      </el-button>
      <el-button
        v-if="state === 'forbidden'"
        type="primary"
        @click="router.push('/profile')"
      >
        开通 VIP
      </el-button>
    </div>

    <PrivatePasswordDialog
      v-model="showPassword"
      @unlocked="onUnlocked"
    />
    <PrivateSetupDialog
      v-model="showSetup"
      @done="onSetupDone"
    />
    <PrivateReminderDialog
      v-model="showReminder"
      :message="graceReminder"
    />
  </section>
</template>

<script setup lang="ts">
  import ImmersiveTabs from "@/components/ImmersiveTab.vue";
  import { PersonalAPI } from "@/api/personal.api";
  import UserAPI from "@/api/user.api";
  import PrivatePasswordDialog from "./components/PrivatePasswordDialog.vue";
  import PrivateSetupDialog from "./components/PrivateSetupDialog.vue";
  import PrivateReminderDialog from "./components/PrivateReminderDialog.vue";

  defineOptions({
    name: "PrivateSpace",
    inheritAttrs: false,
  });

  const router = useRouter();
  const tabs: { name: string }[] = [{ name: "files" }, { name: "recycle" }];

  const activeTab = ref("files");
  const explorerRef = ref();
  const recycleRef = ref();
  const showPassword = ref(false);
  const showSetup = ref(false);
  const showReminder = ref(false);
  const isVip = ref(false);
  const isGrace = ref(false);
  const graceReminder = ref("");
  const state = ref<"loading" | "active" | "locked" | "disabled" | "forbidden">("loading");
  const info = ref<any>(null);

  const placeholderText = computed(() => {
    switch (state.value) {
      case "locked":
        return "私密空间已锁定，请输入密码解锁";
      case "disabled":
        return "私密空间尚未开启，请先设置私密空间密码";
      case "forbidden":
        return graceReminder.value || "私密空间仅限 VIP 用户使用，请先开通 VIP";
      default:
        return "";
    }
  });

  async function checkStatus() {
    state.value = "loading";

    // Step 1: get user profile + private space status in parallel
    let profileRes: any;
    let statusRes: any;
    try {
      [profileRes, statusRes] = await Promise.all([
        UserAPI.getCurrentUser(),
        PersonalAPI.getStatus().catch(() => null),
      ]);
    } catch {
      state.value = "forbidden";
      return;
    }

    isVip.value = profileRes?.vipState === "VIP";
    info.value = profileRes;

    const profileReminder = profileRes?.privateSpaceReminder;
    if (profileReminder) {
      graceReminder.value = profileReminder;
      showReminder.value = true;
    }

    const s = statusRes?.state;

    // Step 2: GRACE_PERIOD → readonly regardless of VIP status
    if (s === "GRACE_PERIOD") {
      isGrace.value = true;
      graceReminder.value =
        statusRes.reminderMessage ||
        "VIP 已过期，请尽快处理私密空间文件。当前仅可查看下载，不可上传。";
      showReminder.value = true;
      state.value = "active";
      return;
    }

    // Step 3: non-VIP (and not in grace) → forbidden
    if (!isVip.value) {
      state.value = "forbidden";
      return;
    }

    // Step 4: VIP → handle remaining states
    if (!statusRes) {
      state.value = "locked";
      return;
    }

    if (s === "EXPIRED") {
      state.value = "forbidden";
      graceReminder.value = statusRes.reminderMessage || "私密空间已过期，请重新开通 VIP";
      showReminder.value = true;
      return;
    }

    const stateMap: Record<string, typeof state.value> = {
      ACTIVE: "active",
      LOCKED: "locked",
      DISABLED: "disabled",
    };
    state.value = stateMap[s] || "locked";
  }

  function onUnlocked() {
    state.value = "active";
  }

  function onSetupDone() {
    state.value = "locked";
  }

  function onTabChange(name: string) {
    if (name === "recycle") {
      recycleRef.value?.fetchRecycle();
    } else {
      explorerRef.value?.activate();
    }
  }

  onMounted(checkStatus);
</script>

<style scoped>
  .private-space {
    display: flex;
    flex-direction: column;
    width: 100%;
    height: 100%;
    background: var(--el-fill-color-blank);
  }

  .private-space__tabbar {
    display: flex;
    flex-shrink: 0;
    align-items: flex-end;
    padding: 6px 12px 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .private-placeholder {
    display: flex;
    flex: 1;
    flex-direction: column;
    gap: 12px;
    align-items: center;
    justify-content: center;
  }
</style>
