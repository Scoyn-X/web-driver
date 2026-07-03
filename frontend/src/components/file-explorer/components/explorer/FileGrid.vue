<template>
  <div
    v-loading="loading"
    class="min-h-0 px-3 pt-2 pb-3"
  >
    <el-scrollbar class="wh-full">
      <div
        v-if="files.length"
        class="explorer-grid-list"
      >
        <article
          v-for="row in files"
          :key="row.id"
          class="windows-tile"
          :class="{ 'is-selected': isSelected(row), 'is-dir': isDirectory(row) }"
          @click="toggleGridSelection(row)"
        >
          <div class="tile-check">
            <el-checkbox
              :model-value="isSelected(row)"
              @click.stop
              @change="(checked) => setGridSelection(row, checked)"
            />
          </div>

          <div
            class="tile-body"
            @dblclick="isDirectory(row) ? emit('open-directory', row) : undefined"
          >
            <FileIcon
              :type="resolveFileType(row)"
              :size="52"
            />
            <span
              v-if="isDirectory(row)"
              class="file-link tile-name"
              @click.stop="emit('open-directory', row)"
            >
              {{ row.originalName }}
            </span>
            <span
              v-else
              class="file-link tile-name"
            >
              {{ row.originalName }}
            </span>
          </div>

          <el-dropdown
            v-if="hasRowActions(row)"
            class="tile-more"
            trigger="click"
            placement="bottom-end"
            @command="(command) => emitGridCommand(command as GridCommand, row)"
          >
            <button
              type="button"
              class="tile-more__btn"
              @click.stop
            >
              <el-icon><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-if="!isDirectory(row) && canDownload"
                  command="download"
                  icon="Download"
                >
                  下载
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="canShare"
                  command="share"
                  :icon="isShared(row) ? 'Close' : 'Share'"
                >
                  {{ isShared(row) ? "取消分享" : "分享" }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="canMove"
                  command="move"
                  icon="Folder"
                >
                  移动
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="scope !== 'private' && canCopy"
                  command="copy"
                  icon="DocumentCopy"
                >
                  复制
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="scope === 'team' && canTransferToPersonal"
                  command="transfer-to-personal"
                  icon="Upload"
                >
                  转存到个人
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="scope === 'personal'"
                  command="transfer-to-team"
                  icon="Upload"
                >
                  上传到团队
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="isDirectory(row) && canMove"
                  command="rename"
                  icon="Edit"
                >
                  重命名
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="canDelete"
                  command="delete"
                  icon="Delete"
                  divided
                >
                  删除
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </article>
      </div>
      <el-empty
        v-else
        class="py-6"
        description="暂无文件"
      />
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
  import type { FileInfoResponseVO as FileInfo } from "@/api/file.api";
  import type { ShareInfoResponseVO } from "@/api/share.api";
  import { isDirectory, resolveFileType } from "../../utils/file-display";

  /** 单行操作指令，与下方 emit 事件名一一对应 */
  type GridCommand =
    | "download"
    | "share"
    | "move"
    | "copy"
    | "transfer-to-personal"
    | "transfer-to-team"
    | "rename"
    | "delete";

  const props = defineProps<{
    files: FileInfo[];
    selectedRows: FileInfo[];
    shareMap: Map<number, ShareInfoResponseVO>;
    loading: boolean;
    scope?: "personal" | "team" | "private";
    canDownload?: boolean;
    canShare?: boolean;
    canMove?: boolean;
    canCopy?: boolean;
    canTransferToPersonal?: boolean;
    canDelete?: boolean;
  }>();

  const emit = defineEmits<{
    "update:selectedRows": [rows: FileInfo[]];
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

  function isSelected(file: FileInfo) {
    return props.selectedRows.some((item) => item.id === file.id);
  }

  function setGridSelection(file: FileInfo, selected: string | number | boolean) {
    const checked = Boolean(selected);
    if (checked) {
      if (isSelected(file)) return;
      emit("update:selectedRows", [...props.selectedRows, file]);
      return;
    }
    emit(
      "update:selectedRows",
      props.selectedRows.filter((item) => item.id !== file.id)
    );
  }

  function toggleGridSelection(file: FileInfo) {
    setGridSelection(file, !isSelected(file));
  }

  function isShared(file: FileInfo) {
    return props.shareMap.has(file.id || 0);
  }

  function emitGridCommand(command: GridCommand, row: FileInfo) {
    switch (command) {
      case "download":
        emit("download", row);
        break;
      case "share":
        emit("share", row);
        break;
      case "move":
        emit("move", row);
        break;
      case "copy":
        emit("copy", row);
        break;
      case "transfer-to-personal":
        emit("transfer-to-personal", row);
        break;
      case "transfer-to-team":
        emit("transfer-to-team", row);
        break;
      case "rename":
        emit("rename", row);
        break;
      case "delete":
        emit("delete", row);
        break;
    }
  }

  function hasRowActions(row: FileInfo) {
    if (!isDirectory(row) && props.canDownload) return true;
    if (props.canShare) return true;
    if (props.canMove) return true;
    if (props.scope !== "private" && props.canCopy) return true;
    if (props.scope === "team" && props.canTransferToPersonal) return true;
    if (isDirectory(row) && props.canMove) return true;
    return Boolean(props.canDelete);
  }
</script>

<style scoped>
  .explorer-grid-list {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(104px, 104px));
    gap: 6px;
    align-content: start;
    justify-content: start;
    padding: 4px;
  }

  .windows-tile {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-start;
    min-height: 112px;
    padding: 6px;
    cursor: pointer;
    background: transparent;
    border: 1px solid transparent;
    border-radius: 4px;
    transition:
      background-color 0.15s ease,
      border-color 0.15s ease;
  }

  .windows-tile:hover {
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-8);
  }

  .windows-tile.is-selected {
    background: var(--el-color-primary-light-8);
    border-color: var(--el-color-primary-light-5);
  }
</style>

<style scoped>
  .tile-check {
    position: absolute;
    top: 4px;
    left: 4px;
    opacity: 0;
    transition: opacity 0.15s ease;
  }

  .windows-tile:hover .tile-check,
  .windows-tile.is-selected .tile-check {
    opacity: 1;
  }

  .tile-body {
    display: flex;
    flex-direction: column;
    gap: 6px;
    align-items: center;
    width: 100%;
    margin-top: 8px;
  }

  .tile-name {
    display: -webkit-box;
    max-width: 92px;
    overflow: hidden;
    text-overflow: ellipsis;
    line-clamp: 2;
    font-size: 12px;
    line-height: 1.25;
    text-align: center;
  }

  .tile-more {
    position: absolute;
    top: 4px;
    right: 4px;
    opacity: 0;
    transition: opacity 0.15s ease;
  }

  .windows-tile:hover .tile-more,
  .windows-tile.is-selected .tile-more {
    opacity: 1;
  }

  .tile-more__btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    color: var(--el-text-color-regular);
    cursor: pointer;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-light);
    border-radius: 6px;
    transition:
      color 0.15s ease,
      border-color 0.15s ease;
  }

  .tile-more__btn:hover {
    color: var(--el-color-primary);
    border-color: var(--el-color-primary-light-5);
  }
</style>
