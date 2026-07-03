<template>
  <div class="logo">
    <el-icon
      :size="28"
      color="var(--el-color-primary)"
    >
      <Monitor />
    </el-icon>
    <span class="app-title">网盘管理系统</span>
  </div>
</template>

<style scoped>
  .logo {
    display: flex;
    gap: 10px;
    align-items: center;
  }

  .app-title {
    font-size: 18px;
    font-weight: bold;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }
</style>
