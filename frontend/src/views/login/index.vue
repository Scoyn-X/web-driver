<template>
  <div class="login-container">
    <div class="flex flex-1 items-center justify-center w-full p-6">
      <el-card
        style="width: min(860px, 92vw); overflow: hidden; border-radius: 12px"
        shadow="always"
      >
        <div class="card-inner">
          <!-- 左侧装饰区 -->
          <div class="card-left">
            <div class="text-center">
              <el-icon
                :size="48"
                color="var(--el-color-primary)"
              >
                <Monitor />
              </el-icon>
              <h2 class="mt-4 mb-2 text-2xl font-bold text-[var(--el-fill-color-blank)]">
                网盘管理系统
              </h2>
              <p class="m-0 text-sm text-[rgb(255,255,255,0.8)]">安全、便捷的个人云存储</p>
            </div>
          </div>
          <!-- 右侧表单区 -->
          <div class="card-right">
            <transition
              mode="out-in"
              name="fade-slide"
            >
              <Login
                v-if="currentForm === 'login'"
                key="login"
                @switch="switchForm"
              />
              <Register
                v-else
                key="register"
                @switch="switchForm"
              />
            </transition>
          </div>
        </div>
      </el-card>
    </div>
    <el-text class="py-3 text-xs text-[var(--el-text-color-secondary)]">
      Copyright &copy; 2025 复旦大学 计算与智能创新学院 All Rights Reserved.
    </el-text>
  </div>
</template>

<script setup lang="ts">
  import Login from "./components/Login.vue";
  import Register from "./components/Register.vue";

  const currentForm = ref<"login" | "register">("login");

  function switchForm(target: string) {
    currentForm.value = target as "login" | "register";
  }
</script>

<style scoped>
  .login-container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100vh;
    background: linear-gradient(
      135deg,
      var(--el-color-primary-light-9) 0%,
      var(--el-fill-color-lighter) 100%
    );
  }

  .card-inner {
    display: flex;
    flex-direction: row;
    min-height: 420px;
  }

  .card-left {
    display: flex;
    flex: 1 1 45%;
    align-items: center;
    justify-content: center;
    padding: 40px;
    background: linear-gradient(135deg, var(--el-color-primary) 0%, var(--el-color-primary) 100%);
    border-radius: 8px 0 0 8px;
  }

  .card-right {
    display: flex;
    flex: 1 1 55%;
    flex-direction: column;
    justify-content: center;
    padding: 40px 36px;
  }

  .fade-slide-leave-active,
  .fade-slide-enter-active {
    transition: all 0.3s;
  }

  .fade-slide-enter-from {
    opacity: 0;
    transform: translateX(-20px);
  }

  .fade-slide-leave-to {
    opacity: 0;
    transform: translateX(20px);
  }

  @media (width <= 640px) {
    .card-inner {
      flex-direction: column;
    }

    .card-left {
      padding: 24px;
      border-radius: 8px 8px 0 0;
    }

    .card-right {
      padding: 24px 20px;
    }
  }
</style>
