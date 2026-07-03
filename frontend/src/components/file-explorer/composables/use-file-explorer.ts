/**
 * 文件浏览器核心逻辑：目录数据加载、前进/后退历史、排序。
 * 从 index.vue 中抽离，使视图组件专注于模板与交互编排。
 */
import FileAPI, {
  type BreadcrumbItemResponseVO,
  type FileInfoResponseVO as FileInfo,
} from "@/api/file.api";
import TeamAPI from "@/api/team.api";
import { PersonalAPI } from "@/api/personal.api";
import { formatFileType, normalizeDirectoryId, type ViewMode } from "../utils/file-display";

type SortOrder = "ascending" | "descending" | null;

interface SortState {
  prop: string;
  order: SortOrder;
}

/** 解析时间戳为毫秒数，非法值返回 0 */
function parseTimestamp(value: string): number {
  const ms = new Date(value).getTime();
  return Number.isNaN(ms) ? 0 : ms;
}

/** 按列属性比较两个文件，返回排序权值 */
function compareByProp(a: FileInfo, b: FileInfo, prop: string): number {
  if (prop === "originalName") {
    return a.originalName.localeCompare(b.originalName, "zh-CN", { sensitivity: "base" });
  }
  if (prop === "type") {
    return formatFileType(a).localeCompare(formatFileType(b), "zh-CN", { sensitivity: "base" });
  }
  if (prop === "createTime") {
    return parseTimestamp(a.createTime) - parseTimestamp(b.createTime);
  }
  if (prop === "fileSize") {
    return Number(a.fileSize || 0) - Number(b.fileSize || 0);
  }
  return 0;
}

export function useFileExplorer(options?: {
  scope?: "personal" | "team" | "private";
  teamId?: number;
}) {
  const scope = options?.scope ?? "personal";
  const teamId = options?.teamId;
  const files = ref<FileInfo[]>([]);
  const activeDirectoryTrail = ref<BreadcrumbItemResponseVO[]>([]);
  const activeDirectoryId = ref(0);
  const selectedRows = ref<FileInfo[]>([]);
  const keyword = ref("");
  const loading = ref(false);
  const viewMode = ref<ViewMode>("list");

  /** 每次刷新自增，用于强制重建目录树 */
  const treeVersion = ref(0);
  /** 每次刷新自增，用于通知配额等附属组件重新拉取 */
  const refreshTrigger = ref(0);

  const sortState = ref<SortState>({ prop: "", order: null });
  const directoryHistory = ref<number[]>([0]);
  const historyIndex = ref(0);

  const canGoBack = computed(() => historyIndex.value > 0);
  const canGoForward = computed(() => historyIndex.value < directoryHistory.value.length - 1);
  const activeTrailIds = computed(() =>
    activeDirectoryTrail.value.map((item) => normalizeDirectoryId(item.id))
  );

  /** 目录优先 + 当前排序列的展示用文件列表 */
  const displayFiles = computed(() => {
    const indexed = files.value.map((item, index) => ({ item, index }));

    indexed.sort((a, b) => {
      const aIsDir = Number(a.item.isDirectory) === 1;
      const bIsDir = Number(b.item.isDirectory) === 1;
      if (aIsDir !== bIsDir) return aIsDir ? -1 : 1;

      const { prop, order } = sortState.value;
      if (!prop || !order) return a.index - b.index;

      const direction = order === "ascending" ? 1 : -1;
      const result = compareByProp(a.item, b.item, prop);
      return result !== 0 ? result * direction : a.index - b.index;
    });

    return indexed.map((entry) => entry.item);
  });

  /** 按当前目录 / 关键词拉取文件列表 */
  async function fetchFiles() {
    loading.value = true;
    try {
      const kw = keyword.value.trim();
      if (kw) {
        if (scope === "team" && teamId) {
          files.value = await TeamAPI.searchTeamFiles(teamId, kw);
        } else {
          files.value = await FileAPI.searchPersonalFiles(kw);
        }
        activeDirectoryTrail.value = [];
        return;
      }
      if (scope === "private") {
        const result = await PersonalAPI.listPrivateFiles(activeDirectoryId.value);
        files.value = (result as any).items || (Array.isArray(result) ? result : []);
        activeDirectoryTrail.value = (result as any).breadcrumb || [];
      } else if (scope === "team" && teamId) {
        const result = await TeamAPI.listTeamFiles(teamId, activeDirectoryId.value);
        files.value = (result as any).items || [];
        activeDirectoryTrail.value = (result as any).breadcrumb || [];
      } else {
        const result = await FileAPI.listPersonalFiles(activeDirectoryId.value);
        files.value = result.items || [];
        activeDirectoryTrail.value = result.breadcrumb || [];
      }
    } finally {
      loading.value = false;
    }
  }

  /** 切换目录，并维护前进/后退历史栈 */
  async function navigateDirectory(directoryId: number, fromHistory = false) {
    const nextId = normalizeDirectoryId(directoryId);
    activeDirectoryId.value = nextId;
    keyword.value = "";
    selectedRows.value = [];

    if (!fromHistory) {
      const current = directoryHistory.value[historyIndex.value];
      if (current === nextId) return;
      directoryHistory.value = directoryHistory.value.slice(0, historyIndex.value + 1);
      directoryHistory.value.push(nextId);
      historyIndex.value = directoryHistory.value.length - 1;
    }

    await fetchFiles();
  }

  async function goBack() {
    if (!canGoBack.value) return;
    historyIndex.value -= 1;
    await navigateDirectory(directoryHistory.value[historyIndex.value], true);
  }

  async function goForward() {
    if (!canGoForward.value) return;
    historyIndex.value += 1;
    await navigateDirectory(directoryHistory.value[historyIndex.value], true);
  }

  /** 打开目录（双击文件夹 / 树节点） */
  async function openDirectory(row: FileInfo) {
    await navigateDirectory(normalizeDirectoryId(row.id));
  }

  function onSelectionChange(rows: FileInfo[]) {
    selectedRows.value = rows;
  }

  function onSortChange(payload: { prop?: string; order: SortOrder }) {
    sortState.value = { prop: payload.prop || "", order: payload.order };
  }

  /** 重新加载当前目录，并触发目录树与附属组件刷新 */
  async function reload() {
    selectedRows.value = [];
    treeVersion.value += 1;
    await fetchFiles();
    refreshTrigger.value += 1;
  }

  return {
    files,
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
  };
}
