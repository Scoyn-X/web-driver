<template>
  <el-dialog
    :model-value="modelValue"
    title="创建团队"
    width="420px"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
    >
      <el-form-item
        label="团队名"
        prop="name"
      >
        <el-input
          v-model="form.name"
          maxlength="32"
          show-word-limit
          placeholder="例：项目协作组"
        />
      </el-form-item>
      <el-form-item
        label="描述"
        prop="description"
      >
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="120"
          show-word-limit
          placeholder="选填"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        @click="handleSubmit"
      >
        创建
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import type { FormInstance, FormRules } from "element-plus";
  import TeamAPI from "@/api/team.api";

  const props = defineProps<{ modelValue: boolean }>();
  const emit = defineEmits<{
    (e: "update:modelValue", v: boolean): void;
    (e: "created"): void;
  }>();

  const formRef = ref<FormInstance | null>(null);
  const submitting = ref(false);
  const form = reactive({ name: "", description: "" });

  const rules: FormRules = {
    name: [{ required: true, message: "团队名不能为空", trigger: "blur" }],
  };

  watch(
    () => props.modelValue,
    (visible) => {
      if (visible) {
        form.name = "";
        form.description = "";
        nextTick(() => formRef.value?.clearValidate());
      }
    }
  );

  async function handleSubmit() {
    if (!formRef.value) return;
    const valid = await formRef.value.validate().catch(() => false);
    if (!valid) return;
    submitting.value = true;
    try {
      await TeamAPI.createTeam({ name: form.name.trim(), description: form.description });
      ElMessage.success("团队创建成功");
      emit("created");
    } finally {
      submitting.value = false;
    }
  }
</script>
