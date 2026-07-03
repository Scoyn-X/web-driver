<template>
  <div class="counter-page">
    <el-card class="counter-card">
      <template #header>
        <div class="flex-x-between">
          <span>访问计数器</span>
        </div>
      </template>
      <div class="counter-display">
        <span class="counter-number">{{ count }}</span>
        <span class="counter-label">次访问</span>
      </div>
      <div class="button-group">
        <el-button
          type="primary"
          :loading="incrementLoading"
          @click="increment"
        >
          访问 +1
        </el-button>
        <el-button
          :loading="refreshLoading"
          @click="refresh"
        >
          刷新
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
  import VisitAPI from "@/api/visit.api";

  const count = ref(0);
  const incrementLoading = ref(false);
  const refreshLoading = ref(false);

  onMounted(async () => {
    await fetchCount();
  });

  async function fetchCount() {
    count.value = await VisitAPI.getVisitCount();
  }

  async function increment() {
    incrementLoading.value = true;
    try {
      count.value = await VisitAPI.incrementVisitCount();
    } catch {
      ElMessage.error("操作失败");
    } finally {
      incrementLoading.value = false;
    }
  }

  async function refresh() {
    refreshLoading.value = true;
    try {
      await VisitAPI.resetVisitCount();
      await fetchCount();
    } catch {
      ElMessage.error("刷新失败");
    } finally {
      refreshLoading.value = false;
    }
  }
</script>

<style scoped>
  .counter-page {
    display: flex;
    align-items: flex-start;
    justify-content: center;
    padding: 20px;
  }
  .counter-card {
    width: 360px;
    text-align: center;
  }
  .counter-display {
    margin: 24px 0;
  }
  .counter-number {
    font-size: 48px;
    font-weight: bold;
    color: var(--el-color-primary);
  }
  .counter-label {
    margin-left: 8px;
    font-size: 16px;
    color: var(--el-text-color-secondary);
  }
  .button-group {
    display: flex;
    gap: 12px;
    justify-content: center;
  }
</style>
