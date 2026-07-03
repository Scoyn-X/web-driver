<template>
  <div
    class="user-cell"
    :class="{ 'user-cell--inline': inline }"
  >
    <el-avatar
      v-if="showAvatar"
      :size="size"
    >
      {{ avatarChar }}
    </el-avatar>
    <div :class="inline ? 'flex-y-center gap-1' : 'flex flex-col'">
      <span
        class="user-cell__name"
        :class="inline ? '' : 'font-medium text-[var(--el-text-color-primary)]'"
      >
        {{ displayName }}
      </span>
      <span
        v-if="displayAccount"
        class="text-xs text-[var(--el-text-color-secondary)]"
      >
        @{{ displayAccount }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
  defineOptions({
    name: "UserCell",
    inheritAttrs: false,
  });

  const props = withDefaults(
    defineProps<{
      nickname?: string;
      accountName?: string;
      size?: number;
      showAvatar?: boolean;
      inline?: boolean;
    }>(),
    { size: 32, showAvatar: true }
  );

  const displayName = computed(() => props.nickname || props.accountName || "用户");
  const displayAccount = computed(() => (props.nickname ? props.accountName : ""));
  const avatarChar = computed(() => (displayName.value || "?").slice(0, 1).toUpperCase());
</script>

<style scoped>
  .user-cell {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .user-cell--inline {
    gap: 6px;
  }
</style>
