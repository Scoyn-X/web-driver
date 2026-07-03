<template>
  <div>
    <h3 class="form-title">用户登录</h3>
    <el-form
      ref="loginFormRef"
      :model="formData"
      :rules="loginRules"
      size="large"
    >
      <el-form-item prop="accountName">
        <el-input
          v-model.trim="formData.accountName"
          placeholder="请输入账户名"
        >
          <template #prefix>
            <el-icon><User /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item prop="password">
        <el-input
          v-model.trim="formData.password"
          type="password"
          placeholder="请输入密码"
          show-password
          @keyup.enter="handleLogin"
        >
          <template #prefix>
            <el-icon><Lock /></el-icon>
          </template>
        </el-input>
      </el-form-item>

      <el-form-item>
        <el-button
          class="w-full"
          type="primary"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form-item>
    </el-form>

    <div class="switch-form">
      <el-text size="default">还没有账号？</el-text>
      <el-link
        type="primary"
        :underline="false"
        @click="emit('switch', 'register')"
      >
        立即注册
      </el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
  import AuthAPI from "@/api/auth.api";
  import { useUserStore } from "@/store/user";
  import type { FormInstance } from "element-plus";

  const emit = defineEmits<{ switch: [target: string] }>();

  const router = useRouter();
  const route = useRoute();
  const userStore = useUserStore();

  const loginFormRef = ref<FormInstance>();
  const loading = ref(false);
  const formData = reactive({
    accountName: "",
    password: "",
  });

  const loginRules = {
    accountName: [{ required: true, message: "请输入账户名", trigger: "blur" }],
    password: [{ required: true, message: "请输入密码", trigger: "blur" }],
  };

  async function handleLogin() {
    const valid = await loginFormRef.value?.validate().catch(() => false);
    if (!valid) return;

    loading.value = true;
    try {
      const res = await AuthAPI.login({
        accountName: formData.accountName,
        password: formData.password,
      });
      userStore.setUserInfo(res);
      ElMessage.success("登录成功");
      const redirect = (route.query.redirect as string) || "/files";
      router.push(redirect);
    } catch {
      ElMessage.error("账户名或密码错误");
    } finally {
      loading.value = false;
    }
  }
</script>

<style scoped>
  .form-title {
    margin: 0 0 24px;
    font-size: 20px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    text-align: center;
  }

  .switch-form {
    display: flex;
    gap: 4px;
    align-items: center;
    justify-content: center;
    margin-top: 12px;
  }
</style>
