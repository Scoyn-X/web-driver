<template>
  <el-dialog
    :model-value="modelValue"
    title="设置私密空间密码"
    width="400px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="mb-4 text-[13px] text-[var(--el-text-color-secondary)]">
      首次使用私密空间，请设置密码
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
    >
      <el-form-item
        label="密码"
        prop="password"
      >
        <el-input
          v-model="form.password"
          type="password"
          placeholder="请设置至少 4 位密码"
          show-password
        />
      </el-form-item>
      <el-form-item
        label="确认密码"
        prop="confirm"
      >
        <el-input
          v-model="form.confirm"
          type="password"
          placeholder="请再次输入密码"
          show-password
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button
        type="primary"
        :loading="loading"
        @click="handleSetup"
      >
        设置
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import type { FormRules } from "element-plus";
  import { PersonalAPI } from "@/api/personal.api";

  defineOptions({
    name: "PrivateSetup",
    inheritAttrs: false,
  });

  defineProps<{
    modelValue: boolean;
  }>();

  const emit = defineEmits<{
    (e: "update:modelValue", v: boolean): void;
    (e: "done"): void;
  }>();

  const formRef = ref();
  const loading = ref(false);
  const form = reactive({ password: "", confirm: "" });

  const validateConfirm = (_rule: any, value: string, callback: any) => {
    if (value !== form.password) {
      callback(new Error("两次密码输入不一致"));
    } else {
      callback();
    }
  };

  const rules: FormRules = {
    password: [
      { required: true, message: "请输入密码", trigger: "blur" },
      { min: 4, message: "密码长度不能少于 4 位", trigger: "blur" },
    ],
    confirm: [
      { required: true, message: "请再次输入密码", trigger: "blur" },
      { validator: validateConfirm, trigger: "blur" },
    ],
  };

  async function handleSetup() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
    loading.value = true;
    try {
      await PersonalAPI.updatePassword({ password: form.password });
      ElMessage.success("密码设置成功");
      emit("done");
      emit("update:modelValue", false);
    } catch {
      ElMessage.error("设置失败");
    } finally {
      loading.value = false;
    }
  }
</script>
