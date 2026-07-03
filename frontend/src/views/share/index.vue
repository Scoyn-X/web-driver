<template>
  <div class="share-page">
    <main class="flex items-start justify-center">
      <section
        class="share-card"
        :class="{ 'share-card--wide': accessInfo?.isDirectory }"
      >
        <header
          class="flex items-center justify-between pb-4 mb-5 border-b border-solid border-[var(--el-border-color-lighter)]"
        >
          <h1 class="m-0 text-base font-semibold text-[var(--el-text-color-primary)]">
            {{ pageTitle }}
          </h1>
          <el-tag
            v-if="accessInfo?.requireExtractCode"
            size="small"
            type="warning"
          >
            需要提取码
          </el-tag>
          <el-tag
            v-else-if="accessInfo"
            size="small"
            type="success"
          >
            公开分享
          </el-tag>
          <el-tag
            v-else-if="errorMessage"
            size="small"
            type="danger"
          >
            分享失效
          </el-tag>
        </header>

        <section class="flex gap-4 mb-5 file-block">
          <FileIcon
            :type="fileIconType"
            :size="44"
          />
          <div class="flex-1 min-w-0 file-meta">
            <div class="file-name">{{ accessInfo?.fileName || "—" }}</div>
            <dl class="meta-list">
              <div class="meta-row">
                <dt>大小</dt>
                <dd>{{ fileSizeText }}</dd>
              </div>
              <div class="meta-row">
                <dt>类型</dt>
                <dd>{{ fileTypeText }}</dd>
              </div>
              <div class="meta-row">
                <dt>上传时间</dt>
                <dd>{{ uploadTimeText }}</dd>
              </div>
            </dl>
          </div>
        </section>

        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          show-icon
          :closable="false"
          class="mb-4"
        />

        <section
          v-if="accessInfo?.requireExtractCode && !verified"
          class="flex flex-col gap-2.5"
        >
          <label class="text-[13px] text-[var(--el-text-color-regular)]">提取码</label>
          <el-input
            v-model="extractCode"
            maxlength="4"
            clearable
            placeholder="请输入 4 位提取码"
            @keyup.enter="handleVerify"
          />
          <el-button
            type="primary"
            class="w-full"
            :loading="verifying"
            @click="handleVerify"
          >
            验证
          </el-button>
        </section>

        <section
          v-else-if="accessInfo && !accessInfo.isDirectory"
          class="flex flex-col gap-2.5"
        >
          <el-button
            type="primary"
            class="w-full"
            :loading="downloading"
            :disabled="!canDownload"
            @click="() => handleDownload()"
          >
            下载文件
          </el-button>
        </section>

        <section
          v-else-if="accessInfo?.isDirectory"
          class="flex flex-col gap-3"
        >
          <div
            v-if="directoryBreadcrumb.length"
            class="shared-breadcrumb"
          >
            <template
              v-for="(item, index) in directoryBreadcrumb"
              :key="item.id"
            >
              <el-button
                link
                type="primary"
                @click="navigateSharedDirectory(item.id || 0)"
              >
                {{ item.name || "目录" }}
              </el-button>
              <span v-if="index < directoryBreadcrumb.length - 1">/</span>
            </template>
          </div>

          <el-table
            v-loading="childrenLoading"
            :data="directoryItems"
            size="small"
          >
            <template #empty>
              <el-empty description="暂无内容" />
            </template>
            <el-table-column
              label="名称"
              min-width="180"
            >
              <template #default="{ row }">
                <div class="shared-file-name">
                  <FileIcon
                    :type="sharedItemIcon(row)"
                    :size="18"
                  />
                  <el-button
                    v-if="isSharedDirectory(row)"
                    link
                    type="primary"
                    @click="navigateSharedDirectory(row.id || 0)"
                  >
                    {{ row.originalName || "-" }}
                  </el-button>
                  <span v-else>{{ row.originalName || "-" }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              label="类型"
              width="88"
              :formatter="formatSharedItemType"
            />
            <el-table-column
              label="大小"
              width="96"
              :formatter="formatSharedItemSize"
            />
            <el-table-column
              label="操作"
              width="80"
              align="center"
            >
              <template #default="{ row }">
                <el-button
                  v-if="!isSharedDirectory(row)"
                  link
                  type="primary"
                  @click="handleDownload(row)"
                >
                  下载
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>

        <div
          v-if="accessInfo && !errorMessage"
          class="flex justify-center mt-4"
        >
          <el-button
            link
            @click="handleCopyLink"
          >
            复制分享链接
          </el-button>
        </div>
      </section>
    </main>

    <div
      v-if="loading"
      class="loading-mask"
    >
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在读取分享信息...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
  import ShareAPI, {
    type BreadcrumbItemResponseVO,
    type FileInfoResponseVO,
    type ShareAccessResponseVO,
  } from "@/api/share.api";
  import { formatSize } from "@/utils/format-size";
  import { formatTimestamp } from "@/utils/format-time";

  const route = useRoute();
  const shareToken = String(route.params.shareToken || "");

  const loading = ref(false);
  const verifying = ref(false);
  const downloading = ref(false);
  const verified = ref(false);
  const extractCode = ref("");
  const errorMessage = ref("");

  // ---- download progress ----
  const dlState = reactive({ fileName: "", progress: 0, speed: 0, visible: false });
  let dlNotif: any = null;
  let dlCancel: (() => void) | null = null;

  const ProgressBar = defineComponent({
    props: { state: { type: Object, required: true } },
    setup(props) {
      return () => {
        const s = props.state as typeof dlState;
        return h("div", { style: "min-width: 280px" }, [
          h("div", { style: "margin-bottom: 8px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500" }, s.fileName),
          h("div", { style: "display: flex; align-items: center; gap: 8px" }, [
            h("div", { style: "flex: 1; height: 6px; background: var(--el-border-color-light); border-radius: 3px; overflow: hidden" }, [
              h("div", { style: `width: ${s.progress}%; height: 100%; background: var(--el-color-primary); border-radius: 3px; transition: width 0.15s ease` }),
            ]),
            h("span", { style: "font-size: 13px; white-space: nowrap" }, `${s.progress}%`),
          ]),
          h("div", { style: "margin-top: 4px; font-size: 12px; color: var(--el-text-color-secondary)" }, s.speed > 0 ? `${formatSize(s.speed)}/s` : ""),
        ]);
      };
    },
  });

  function openProgressNotif() {
    if (dlNotif) return;
    dlNotif = ElNotification({
      title: "文件下载中",
      message: h(ProgressBar, { state: dlState }),
      duration: 0,
      position: "top-right",
      onClose: () => { dlNotif = null; if (dlCancel) { dlCancel(); dlCancel = null; } },
    });
  }

  function finishProgress(ok: boolean) {
    if (dlNotif) { dlNotif.close(); dlNotif = null; }
    dlState.visible = false;
    dlCancel = null;
    downloading.value = false;
    if (ok) {
      ElNotification({ title: "下载完成", message: dlState.fileName, type: "success", position: "top-right" });
    } else {
      ElNotification({ title: "下载失败", message: dlState.fileName, type: "error", position: "top-right" });
    }
  }
  const accessInfo = ref<ShareAccessResponseVO | null>(null);
  const childrenLoading = ref(false);
  const directoryItems = ref<FileInfoResponseVO[]>([]);
  const directoryBreadcrumb = ref<BreadcrumbItemResponseVO[]>([]);

  const pageTitle = computed(() => (accessInfo.value?.isDirectory ? "目录分享" : "文件分享"));

  const fileExt = computed(() => {
    const name = accessInfo.value?.fileName || "";
    const ext = name.includes(".") ? name.split(".").pop() : "";
    return ext?.toLowerCase() || "file";
  });

  const fileIconType = computed(() => (accessInfo.value?.isDirectory ? "folder" : fileExt.value));

  const fileTypeText = computed(() => {
    if (accessInfo.value?.isDirectory) return "文件夹";
    return accessInfo.value?.mimeType || "未知";
  });

  const fileSizeText = computed(() => {
    const size = accessInfo.value?.fileSize;
    if (typeof size === "number") return formatSize(size);
    return accessInfo.value?.fileSizeFormatted || "未知";
  });

  const uploadTimeText = computed(() => {
    const time = accessInfo.value?.fileUploadTime;
    return time ? formatTimestamp(time) : "未知";
  });

  const canDownload = computed(() => {
    if (!accessInfo.value || errorMessage.value) return false;
    return !accessInfo.value.requireExtractCode || verified.value;
  });

  async function fetchAccessInfo() {
    if (!shareToken) {
      errorMessage.value = "分享链接无效";
      return;
    }
    loading.value = true;
    errorMessage.value = "";
    try {
      const data = await ShareAPI.getShare(shareToken);
      accessInfo.value = data;
      verified.value = !data.requireExtractCode;
      if (data.isDirectory && verified.value) {
        await fetchSharedChildren(0);
      }
    } catch {
      errorMessage.value = "分享不存在、已失效或已被取消";
    } finally {
      loading.value = false;
    }
  }

  async function handleVerify() {
    const code = extractCode.value.trim();
    if (code.length !== 4) {
      ElMessage.warning("请输入 4 位提取码");
      return;
    }
    verifying.value = true;
    errorMessage.value = "";
    try {
      const payload = { extractCode: code };
      await ShareAPI.verifyExtractCode(shareToken, payload);
      verified.value = true;
      if (accessInfo.value?.isDirectory) {
        await fetchSharedChildren(0);
      }
      ElMessage.success("提取码验证成功");
    } catch {
      verified.value = false;
      errorMessage.value = "提取码错误，请重试";
    } finally {
      verifying.value = false;
    }
  }

  async function fetchSharedChildren(parentId: number) {
    if (!accessInfo.value?.isDirectory || !canDownload.value) return;
    childrenLoading.value = true;
    errorMessage.value = "";
    try {
      const code = accessInfo.value.requireExtractCode ? extractCode.value.trim() : undefined;
      const data = await ShareAPI.getSShareTokenChildren(shareToken, parentId, code);
      directoryItems.value = data.items || [];
      directoryBreadcrumb.value = data.breadcrumb || [];
    } catch {
      errorMessage.value = "目录读取失败，请稍后重试";
    } finally {
      childrenLoading.value = false;
    }
  }

  function navigateSharedDirectory(parentId: number) {
    fetchSharedChildren(parentId);
  }

  function isSharedDirectory(file: FileInfoResponseVO) {
    return Number(file.isDirectory) === 1;
  }

  function sharedItemIcon(file: FileInfoResponseVO) {
    if (isSharedDirectory(file)) return "folder";
    const name = file.originalName || "";
    const ext = name.includes(".") ? name.split(".").pop() : "";
    return ext?.toLowerCase() || "file";
  }

  function formatSharedItemType(row: FileInfoResponseVO) {
    return isSharedDirectory(row) ? "文件夹" : row.mimeType || "文件";
  }

  function formatSharedItemSize(row: FileInfoResponseVO) {
    return isSharedDirectory(row) ? "-" : formatSize(row.fileSize || 0);
  }

  async function handleDownload(file?: FileInfoResponseVO) {
    if (!canDownload.value) return;
    downloading.value = true;
    errorMessage.value = "";

    const code = accessInfo.value?.requireExtractCode ? extractCode.value.trim() : undefined;
    const fileName = file?.originalName || accessInfo.value?.fileName || "download";

    dlState.fileName = fileName;
    dlState.progress = 0;
    dlState.speed = 0;
    openProgressNotif();

    dlCancel = ShareAPI.downloadWithProgress(
      shareToken,
      code,
      file?.id,
      (_name, _total) => { /* start */ },
      (percent, speed) => { dlState.progress = percent; dlState.speed = speed; },
      (_b64) => { /* chunk */ },
      () => { finishProgress(true); },
      (_msg) => { finishProgress(false); },
    );
  }

  async function handleCopyLink() {
    const link = window.location.href;
    try {
      await navigator.clipboard.writeText(link);
      ElMessage.success("分享链接已复制");
    } catch {
      ElMessage.warning("复制失败，请手动复制地址栏链接");
    }
  }

  onMounted(fetchAccessInfo);
</script>

<style scoped>
  .share-page {
    min-height: 100vh;
    padding: 40px 20px;
    background: var(--el-bg-color-page);
  }

  .share-card {
    width: 100%;
    max-width: 520px;
    padding: 28px;
    background: var(--el-fill-color-blank);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
  }

  .share-card--wide {
    max-width: 760px;
  }

  .file-name {
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    white-space: nowrap;
  }

  .loading-mask {
    position: fixed;
    inset: 0;
    z-index: 10;
    display: flex;
    gap: 10px;
    align-items: center;
    justify-content: center;
    color: var(--el-text-color-regular);
    background: rgb(255 255 255 / 70%);
  }

  .shared-breadcrumb {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    align-items: center;
    padding: 8px 0;
    font-size: 13px;
  }

  .shared-file-name {
    display: flex;
    gap: 8px;
    align-items: center;
    min-width: 0;
  }

  .shared-file-name span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
</style>

<style scoped>
  .file-block {
    display: flex;
    gap: 16px;
    margin-bottom: 20px;
  }

  .file-meta {
    flex: 1;
    min-width: 0;
  }

  .meta-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: 12px 0 0;
  }

  .meta-row {
    display: flex;
    gap: 12px;
    font-size: 13px;
  }

  .meta-row dt {
    flex: none;
    width: 64px;
    color: var(--el-text-color-secondary);
  }

  .meta-row dd {
    flex: 1;
    min-width: 0;
    margin: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    color: var(--el-text-color-regular);
    white-space: nowrap;
  }

  @media (width <= 560px) {
    .share-page {
      padding: 20px 12px;
    }

    .share-card {
      padding: 20px;
    }

    .file-block {
      flex-direction: column;
      gap: 12px;
    }
  }
</style>
