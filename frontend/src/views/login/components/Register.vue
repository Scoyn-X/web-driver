<template>
  <div>
    <h3 class="form-title">用户注册</h3>
    <el-form
      ref="registerFormRef"
      :model="formData"
      :rules="registerRules"
      size="large"
      label-position="top"
    >
      <el-form-item
        label="昵称"
        prop="nickname"
      >
        <el-input
          v-model.trim="formData.nickname"
          placeholder="请输入昵称"
        />
      </el-form-item>

      <el-form-item
        label="账户名"
        prop="accountName"
      >
        <el-input
          v-model.trim="formData.accountName"
          placeholder="2-20位，中英文/数字/下划线"
        />
      </el-form-item>

      <el-form-item
        label="密码"
        prop="password"
      >
        <el-input
          v-model.trim="formData.password"
          type="password"
          placeholder="至少8位，须包含英文和数字"
          show-password
        />
      </el-form-item>

      <el-form-item
        label="确认密码"
        prop="confirmPassword"
      >
        <el-input
          v-model.trim="formData.confirmPassword"
          type="password"
          placeholder="请再次输入密码"
          show-password
        />
      </el-form-item>

      <el-form-item
        label="邮箱"
        prop="email"
      >
        <el-input
          v-model.trim="formData.email"
          placeholder="请输入邮箱"
        />
      </el-form-item>

      <el-form-item
        label="账户类型"
        prop="accountType"
      >
        <el-select
          v-model="formData.accountType"
          placeholder="请选择账户类型"
          style="width: 100%"
        >
          <el-option
            label="个人"
            value="personal"
          />
          <el-option
            label="工作"
            value="work"
          />
          <el-option
            label="团队"
            value="team"
          />
        </el-select>
      </el-form-item>

      <el-form-item>
        <el-button
          class="w-full"
          type="primary"
          :loading="loading"
          @click="handleRegister"
        >
          注册
        </el-button>
      </el-form-item>
    </el-form>

    <div class="switch-form">
      <el-text size="default">已有账号？</el-text>
      <el-link
        type="primary"
        :underline="false"
        @click="emit('switch', 'login')"
      >
        返回登录
      </el-link>
    </div>
  </div>
</template>

<script setup lang="ts">
  import AuthAPI from "@/api/auth.api";
  import type { FormInstance, FormRules } from "element-plus";

  const emit = defineEmits<{ switch: [target: string] }>();

  const registerFormRef = ref<FormInstance>();
  const loading = ref(false);

  const formData = reactive({
    nickname: "",
    accountName: "",
    password: "",
    confirmPassword: "",
    email: "",
    accountType: "personal",
  });

  /** 账户名校验：2-20位，仅中英文/数字/下划线 */
  function validateAccountName(_rule: any, value: string, callback: any) {
    if (!value) return callback(new Error("请输入账户名"));
    if (!/^[\u4e00-\u9fa5a-zA-Z0-9_]{2,20}$/.test(value)) {
      return callback(new Error("2-20位，仅支持中英文、数字、下划线"));
    }
    callback();
  }

  /** 密码校验：≥8位，仅英文/数字/下划线，必须同时包含英文和数字 */
  function validatePassword(_rule: any, value: string, callback: any) {
    if (!value) return callback(new Error("请输入密码"));
    if (!/^[a-zA-Z0-9_]{8,}$/.test(value)) {
      return callback(new Error("至少8位，仅支持英文、数字、下划线"));
    }
    if (!/[a-zA-Z]/.test(value) || !/[0-9]/.test(value)) {
      return callback(new Error("必须同时包含英文字符和数字"));
    }
    callback();
  }

  /** 确认密码校验 */
  function validateConfirmPassword(_rule: any, value: string, callback: any) {
    if (!value) return callback(new Error("请再次输入密码"));
    if (value !== formData.password) {
      return callback(new Error("两次输入的密码不一致"));
    }
    callback();
  }

  const registerRules: FormRules = {
    nickname: [{ required: true, message: "请输入昵称", trigger: "blur" }],
    accountName: [{ required: true, validator: validateAccountName, trigger: "blur" }],
    password: [{ required: true, validator: validatePassword, trigger: "blur" }],
    confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: "blur" }],
    email: [
      { required: true, message: "请输入邮箱", trigger: "blur" },
      { type: "email", message: "请输入正确的邮箱格式", trigger: "blur" },
    ],
    accountType: [{ required: true, message: "请选择账户类型", trigger: "change" }],
  };

  async function handleRegister() {
    const valid = await registerFormRef.value?.validate().catch(() => false);
    if (!valid) return;

    loading.value = true;
    try {
      await AuthAPI.register({
        nickname: formData.nickname,
        accountName: formData.accountName,
        password: formData.password,
        email: formData.email,
        accountType: formData.accountType,
      });
      ElMessage.success("注册成功，请登录");
      emit("switch", "login");
    } catch {
      // 错误信息由响应拦截器统一处理
    } finally {
      loading.value = false;
    }
  }
</script>

<style scoped>
  .form-title {
    margin: 0 0 20px;
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
