<!-- “我的文件”标签页：文件浏览器主体，含工具栏、目录树、列表/图标视图与相关弹窗 -->
<template>
  <div class="file-explorer">
    <ExplorerHeader
      v-model:keyword="keyword"
      v-model:view-mode="viewMode"
      :can-go-back="canGoBack"
      :can-go-forward="canGoForward"
      :scope="scope"
      :team-id="teamId"
      :active-directory-trail="activeDirectoryTrail"
      :root-label="rootLabel"
      :quota-used="quotaUsed"
      :quota-total="quotaTotal"
      :readonly="readonly || !canUpload"
      :selected-count="selectedRows.length"
      :refresh-trigger="refreshTrigger"
      @go-back="goBack"
      @go-forward="goForward"
      @navigate-directory="navigateDirectory"
      @search="fetchFiles"
      @upload="showUpload = true"
      @create-dir="showCreateDir = true"
      @transfer-to-team="openTransferToTeamFromSelection"
      @refresh="handleChanged"
    />

    <div
      class="file-explorer__main"
      :style="{ gridTemplateColumns: treeWidth + 'px 1fr' }"
    >
      <aside class="file-explorer__tree">
        <el-scrollbar class="wh-full tree-scrollbar">
          <FileTree
            :key="treeVersion"
            :active-directory-id="activeDirectoryId"
            :active-trail-ids="activeTrailIds"
            :scope="scope"
            :team-id="teamId"
            @change-directory="navigateDirectory"
          />
        </el-scrollbar>
        <ResizeHandle
          v-model:width="treeWidth"
          v-model:dragging="isTreeDragging"
          :min="180"
          :max="480"
        />
      </aside>

      <FileTable
        v-if="viewMode === 'list'"
        ref="tableRef"
        :files="displayFiles"
        :share-map="shareByFileId"
        :loading="loading"
        :scope="scope"
        :can-download="canDownload"
        :can-share="canShare"
        :can-move="canMove"
        :can-copy="canCopy"
        :can-transfer-to-personal="canTransferToPersonal"
        :can-delete="canDelete"
        @sort-change="onSortChange"
        @selection-change="onSelectionChange"
        @download="downloadFile"
        @share="openQuickShare"
        @move="(row) => openTransfer(row, 'move')"
        @copy="(row) => openTransfer(row, 'copy')"
        @transfer-to-personal="openTransferToPersonal"
        @transfer-to-team="openTransferToTeam"
        @rename="openRename"
        @delete="removeFile"
        @open-directory="openDirectory"
      />

      <FileGrid
        v-else
        :files="displayFiles"
        :selected-rows="selectedRows"
        :share-map="shareByFileId"
        :loading="loading"
        :scope="scope"
        :can-download="canDownload"
        :can-share="canShare"
        :can-move="canMove"
        :can-copy="canCopy"
        :can-transfer-to-personal="canTransferToPersonal"
        :can-delete="canDelete"
        @update:selected-rows="onSelectionChange"
        @download="downloadFile"
        @share="openQuickShare"
        @move="(row) => openTransfer(row, 'move')"
        @copy="(row) => openTransfer(row, 'copy')"
        @transfer-to-personal="openTransferToPersonal"
        @transfer-to-team="openTransferToTeam"
        @rename="openRename"
        @delete="removeFile"
        @open-directory="openDirectory"
      />
    </div>

    <UploadDialog
      v-model="showUpload"
      :parent-directory-id="activeDirectoryId"
      :scope="scope"
      :team-id="teamId"
      @changed="handleChanged"
    />
    <CreateDirDialog
      v-model="showCreateDir"
      :parent-directory-id="activeDirectoryId"
      :scope="scope"
      :team-id="teamId"
      @changed="handleChanged"
    />
    <TransferDialog
      v-model="showTransfer"
      :action="transferAction"
      :target-directory-id="transferTargetId"
      :scope="scope"
      :team-id="teamId"
      @update:target-directory-id="(value) => (transferTargetId = value)"
      @confirm="confirmTransfer"
    />
    <TransferToPersonalDialog
      v-model="showTransferToPersonal"
      :file="transferToPersonalFile"
      :team-id="teamId"
      @changed="handleChanged"
    />
    <ImportFromPersonalDialog
      v-model="showImportFromPersonal"
      :file="transferToTeamFile"
      @changed="handleChanged"
    />
    <RenameDialog
      v-model="showRename"
      :directory-id="renameId"
      :directory-name="renameName"
      :scope="scope"
      :team-id="teamId"
      @changed="handleChanged"
    />
    <QuickShareDialog
      v-model="showQuickShare"
      :file="currentShareFile"
      :scope="scope"
      :team-id="teamId"
      :existing-share="currentShareInfoResponseVO"
      @changed="handleChanged"
    />
  </div>
</template>

<script setup lang="ts">
  import FileAPI, { type FileInfoResponseVO as FileInfo } from "@/api/file.api";
  import DirectoryAPI from "@/api/directory.api";
  import ShareAPI, { type ShareInfoResponseVO } from "@/api/share.api";
  import TeamAPI from "@/api/team.api";
  import { PersonalAPI } from "@/api/personal.api";
  import { useTeamStore } from "@/store/team";
  import ResizeHandle from "@/layouts/components/ResizeHandle.vue";
  import { useFileExplorer } from "../../composables/use-file-explorer";
  import { isDirectory } from "../../utils/file-display";
  import type { TransferAction } from "../../types";
  import ExplorerHeader from "./ExplorerHeader.vue";
  import FileTree from "./FileTree.vue";
  import FileTable from "./FileTable.vue";
  import FileGrid from "./FileGrid.vue";
  import UploadDialog from "../dialogs/UploadDialog.vue";
  import CreateDirDialog from "../dialogs/CreateDirDialog.vue";
  import TransferDialog from "../dialogs/TransferDialog.vue";
  import TransferToPersonalDialog from "../dialogs/TransferToPersonalDialog.vue";
  import ImportFromPersonalDialog from "../dialogs/ImportFromPersonalDialog.vue";
  import RenameDialog from "../dialogs/RenameDialog.vue";
  import QuickShareDialog from "../dialogs/QuickShareDialog.vue";

  const props = withDefaults(
    defineProps<{
      scope?: "personal" | "team" | "private";
      teamId?: number;
      quotaUsed?: number;
      quotaTotal?: number;
      readonly?: boolean;
    }>(),
    { scope: "personal" }
  );

  const emit = defineEmits<{
    (e: "changed"): void;
  }>();

  const {
    activeDirectoryTrail,
    activeDirectoryId,
    selectedRows,
    keyword,
    loading,
    viewMode,
    treeVersion,
    refreshTrigger,
    canGoBack,
    canGoForward,
    activeTrailIds,
    displayFiles,
    fetchFiles,
    navigateDirectory,
    goBack,
    goForward,
    openDirectory,
    onSelectionChange,
    onSortChange,
    reload,
  } = useFileExplorer({ scope: props.scope, teamId: props.teamId });

  const rootLabel = computed(() =>
    props.scope === "team" ? "团队网盘" : props.scope === "private" ? "私密空间" : "我的网盘"
  );

  const teamStore = useTeamStore();
  const teamPerms = computed(() => (props.teamId ? teamStore.permsOf(props.teamId) : []));

  function hasTeamPerm(permission: string) {
    if (props.scope !== "team") return true;
    return teamPerms.value.includes("*:*:*") || teamPerms.value.includes(permission);
  }

  const canUpload = computed(() => !props.readonly && hasTeamPerm("file:upload"));
  const canDownload = computed(() => hasTeamPerm("file:download"));
  const canShare = computed(
    () => !props.readonly && props.scope !== "private" && hasTeamPerm("share:create")
  );
  const canMove = computed(() => !props.readonly && hasTeamPerm("file:move"));
  const canCopy = computed(
    () => !props.readonly && props.scope !== "private" && hasTeamPerm("file:copy")
  );
  const canTransferToPersonal = computed(() => hasTeamPerm("file:transfer:to-personal"));
  const canDelete = computed(() => !props.readonly && hasTeamPerm("file:delete"));
  const canManageShares = computed(() => hasTeamPerm("share:manage"));

  const treeWidth = useStorage<number>("explorerTreeWidth", 240);
  const isTreeDragging = ref(false);
  const tableRef = ref<InstanceType<typeof FileTable> | null>(null);

  // 分享数据：用于文件列表中的“分享中”标记与快速分享弹窗
  const shares = ref<ShareInfoResponseVO[]>([]);
  const shareByFileId = computed(() => {
    const map = new Map<number, ShareInfoResponseVO>();
    shares.value.forEach((item) => {
      if (item.fileId) map.set(item.fileId, item);
    });
    return map;
  });

  // 弹窗状态
  const showUpload = ref(false);
  const showCreateDir = ref(false);
  const showTransfer = ref(false);
  const showTransferToPersonal = ref(false);
  const showImportFromPersonal = ref(false);
  const showRename = ref(false);
  const showQuickShare = ref(false);

  const transferAction = ref<TransferAction>("move");
  const transferFileId = ref<number | null>(null);
  const transferTargetId = ref(0);
  const transferToPersonalFile = ref<FileInfo | null>(null);
  const transferToTeamFile = ref<FileInfo | null>(null);

  const renameId = ref<number | null>(null);
  const renameName = ref("");

  const currentShareFile = ref<FileInfo | null>(null);
  const currentShareInfoResponseVO = computed(() =>
    currentShareFile.value ? shareByFileId.value.get(currentShareFile.value.id) || null : null
  );

  async function fetchShares() {
    if (props.scope === "private") return;
    try {
      if (props.scope === "team" && props.teamId) {
        if (!canManageShares.value) {
          shares.value = [];
          return;
        }
        shares.value = await TeamAPI.listTeamShares(props.teamId);
      } else {
        shares.value = await ShareAPI.listMyShares();
      }
    } catch {
      shares.value = [];
    }
  }

  /** 数据变更后统一刷新：文件列表、目录树、分享标记、配额 */
  async function handleChanged() {
    await Promise.all([reload(), fetchShares()]);
    emit("changed");
  }

  watch(canManageShares, (canManage, wasManage) => {
    if (props.scope !== "team" || !props.teamId) return;
    if (canManage) {
      void fetchShares();
    } else if (wasManage) {
      shares.value = [];
    }
  });

  interface DownloadState {
    fileName: string;
    progress: number;
    status: "downloading" | "paused" | "done";
  }

  const downloadState = reactive<DownloadState>({
    fileName: "",
    progress: 0,
    status: "done",
  });
  let downloadAbort: AbortController | null = null;
  let downloadNotif: any = null;
  let activeDownloadRow: any = null;

  const DownloadContent = defineComponent({
    props: { state: { type: Object, required: true } },
    emits: ["pause", "resume", "cancel"],
    setup(props, { emit }) {
      const btnStyle =
        "padding:5px 12px;font-size:13px;background:transparent;border:1px solid var(--el-border-color);border-radius:4px;cursor:pointer;color:var(--el-text-color-regular)";
      return () => {
        const s = props.state as DownloadState;
        return h("div", { style: "min-width: 280px" }, [
          h(
            "div",
            {
              style:
                "margin-bottom: 10px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500",
            },
            s.fileName
          ),
          h("div", { style: "display: flex; align-items: center; gap: 8px; margin-bottom: 10px" }, [
            h(
              "div",
              {
                style:
                  "flex: 1; height: 6px; background: var(--el-border-color-light); border-radius: 3px; overflow: hidden",
              },
              [
                h("div", {
                  style: `width: ${s.progress}%; height: 100%; background: var(--el-color-primary); border-radius: 3px; transition: width 0.15s ease`,
                }),
              ]
            ),
            h("span", { style: "font-size: 13px; white-space: nowrap" }, `${s.progress}%`),
          ]),
          s.status !== "done"
            ? h("div", { style: "display: flex; gap: 8px; justify-content: flex-end" }, [
                s.status === "downloading"
                  ? h("button", { style: btnStyle, onClick: () => emit("pause") }, "暂停")
                  : s.status === "paused"
                    ? h(
                        "button",
                        {
                          style: `${btnStyle}color:var(--el-color-primary)`,
                          onClick: () => emit("resume"),
                        },
                        "继续"
                      )
                    : null,
                h(
                  "button",
                  {
                    style: `${btnStyle}color:var(--el-color-danger)`,
                    onClick: () => emit("cancel"),
                  },
                  "取消"
                ),
              ])
            : null,
        ]);
      };
    },
  });

  function openDownloadNotif() {
    if (downloadNotif) return;
    downloadNotif = ElNotification({
      title: "文件下载中",
      message: h(DownloadContent, {
        state: downloadState,
        onPause: handlePause,
        onResume: handleResume,
        onCancel: handleCancel,
      }),
      duration: 0,
      position: "top-right",
      onClose: () => {
        downloadNotif = null;
        cleanupDownload();
      },
    });
  }

  function finishDownload(ok: boolean) {
    if (downloadNotif) {
      downloadNotif.close();
      downloadNotif = null;
    }
    downloadState.status = "done";
    downloadAbort = null;
    if (ok) {
      ElNotification({
        title: "下载完成",
        message: downloadState.fileName,
        type: "success",
        position: "top-right",
      });
    } else {
      ElNotification({
        title: "下载失败",
        message: downloadState.fileName,
        type: "error",
        position: "top-right",
      });
    }
  }

  function cleanupDownload() {
    if (downloadAbort) {
      downloadAbort.abort();
      downloadAbort = null;
    }
    downloadState.status = "done";
  }

  function handlePause() {
    if (downloadAbort) {
      downloadAbort.abort();
      downloadAbort = null;
    }
    downloadState.status = "paused";
  }

  function handleResume() {
    if (!activeDownloadRow) return;
    executeDownload(activeDownloadRow);
  }

  function handleCancel() {
    cleanupDownload();
    if (downloadNotif) {
      downloadNotif.close();
      downloadNotif = null;
    }
  }

  async function executeDownload(row: any) {
    downloadAbort = new AbortController();
    downloadState.status = "downloading";
    const abortCtrl = downloadAbort;

    const onProgress = (e: ProgressEvent) => {
      if (abortCtrl.signal.aborted) return;
      if (e.lengthComputable && e.total > 0) {
        downloadState.progress = Math.round((e.loaded / e.total) * 100);
      }
    };

    try {
      let blob: Blob;
      const fileId = row.id;
      const sig = abortCtrl.signal;
      if (props.scope === "private") {
        blob = await PersonalAPI.downloadPrivateFile(fileId, onProgress, sig);
      } else if (props.scope === "team" && props.teamId) {
        blob = await TeamAPI.downloadTeamFile(props.teamId, fileId, onProgress, sig);
      } else {
        blob = await FileAPI.downloadPersonalFileById(fileId, onProgress, sig);
      }

      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = row.originalName;
      a.click();
      URL.revokeObjectURL(url);
      finishDownload(true);
    } catch (err: any) {
      if (
        err?.name === "CanceledError" ||
        err?.code === "ERR_CANCELED" ||
        abortCtrl.signal.aborted
      ) {
        return;
      }
      finishDownload(false);
    }
  }

  async function downloadFile(row: any) {
    if (downloadState.status !== "done") {
      handleCancel();
    }
    activeDownloadRow = row;
    downloadState.fileName = row.originalName;
    downloadState.progress = 0;
    openDownloadNotif();
    await executeDownload(row);
  }

  async function removeFile(row: any) {
    try {
      await ElMessageBox.confirm("确定要删除这个文件吗？", "提示", {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        type: "warning",
      });
      if (props.scope === "private") {
        await PersonalAPI.deletePrivateFile(row.id);
      } else if (props.scope === "team" && props.teamId) {
        await TeamAPI.deleteToTrash(props.teamId, row.id);
      } else if (isDirectory(row)) {
        await DirectoryAPI.deleteDirectory(row.id);
      } else {
        await FileAPI.deleteFileById(row.id);
      }
      ElMessage.success("删除成功");
      await handleChanged();
    } catch (error) {
      if (error === "cancel" || error === "close") return;
      ElMessage.error("删除失败");
    }
  }

  function openTransfer(row: FileInfo, action: TransferAction) {
    transferFileId.value = row.id;
    transferAction.value = action;
    transferTargetId.value = activeDirectoryId.value;
    showTransfer.value = true;
  }

  function openTransferToTeam(row: FileInfo) {
    transferToTeamFile.value = row;
    showImportFromPersonal.value = true;
  }

  function openTransferToTeamFromSelection() {
    if (selectedRows.length !== 1) {
      ElMessage.warning("请先选择一个文件");
      return;
    }
    const row = selectedRows[0];
    if (!row || isDirectory(row)) {
      ElMessage.warning("请选择一个文件");
      return;
    }
    openTransferToTeam(row);
  }

  function openTransferToPersonal(row: FileInfo) {
    transferToPersonalFile.value = row;
    showTransferToPersonal.value = true;
  }

  async function confirmTransfer() {
    if (!transferFileId.value) {
      ElMessage.warning("请选择文件");
      return;
    }
    const label = transferAction.value === "move" ? "移动" : "复制";
    try {
      if (props.scope === "private") {
        const body = { targetDirectoryId: transferTargetId.value };
        await PersonalAPI.movePrivateFile(transferFileId.value, body);
      } else if (props.scope === "team" && props.teamId) {
        const body = { targetDirectoryId: transferTargetId.value } as any;
        if (transferAction.value === "move") {
          await TeamAPI.moveTeamFile(props.teamId, transferFileId.value, body);
        } else {
          await TeamAPI.copyTeamFile(props.teamId, transferFileId.value, body);
        }
      } else if (transferAction.value === "move") {
        await FileAPI.moveFile(transferFileId.value, { targetDirectoryId: transferTargetId.value });
      } else {
        await FileAPI.copyFile(transferFileId.value, { targetDirectoryId: transferTargetId.value });
      }
      ElMessage.success(`${label}成功`);
      showTransfer.value = false;
      await nextTick();
      await handleChanged();
      showTransfer.value = false;
    } catch {
      ElMessage.error(`${label}失败`);
    }
  }

  function openRename(row: FileInfo) {
    renameId.value = row.id;
    renameName.value = row.originalName;
    showRename.value = true;
  }

  function openQuickShare(row: FileInfo) {
    currentShareFile.value = row;
    showQuickShare.value = true;
  }

  onMounted(handleChanged);

  /** 标签页重新激活时重算表格列宽，避免 v-show 隐藏期间的布局错位 */
  function activate() {
    nextTick(() => tableRef.value?.doLayout());
  }

  defineExpose({ refresh: handleChanged, activate });
</script>

<style scoped>
  .file-explorer {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-height: 0;
  }

  .file-explorer__main {
    display: grid;
    flex: 1;
    min-height: 0;
  }

  .file-explorer__tree {
    position: relative;
    min-height: 0;
    padding: 8px 12px;
    border-right: 1px solid var(--el-border-color-lighter);
  }

  .tree-scrollbar :deep(.el-scrollbar__wrap) {
    overflow: auto;
  }
</style>
