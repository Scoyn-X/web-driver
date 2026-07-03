<!-- 分享管理面板：以标签页形式展示当前用户的全部文件分享 -->
<template>
  <div class="share-panel">
    <div class="share-panel__body">
      <el-table
        v-loading="loading"
        :data="shares"
        height="100%"
      >
        <el-table-column
          prop="fileName"
          label="文件名"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="flex gap-2 items-center">
              <FileIcon
                :type="resolveShareFileType(row)"
                :size="20"
              />
              <span class="file-link">{{ row.fileName || "-" }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="类型"
          width="100"
          align="center"
          :formatter="fmtType"
        />
        <el-table-column
          label="分享链接"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span>{{ buildShareLink(row.shareToken) }}</span>
            <el-tooltip content="复制链接">
              <el-button
                icon="CopyDocument"
                link
                type="primary"
                @click="copyLink(row)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column
          label="提取码"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ shareExtractCode(row) }}</span>
            <el-tooltip
              v-if="shareExtractCode(row) !== '-'"
              content="复制提取码"
            >
              <el-button
                icon="CopyDocument"
                link
                type="primary"
                @click="copyCode(row)"
              />
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column
          align="center"
          label="状态"
          width="110"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="shareStatusTagType(row)"
              effect="light"
            >
              {{ shareStatusLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="expireTime"
          label="到期时间"
          min-width="160"
          align="center"
          :formatter="fmtTime"
        />
        <el-table-column
          label="操作"
          fixed="right"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              icon="Close"
              @click="removeShare(row.id)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无分享" />
        </template>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
  import ShareAPI, { type ShareInfoResponseVO } from "@/api/share.api";
  import { formatTimestamp } from "@/utils/format-time";
  import {
    buildShareLink,
    formatShareFileType,
    resolveShareFileType,
    shareExtractCode,
    shareStatusLabel,
    shareStatusTagType,
  } from "../../utils/share-display";

  const emit = defineEmits<{
    (e: "changed"): void;
  }>();

  const loading = ref(false);
  const shares = ref<ShareInfoResponseVO[]>([]);

  async function fetchShares() {
    loading.value = true;
    try {
      shares.value = await ShareAPI.listMyShares();
    } finally {
      loading.value = false;
    }
  }

  async function removeShare(id: number) {
    try {
      await ElMessageBox.confirm("确定取消该分享吗？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      });
      await ShareAPI.cancelShare(id);
      ElMessage.success("取消分享成功");
      await fetchShares();
      emit("changed");
    } catch (error) {
      if (error === "cancel" || error === "close") return;
      ElMessage.error("取消分享失败");
    }
  }

  async function copyText(text: string, emptyTip: string, successTip: string) {
    if (text === "-") {
      ElMessage.warning(emptyTip);
      return;
    }
    try {
      await navigator.clipboard.writeText(text);
      ElMessage.success(successTip);
    } catch {
      ElMessage.error("复制失败");
    }
  }

  function copyLink(share: ShareInfoResponseVO) {
    copyText(buildShareLink(share.shareToken), "分享链接为空", "链接已复制");
  }

  function copyCode(share: ShareInfoResponseVO) {
    copyText(shareExtractCode(share), "暂无可复制提取码", "提取码已复制");
  }

  function fmtType(row: ShareInfoResponseVO) {
    return formatShareFileType(row);
  }

  function fmtTime(_row: ShareInfoResponseVO, _col: unknown, value: string) {
    return value ? formatTimestamp(value) : "-";
  }

  defineExpose({ fetchShares });
</script>

<style scoped>
  .share-panel {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
  }

  .share-panel__body {
    flex: 1;
    min-height: 0;
    padding: 4px 12px;
  }
</style>

<style>
  .file-manager .el-table,
  .share-panel .el-table {
    --el-table-border-color: transparent;
  }
  .file-manager .el-table__inner-wrapper::before,
  .share-panel .el-table__inner-wrapper::before {
    display: none;
  }
</style>
