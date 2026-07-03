<template>
  <section class="profile-page">
    <header class="mb-6">
      <h2 class="m-0 text-xl font-semibold">个人中心</h2>
    </header>

    <div
      v-loading="loading"
      class="profile-content"
    >
      <div class="profile-card">
        <el-avatar
          :size="64"
          class="text-[28px]"
        >
          {{ avatarChar }}
        </el-avatar>
        <div>
          <div class="text-lg font-semibold">{{ info?.nickname ?? "-" }}</div>
          <div class="text-[13px] text-[var(--el-text-color-secondary)]">
            @{{ info?.accountName ?? "-" }}
          </div>
        </div>
      </div>

      <div class="h-[1px] bg-[var(--el-border-color-lighter)]" />

      <div class="flex flex-col gap-3">
        <div class="flex-x-between">
          <span class="info-label">邮箱</span>
          <span class="info-value">{{ info?.email || "-" }}</span>
        </div>
        <div class="flex-x-between">
          <span class="info-label">VIP 状态</span>
          <span class="info-value flex-y-center gap-2">
            <el-tag
              :type="info?.vipState === 'VIP' ? 'warning' : 'info'"
              size="small"
            >
              {{ info?.vipState === "VIP" ? "VIP" : "普通用户" }}
            </el-tag>
            <el-button
              text
              size="small"
              :loading="vipLoading"
              @click="toggleVip"
            >
              {{ info?.vipState === "VIP" ? "取消 VIP" : "开通 VIP" }}
            </el-button>
          </span>
        </div>
      </div>

      <div class="h-[1px] bg-[var(--el-border-color-lighter)]" />

      <div>
        <div class="text-[15px] font-semibold mb-3">个人配额</div>
        <QuotaBar
          :used="info?.personalQuota?.usedSpace ?? 0"
          :total="info?.personalQuota?.totalQuota ?? 0"
          :stroke-width="16"
        />
        <div class="flex-x-between mt-2 text-[13px] text-[var(--el-text-color-secondary)]">
          <span>已用 {{ info?.personalQuota?.usedSpaceFormatted ?? "-" }}</span>
          <span>总额 {{ info?.personalQuota?.totalQuotaFormatted ?? "-" }}</span>
        </div>
      </div>

      <template v-if="info?.privateSpaceReminder">
        <div class="h-[1px] bg-[var(--el-border-color-lighter)]" />
        <div class="text-[13px] text-[var(--el-text-color-warning)]">
          {{ info.privateSpaceReminder }}
        </div>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
  import UserAPI from "@/api/user.api";
  import { useUserStore } from "@/store/user";

  defineOptions({
    name: "UserProfile",
    inheritAttrs: false,
  });

  const info = ref<any>();
  const loading = ref(false);
  const vipLoading = ref(false);
  const userStore = useUserStore();

  const avatarChar = computed(() => {
    const name = info.value?.nickname || "U";
    return name[0].toUpperCase();
  });

  async function fetchProfile() {
    loading.value = true;
    try {
      info.value = await UserAPI.getCurrentUser();
    } finally {
      loading.value = false;
    }
  }

  async function toggleVip() {
    const currentVip = info.value?.vipState === "VIP";
    vipLoading.value = true;
    try {
      await UserAPI.updateVip(userStore.userId!, { vip: !currentVip });
      ElMessage.success(currentVip ? "已取消 VIP" : "已开通 VIP");
      await fetchProfile();
    } finally {
      vipLoading.value = false;
    }
  }

  onMounted(fetchProfile);
</script>

<style scoped>
  .profile-page {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 20px 24px;
    overflow-y: auto;
  }

  .profile-content {
    display: flex;
    flex-direction: column;
    gap: 16px;
    max-width: 480px;
  }

  .profile-card {
    display: flex;
    gap: 16px;
    align-items: center;
  }

  .info-label {
    font-size: 14px;
    color: var(--el-text-color-secondary);
  }

  .info-value {
    font-size: 14px;
  }
</style>
