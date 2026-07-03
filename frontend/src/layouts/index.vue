<template>
  <div class="app-layout">
    <Header />
    <div class="main-container">
      <Sidebar />
      <Main />
    </div>
  </div>
</template>

<script setup lang="ts">
  import Header from "./components/Header/index.vue";
  import Sidebar from "./components/Sidebar/index.vue";
  import Main from "./components/Main/index.vue";
</script>

<style scoped>
  .app-layout {
    display: flex;
    flex-direction: column;
    height: 100vh;
  }

  .main-container {
    display: flex;
    flex: 1;
    overflow: hidden;
  }
</style>
