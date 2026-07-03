<template>
  <el-dialog
    :model-value="modelValue"
    :title="activeShare ? '分享详情' : '分享文件/目录'"
    width="500"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div class="quick-share__file">{{ file?.originalName || "-" }}</div>

    <template v-if="activeShare">
      <div class="quick-share__line">
        <span class="quick-share__label">分享链接：</span>
        <span class="quick-share__link">{{ shareLink }}</span>
        <el-tooltip content="复制链接">
          <el-button
            link
            type="primary"
            icon="CopyDocument"
            @click="copyShareLink"
          />
        </el-tooltip>
      </div>
      <div class="quick-share__line">
        <span class="quick-share__label">提取码：</span>
        <span class="quick-share__code">{{ extractCodeText }}</span>
        <el-tooltip
          v-if="extractCodeText !== '-'"
          content="复制提取码"
        >
          <el-button
            link
            type="primary"
            icon="CopyDocument"
            @click="copyExtractCode"
          />
        </el-tooltip>
      </div>
    </template>

    <el-form
      v-else
      label-position="top"
      class="quick-share__form"
    >
      <el-form-item label="分享方式">
        <el-select v-model="form.accessType">
          <el-option
            label="公开"
            :value="0"
          />
          <el-option
            label="提取码"
            :value="1"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="有效期（天）">
        <el-input-number
          v-model="form.expireDays"
          :min="1"
          :max="365"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-if="activeShare"
        type="danger"
        @click="cancelShare"
      >
        取消分享
      </el-button>
      <el-button
        v-else
        type="primary"
        @click="createShare"
      >
        分享
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
  import type { FileInfoResponseVO as FileInfo } from "@/api/file.api";
  import ShareAPI, { type ShareInfoResponseVO } from "@/api/share.api";
  import TeamAPI from "@/api/team.api";
  import { buildShareLink, shareExtractCode } from "../../utils/share-display";

  const props = withDefaults(
    defineProps<{
      modelValue: boolean;
      file: FileInfo | null;
      existingShare?: ShareInfoResponseVO | null;
      scope?: "personal" | "team" | "private";
      teamId?: number;
    }>(),
    { scope: "personal" }
  );

  const emit = defineEmits<{
    (e: "update:modelValue", value: boolean): void;
    (e: "changed"): void;
  }>();

  const form = reactive({
    accessType: 0,
    expireDays: 7,
    code: "",
  });
  const createdShare = ref<ShareInfoResponseVO | null>(null);

  const activeShare = computed(() => props.existingShare || createdShare.value);

  const shareLink = computed(() => buildShareLink(activeShare.value?.shareToken));

  const extractCodeText = computed(() =>
    activeShare.value ? shareExtractCode(activeShare.value) : "-"
  );

  watch(
    () => props.modelValue,
    (visible) => {
      if (!visible) {
        createdShare.value = null;
        form.accessType = 0;
        form.expireDays = 7;
        form.code = "";
      }
    }
  );

  async function createShare() {
    const fileId = props.file?.id;
    if (!fileId) {
      ElMessage.warning("文件不存在");
      return;
    }
    try {
      if (props.scope === "team" && props.teamId) {
        createdShare.value = await TeamAPI.createTeamShare(props.teamId, {
          fileId,
          accessType: form.accessType,
          expireDays: form.expireDays,
        });
      } else {
        createdShare.value = await ShareAPI.createShare({
          fileId,
          accessType: form.accessType,
          expireDays: form.expireDays,
        });
      }
      ElMessage.success("创建分享成功");
      emit("changed");
    } catch {
      ElMessage.error("创建分享失败");
    }
  }

  async function cancelShare() {
    const shareId = activeShare.value?.id;
    if (!shareId) {
      ElMessage.warning("分享信息不存在");
      return;
    }

    try {
      await ElMessageBox.confirm("确定取消该分享吗？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      });
      if (props.scope === "team" && props.teamId) {
        await TeamAPI.cancelTeamShare(props.teamId, shareId);
      } else {
        await ShareAPI.cancelShare(shareId);
      }
      ElMessage.success("取消分享成功");
      emit("changed");
      emit("update:modelValue", false);
    } catch (error) {
      if (error === "cancel" || error === "close") return;
      ElMessage.error("取消分享失败");
    }
  }

  async function copyShareLink() {
    if (shareLink.value === "-") {
      ElMessage.warning("暂无可复制链接");
      return;
    }
    try {
      await navigator.clipboard.writeText(shareLink.value);
      ElMessage.success("链接已复制");
    } catch {
      ElMessage.error("复制失败");
    }
  }

  async function copyExtractCode() {
    if (extractCodeText.value === "-") {
      ElMessage.warning("暂无可复制提取码");
      return;
    }
    try {
      await navigator.clipboard.writeText(extractCodeText.value);
      ElMessage.success("提取码已复制");
    } catch {
      ElMessage.error("复制失败");
    }
  }
</script>

<style scoped>
  .quick-share__file {
    margin-bottom: 12px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    word-break: break-all;
  }

  .quick-share__line {
    display: flex;
    gap: 8px;
    align-items: center;
    min-width: 0;
    margin-bottom: 8px;
    line-height: 1.5;
  }

  .quick-share__label {
    flex-shrink: 0;
    color: var(--el-text-color-secondary);
  }

  .quick-share__link,
  .quick-share__code {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  .quick-share__link {
    color: var(--el-color-primary);
  }

  .quick-share__form {
    margin-top: 4px;
  }
</style>
