<!-- 文件列表（表格视图）。与 FileGrid 共用一套行操作事件接口 -->
<template>
  <el-table
    ref="tableRef"
    v-loading="loading"
    class="file-table"
    :data="files"
    height="100%"
    @sort-change="(payload) => emit('sort-change', payload)"
    @selection-change="(rows) => emit('selection-change', rows)"
  >
    <el-table-column
      type="selection"
      width="48"
      align="center"
    />
    <el-table-column
      prop="originalName"
      label="文件名"
      min-width="240"
      sortable="custom"
      show-overflow-tooltip
    >
      <template #default="{ row }">
        <div class="flex gap-2 items-center">
          <FileIcon
            :type="resolveFileType(row)"
            :size="20"
          />
          <span
            v-if="isDirectory(row)"
            class="file-link file-link--dir"
            @click="emit('open-directory', row)"
          >
            {{ row.originalName }}
          </span>
          <span
            v-else
            class="file-link"
          >
            {{ row.originalName }}
          </span>
          <el-tag
            v-if="shareMap.has(row.id || 0)"
            size="small"
            type="primary"
            effect="plain"
            round
          >
            分享中
          </el-tag>
        </div>
      </template>
    </el-table-column>
    <el-table-column
      align="center"
      prop="type"
      label="类型"
      width="100"
      sortable="custom"
      :formatter="fmtType"
    />
    <el-table-column
      align="center"
      prop="createTime"
      label="修改时间"
      min-width="160"
      sortable="custom"
      :formatter="fmtTime"
    />
    <el-table-column
      align="center"
      prop="fileSize"
      label="大小"
      width="110"
      sortable="custom"
      :formatter="fmtSize"
    />
    <el-table-column
      align="center"
      label="操作"
      fixed="right"
      width="260"
    >
      <template #default="{ row }">
        <el-tooltip
          v-if="isDirectory(row) && canMove"
          content="重命名"
        >
          <el-button
            icon="Edit"
            link
            type="primary"
            @click="emit('rename', row)"
          />
        </el-tooltip>
        <el-tooltip
          v-if="!isDirectory(row) && canDownload"
          content="下载"
        >
          <el-button
            icon="Download"
            link
            type="primary"
            @click="emit('download', row)"
          />
        </el-tooltip>
        <el-tooltip
          v-if="canShare"
          :content="shareMap.has(row.id || 0) ? '取消分享' : '分享'"
        >
          <el-button
            :icon="shareMap.has(row.id || 0) ? 'Close' : 'Share'"
            link
            type="primary"
            @click="emit('share', row)"
          />
        </el-tooltip>
        <el-tooltip
          v-if="scope === 'team' && canTransferToPersonal"
          content="转存到个人"
        >
          <el-button
            icon="Upload"
            link
            type="primary"
            @click="emit('transfer-to-personal', row)"
          />
        </el-tooltip>
        <el-tooltip
          v-if="scope === 'personal'"
          content="上传到团队"
        >
          <el-button
            icon="Upload"
            link
            type="primary"
            @click="emit('transfer-to-team', row)"
          />
        </el-tooltip>
        <el-tooltip
          v-if="canMove"
          content="移动"
        >
          <el-button
            icon="Folder"
            link
            type="primary"
            @click="emit('move', row)"
          />
        </el-tooltip>
        <el-tooltip
          v-if="scope !== 'private' && canCopy"
          content="复制"
        >
          <el-button
            icon="DocumentCopy"
            link
            type="primary"
            @click="emit('copy', row)"
          />
        </el-tooltip>

        <el-tooltip
          v-if="canDelete"
          content="删除"
        >
          <el-button
            icon="Delete"
            link
            type="danger"
            @click="emit('delete', row)"
          />
        </el-tooltip>
      </template>
    </el-table-column>
    <template #empty>
      <el-empty description="暂无文件" />
    </template>
  </el-table>
</template>

<script setup lang="ts">
  import type { FileInfoResponseVO as FileInfo } from "@/api/file.api";
  import type { ShareInfoResponseVO } from "@/api/share.api";
  import { formatTimestamp } from "@/utils/format-time";
  import {
    formatFileSize,
    formatFileType,
    isDirectory,
    resolveFileType,
  } from "../../utils/file-display";

  withDefaults(
    defineProps<{
      files: FileInfo[];
      shareMap: Map<number, ShareInfoResponseVO>;
      loading: boolean;
      scope?: "personal" | "team" | "private";
      canDownload?: boolean;
      canShare?: boolean;
      canMove?: boolean;
      canCopy?: boolean;
      canTransferToPersonal?: boolean;
      canDelete?: boolean;
    }>(),
    {
      canDownload: true,
      canShare: true,
      canMove: true,
      canCopy: true,
      canTransferToPersonal: true,
      canDelete: true,
    }
  );

  const emit = defineEmits<{
    "sort-change": [payload: { prop?: string; order: "ascending" | "descending" | null }];
    "selection-change": [rows: FileInfo[]];
    download: [row: FileInfo];
    share: [row: FileInfo];
    move: [row: FileInfo];
    copy: [row: FileInfo];
    "transfer-to-personal": [row: FileInfo];
    "transfer-to-team": [row: FileInfo];
    rename: [row: FileInfo];
    delete: [row: FileInfo];
    "open-directory": [row: FileInfo];
  }>();

  const tableRef = ref<{ doLayout: () => void } | null>(null);

  function fmtType(row: FileInfo) {
    return formatFileType(row);
  }

  function fmtTime(_row: FileInfo, _col: unknown, value: string) {
    return formatTimestamp(value);
  }

  function fmtSize(row: FileInfo, _col: unknown, value: number) {
    return formatFileSize(row, value);
  }

  /** 供父组件在标签页切换回来时重新计算列宽 */
  function doLayout() {
    tableRef.value?.doLayout();
  }

  defineExpose({ doLayout });
</script>

<style scoped>
  .file-table {
    box-sizing: border-box;
    height: 100%;
    padding: 4px 12px;
    margin: 0;
    /* 沉浸式：移除表格的全部边框（外框线、表头与行分隔线） */
    --el-table-border-color: transparent;
  }

  .file-table :deep(.el-table__inner-wrapper)::before {
    display: none;
  }
</style>
