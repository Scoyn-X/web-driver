<template>
  <div class="navbar">
    <Logo />
    <UserDropdown />
  </div>
</template>

<script setup lang="ts">
  import Logo from "./Logo.vue";
  import UserDropdown from "./UserDropdown.vue";
</script>

<style scoped>
  .navbar {
    display: flex;
    flex-shrink: 0;
    align-items: center;
    justify-content: space-between;
    height: 56px;
    padding: 0 20px;
    background-color: var(--el-fill-color-blank);
    border-bottom: 1px solid var(--el-border-color-light);
  }
</style>
