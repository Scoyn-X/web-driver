<template>
  <el-dialog
    :model-value="modelValue"
    title="私密空间"
    width="400px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="mb-4 text-[13px] text-[var(--el-text-color-secondary)]">
      私密空间需要密码验证后才能访问
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="handleUnlock"
    >
      <el-form-item prop="password">
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请输入私密空间密码"
          show-password
          @keyup.enter="handleUnlock"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button
        type="primary"
        :loading="loading"
        @click="handleUnlock"
      >
        验证
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import type { FormRules } from "element-plus";
  import { PersonalAPI } from "@/api/personal.api";

  defineOptions({
    name: "PrivatePassword",
    inheritAttrs: false,
  });

  defineProps<{
    modelValue: boolean;
  }>();

  const emit = defineEmits<{
    (e: "update:modelValue", v: boolean): void;
    (e: "unlocked"): void;
  }>();

  const formRef = ref();
  const loading = ref(false);
  const form = reactive({ password: "" });

  const rules: FormRules = {
    password: [{ required: true, message: "请输入密码", trigger: "blur" }],
  };

  async function handleUnlock() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
    loading.value = true;
    try {
      await PersonalAPI.unlock({ password: form.password });
      ElMessage.success("验证通过");
      emit("unlocked");
      emit("update:modelValue", false);
    } catch {
      ElMessage.error("密码错误");
    } finally {
      loading.value = false;
    }
  }
</script>
