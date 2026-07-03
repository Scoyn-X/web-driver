/**
 * 网盘存储域的 Mock 状态：目录树、文件、回收站、分享。dev server 重启即重置。
 *
 * file / quota / share 三个 mock 模块都从这里读写状态，从而保证
 * “上传后配额变化”“删除后进入回收站”“分享指向真实文件”等行为彼此自洽。
 */
import { createSequence, daysFromNow, formatSize, now, randomToken } from "../shared";

export interface MockDirectory {
  id: number;
  name: string;
  parentId: number;
  createTime: string;
}

export interface MockFile {
  id: number;
  originalName: string;
  fileSize: number;
  mimeType: string;
  createTime: string;
  fileUrl: string;
  parentId: number;
}

export interface MockRecycleItem {
  id: number;
  originalName: string;
  path: string;
  fileSize: number;
  mimeType: string;
  isDirectory: number;
  /** 删除前所在目录，用于还原 */
  parentId: number;
  deletedAt: string;
  deleterId: number;
  expiresAt: string;
}

export interface MockShare {
  id: number;
  fileId: number;
  shareToken: string;
  accessType: number;
  extractCode: string | null;
  expireTime: string | null;
  statusDesc: string;
  createTime: string;
}

export interface BreadcrumbItem {
  id: number;
  name: string;
}

const GB = 1024 ** 3;

/** 个人网盘总配额 */
export const TOTAL_QUOTA = 5 * GB;

export const directories = new Map<number, MockDirectory>([
  [1, { id: 1, name: "项目文档", parentId: 0, createTime: now() }],
  [2, { id: 2, name: "图片资源", parentId: 0, createTime: now() }],
  [3, { id: 3, name: "归档", parentId: 1, createTime: now() }],
]);

export const files = new Map<number, MockFile>([
  [
    101,
    {
      id: 101,
      originalName: "readme.txt",
      fileSize: 1234,
      mimeType: "text/plain",
      createTime: now(),
      fileUrl: "/files/readme.txt",
      parentId: 1,
    },
  ],
  [
    102,
    {
      id: 102,
      originalName: "design.png",
      fileSize: 824_001,
      mimeType: "image/png",
      createTime: now(),
      fileUrl: "/files/design.png",
      parentId: 2,
    },
  ],
  [
    103,
    {
      id: 103,
      originalName: "notes.md",
      fileSize: 2048,
      mimeType: "text/markdown",
      createTime: now(),
      fileUrl: "/files/notes.md",
      parentId: 3,
    },
  ],
]);

export const recycleBin = new Map<number, MockRecycleItem>([
  [
    201,
    {
      id: 201,
      originalName: "old.doc",
      path: "/old.doc",
      fileSize: 2048,
      mimeType: "application/msword",
      isDirectory: 0,
      parentId: 0,
      deletedAt: now(),
      deleterId: 1,
      expiresAt: daysFromNow(30),
    },
  ],
]);

export const shares = new Map<number, MockShare>([
  [
    301,
    {
      id: 301,
      fileId: 102,
      shareToken: "demoshare01",
      accessType: 0,
      extractCode: null,
      expireTime: daysFromNow(7),
      statusDesc: "active",
      createTime: now(),
    },
  ],
]);

export const directorySequence = createSequence(Math.max(...Array.from(directories.keys())));
export const fileSequence = createSequence(Math.max(...Array.from(files.keys())));
export const recycleSequence = createSequence(Math.max(...Array.from(recycleBin.keys())));
export const shareSequence = createSequence(Math.max(...Array.from(shares.keys())));

/* ------------------------------------------------------------------ *
 * 目录树查询
 * ------------------------------------------------------------------ */

/** 列出某目录下的直接子目录 */
export const listChildDirectories = (parentId: number): MockDirectory[] =>
  Array.from(directories.values()).filter((d) => d.parentId === parentId);

/** 列出某目录下的直接子文件 */
export const listChildFiles = (parentId: number): MockFile[] =>
  Array.from(files.values()).filter((f) => f.parentId === parentId);

/** 目录是否含有子目录或子文件 */
export const hasChildren = (directoryId: number): boolean =>
  listChildDirectories(directoryId).length > 0 || listChildFiles(directoryId).length > 0;

/** 取从根到目标目录的 ID 链，如目录 3 -> [1, 3] */
export function pathIds(directoryId: number): number[] {
  const chain: number[] = [];
  const guard = new Set<number>();
  let cursor = directoryId;
  while (cursor && directories.has(cursor) && !guard.has(cursor)) {
    guard.add(cursor);
    chain.unshift(cursor);
    cursor = directories.get(cursor)!.parentId;
  }
  return chain;
}

/** 构造面包屑（仅祖先目录链，不含“根”节点，由前端自行渲染根） */
export const buildBreadcrumb = (parentId: number): BreadcrumbItem[] =>
  pathIds(parentId).map((id) => ({ id, name: directories.get(id)!.name }));

/** 收集目标目录及其全部后代目录的 ID */
export function collectDirectorySubtree(directoryId: number): number[] {
  const result: number[] = [directoryId];
  for (let i = 0; i < result.length; i += 1) {
    listChildDirectories(result[i]).forEach((child) => result.push(child.id));
  }
  return result;
}

/* ------------------------------------------------------------------ *
 * 视图转换：内部状态 -> 接口约定的 FileInfo / DirectoryTreeNode
 * ------------------------------------------------------------------ */

/** 目录 -> FileInfo（isDirectory = 1） */
export function directoryToFileInfo(dir: MockDirectory) {
  return {
    id: dir.id,
    originalName: dir.name,
    fileSize: 0,
    mimeType: "directory",
    createTime: dir.createTime,
    fileUrl: "",
    isDirectory: 1,
    parentId: dir.parentId,
    directoryId: dir.id,
    fullPath: pathIds(dir.id),
  };
}

/** 文件 -> FileInfo（isDirectory = 0） */
export function fileToFileInfo(file: MockFile) {
  return {
    id: file.id,
    originalName: file.originalName,
    fileSize: file.fileSize,
    mimeType: file.mimeType,
    createTime: file.createTime,
    fileUrl: file.fileUrl,
    isDirectory: 0,
    parentId: file.parentId,
    directoryId: null,
    fullPath: pathIds(file.parentId),
  };
}

/** 列出某目录下的全部条目（目录在前、文件在后） */
export const listEntries = (parentId: number) => [
  ...listChildDirectories(parentId).map(directoryToFileInfo),
  ...listChildFiles(parentId).map(fileToFileInfo),
];

/** 目录 -> DirectoryTreeNode */
export const toDirectoryTreeNode = (dir: MockDirectory) => ({
  id: dir.id,
  name: dir.name,
  parentId: dir.parentId,
  directoryId: null,
  hasChildren: listChildDirectories(dir.id).length > 0,
});

/** 递归构造目录树 */
export function buildDirectoryTree(parentId = 0): any[] {
  return listChildDirectories(parentId).map((dir) => ({
    ...toDirectoryTreeNode(dir),
    children: buildDirectoryTree(dir.id),
  }));
}

/* ------------------------------------------------------------------ *
 * 回收站
 * ------------------------------------------------------------------ */

/** 把一个文件移入回收站（从 files 中移除） */
export function moveFileToRecycle(file: MockFile, deleterId: number): MockRecycleItem {
  files.delete(file.id);
  const item: MockRecycleItem = {
    id: recycleSequence.next(),
    originalName: file.originalName,
    path: `/${[...pathIds(file.parentId).map((id) => directories.get(id)!.name), file.originalName].join("/")}`,
    fileSize: file.fileSize,
    mimeType: file.mimeType,
    isDirectory: 0,
    parentId: file.parentId,
    deletedAt: now(),
    deleterId,
    expiresAt: daysFromNow(30),
  };
  recycleBin.set(item.id, item);
  return item;
}

/** 从回收站还原一个文件；原目录已不存在时还原到根目录 */
export function restoreFromRecycle(recycleId: number): MockFile | null {
  const item = recycleBin.get(recycleId);
  if (!item) return null;
  const parentId = item.parentId && directories.has(item.parentId) ? item.parentId : 0;
  const file: MockFile = {
    id: fileSequence.next(),
    originalName: item.originalName,
    fileSize: item.fileSize,
    mimeType: item.mimeType,
    createTime: now(),
    fileUrl: `/files/${item.originalName}`,
    parentId,
  };
  files.set(file.id, file);
  recycleBin.delete(recycleId);
  return file;
}

/* ------------------------------------------------------------------ *
 * 配额
 * ------------------------------------------------------------------ */

/** 当前已用空间：所有文件大小之和 */
export const usedSpace = (): number =>
  Array.from(files.values()).reduce((sum, f) => sum + f.fileSize, 0);

/** 个人配额信息（对齐 QuotaInfo） */
export function quotaInfo() {
  const used = usedSpace();
  const remaining = Math.max(0, TOTAL_QUOTA - used);
  return {
    totalQuota: TOTAL_QUOTA,
    usedSpace: used,
    remainingSpace: remaining,
    totalQuotaFormatted: formatSize(TOTAL_QUOTA),
    usedSpaceFormatted: formatSize(used),
    remainingSpaceFormatted: formatSize(remaining),
  };
}

/* ------------------------------------------------------------------ *
 * 分享
 * ------------------------------------------------------------------ */

/** 分享 -> ShareInfo（补全文件名） */
export function toShareInfo(share: MockShare) {
  return {
    id: share.id,
    fileId: share.fileId,
    fileName: files.get(share.fileId)?.originalName ?? "(文件已删除)",
    shareToken: share.shareToken,
    accessType: share.accessType,
    extractCode: share.extractCode,
    code: share.extractCode,
    expireTime: share.expireTime,
    statusDesc: isShareExpired(share) ? "expired" : share.statusDesc,
    createTime: share.createTime,
  };
}

/** 分享是否已过期 */
export const isShareExpired = (share: MockShare): boolean =>
  !!share.expireTime && new Date(share.expireTime).getTime() < Date.now();

/** 按 token 查找分享 */
export const findShareByToken = (token: string): MockShare | undefined =>
  Array.from(shares.values()).find((s) => s.shareToken === token);

/** 新建一个分享 token（保证唯一） */
export function uniqueShareToken(): string {
  let token = randomToken();
  while (findShareByToken(token)) token = randomToken();
  return token;
}
