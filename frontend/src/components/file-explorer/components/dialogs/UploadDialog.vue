<template>
  <el-dialog
    :model-value="modelValue"
    title="上传文件"
    width="520"
    @update:model-value="(value) => emit('update:modelValue', value)"
    @close="resetState"
  >
    <div class="target-directory">
      <div class="target-directory__meta">
        <div class="target-directory__label">目标目录</div>
        <div class="target-directory__path">{{ targetDirectoryPath }}</div>
      </div>
      <el-button
        link
        type="primary"
        @click="showDirectorySelector = !showDirectorySelector"
      >
        {{ showDirectorySelector ? "收起" : "更改" }}
      </el-button>
    </div>

    <div
      v-if="showDirectorySelector"
      class="directory-selector"
    >
      <FolderSelector
        v-model="targetDirectoryId"
        :scope="scope"
        :team-id="teamId"
      />
    </div>
    <el-upload
      drag
      class="upload-box"
      :file-list="selectedFile ? [{ name: selectedFile.name }] : []"
      :auto-upload="false"
      :on-change="handleFileChange"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">
        将文件拖到此处，或
        <em>点击上传</em>
      </div>
    </el-upload>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :disabled="!selectedFile"
        :loading="uploading"
        @click="upload"
      >
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import type { UploadFile } from "element-plus";
  import FileAPI from "@/api/file.api";
  import TeamAPI from "@/api/team.api";
  import { PersonalAPI } from "@/api/personal.api";
  import { normalizeDirectoryId } from "../../utils/file-display";
  import FolderSelector from "../common/FolderSelector.vue";

  const props = withDefaults(
    defineProps<{
      modelValue: boolean;
      parentDirectoryId: number;
      scope?: "personal" | "team" | "private";
      teamId?: number;
    }>(),
    { scope: "personal" }
  );

  const emit = defineEmits<{
    (e: "update:modelValue", value: boolean): void;
    (e: "changed"): void;
  }>();

  const selectedFile = ref<File | null>(null);
  const uploading = ref(false);
  const targetDirectoryId = ref(0);
  const showDirectorySelector = ref(false);
  const rootLabel = computed(() =>
    props.scope === "team" ? "团队网盘" : props.scope === "private" ? "私密空间" : "我的网盘"
  );
  const targetDirectoryPath = ref(rootLabel.value);

  watch(
    () => props.modelValue,
    async (visible) => {
      if (visible) {
        targetDirectoryId.value = props.parentDirectoryId;
        showDirectorySelector.value = false;
        await refreshTargetDirectoryPath();
      } else {
        resetState();
      }
    }
  );

  watch(targetDirectoryId, () => {
    if (!props.modelValue) return;
    refreshTargetDirectoryPath();
  });

  function handleFileChange(uploadFile: UploadFile) {
    selectedFile.value = uploadFile.raw ?? null;
  }

  function resetState() {
    selectedFile.value = null;
    uploading.value = false;
    showDirectorySelector.value = false;
    targetDirectoryPath.value = rootLabel.value;
  }

  async function refreshTargetDirectoryPath() {
    const parentId = normalizeDirectoryId(targetDirectoryId.value);
    if (!parentId) {
      targetDirectoryPath.value = rootLabel.value;
      return;
    }
    try {
      let result: any;
      if (props.scope === "private") {
        result = await PersonalAPI.listPrivateFiles(parentId);
      } else if (props.scope === "team" && props.teamId) {
        result = await TeamAPI.listTeamFiles(props.teamId, parentId);
      } else {
        result = await FileAPI.listPersonalFiles(parentId);
      }
      const names = ((result as any).breadcrumb || [])
        .map((item: any) => item.name)
        .filter(Boolean);
      targetDirectoryPath.value = names.length
        ? `${rootLabel.value} / ${names.join(" / ")}`
        : rootLabel.value;
    } catch {
      targetDirectoryPath.value = rootLabel.value;
    }
  }

  const TEXT_MIME = new Set([
    "text/plain",
    "text/html",
    "text/css",
    "text/javascript",
    "text/csv",
    "application/json",
    "application/xml",
    "text/xml",
  ]);

  function ensureUtf8Charset(file: File): File {
    const mime = (file.type || "").toLowerCase();
    if (TEXT_MIME.has(mime) && !mime.includes("charset")) {
      return new File([file], file.name, {
        type: `${mime}; charset=utf-8`,
        lastModified: file.lastModified,
      });
    }
    if (
      !mime &&
      /\.(txt|html?|css|js|json|xml|csv|md|log|yml|yaml|ini|cfg|sh|py|java|ts|vue)$/i.test(
        file.name
      )
    ) {
      return new File([file], file.name, {
        type: "text/plain; charset=utf-8",
        lastModified: file.lastModified,
      });
    }
    return file;
  }

  async function upload() {
    if (!selectedFile.value) return;
    uploading.value = true;
    try {
      const file = ensureUtf8Charset(selectedFile.value);
      if (props.scope === "private") {
        await PersonalAPI.uploadPrivateFile(file, targetDirectoryId.value);
      } else if (props.scope === "team" && props.teamId) {
        await TeamAPI.uploadTeamFile(props.teamId, file, targetDirectoryId.value);
      } else {
        await FileAPI.uploadFile(file, targetDirectoryId.value);
      }
      ElMessage.success("上传成功");
      emit("changed");
      emit("update:modelValue", false);
    } catch {
      ElMessage.error("上传失败");
    } finally {
      uploading.value = false;
    }
  }
</script>

<style scoped>
  .target-directory {
    display: flex;
    gap: 12px;
    align-items: center;
    justify-content: space-between;
  }

  .target-directory__meta {
    min-width: 0;
  }

  .target-directory__label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .target-directory__path {
    margin-top: 4px;
    font-size: 14px;
    font-weight: 500;
    color: var(--el-text-color-primary);
    word-break: break-all;
  }

  .directory-selector {
    margin-top: 12px;
  }

  .upload-box {
    margin-top: 12px;
  }
</style>
