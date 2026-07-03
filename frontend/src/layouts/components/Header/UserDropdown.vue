<template>
  <el-popover
    :width="240"
    placement="bottom-end"
    trigger="click"
    popper-class="user-popover"
    :offset="10"
  >
    <template #reference>
      <el-avatar
        class="user-trigger"
        :size="36"
      >
        {{ avatarText }}
      </el-avatar>
    </template>

    <div class="profile-card">
      <div class="profile-header">
        <el-avatar :size="52">
          {{ avatarText }}
        </el-avatar>
        <div class="flex flex-col gap-[4px] min-w-0">
          <div class="profile-nickname">{{ userStore.displayName }}</div>
          <div
            v-if="userStore.accountName"
            class="profile-account"
          >
            @{{ userStore.accountName }}
          </div>
        </div>
      </div>

      <div class="profile-divider" />

      <button
        type="button"
        class="profile-action is-danger"
        @click="handleLogout"
      >
        <el-icon><SwitchButton /></el-icon>
        <span>退出登录</span>
      </button>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
  import { useUserStore } from "@/store/user";

  const router = useRouter();
  const userStore = useUserStore();

  const avatarText = computed(() => {
    const name = userStore.displayName || "U";
    return name[0].toUpperCase();
  });

  async function handleLogout() {
    userStore.clearUserInfo();
    router.push("/login");
  }
</script>

<style scoped>
  .user-trigger {
    font-weight: 600;
    color: var(--el-color-primary);
    cursor: pointer;
    background: var(--el-color-primary-light-7);
    transition:
      box-shadow 0.2s,
      transform 0.2s;
  }

  .user-trigger:hover {
    box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
    transform: translateY(-1px);
  }

  .profile-card {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 4px 0;
  }

  .profile-header {
    display: flex;
    gap: 12px;
    align-items: center;
    padding: 8px 4px 12px;
  }
</style>

<style scoped>
  .profile-nickname {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  .profile-account {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
  }

  .profile-divider {
    height: 1px;
    margin: 0 -12px 4px;
    background: var(--el-border-color-lighter);
  }

  .profile-action {
    display: flex;
    gap: 10px;
    align-items: center;
    width: 100%;
    padding: 8px 10px;
    font-size: 14px;
    color: var(--el-text-color-regular);
    text-align: left;
    cursor: pointer;
    background: transparent;
    border: none;
    border-radius: 6px;
    transition: background-color 0.15s;
  }

  .profile-action:hover {
    background: var(--el-fill-color-light);
  }

  .profile-action.is-danger {
    color: var(--el-color-danger);
  }

  .profile-action.is-danger:hover {
    background: var(--el-color-danger-light-9);
  }
</style>

<style>
  .user-popover.el-popover.el-popper {
    padding: 12px;
    border-radius: 10px;
  }
</style>
