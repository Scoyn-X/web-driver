/**
 * 团队存储 Mock 状态：每个团队独立的目录树、文件、回收站、分享。
 *
 * 与 `file.state.ts`（个人空间）状态空间完全独立：所有 Map 按 teamId 索引。
 * dev server 重启即重置。
 */
import { createSequence, daysFromNow, now, randomToken } from "../shared";

export interface TeamDirectory {
  id: number;
  name: string;
  parentId: number;
  createTime: string;
}

export interface TeamFile {
  id: number;
  originalName: string;
  fileSize: number;
  mimeType: string;
  createTime: string;
  fileUrl: string;
  parentId: number;
  uploaderId: number;
}

export interface TeamTrashItem {
  id: number;
  originalName: string;
  path: string;
  fileSize: number;
  mimeType: string;
  isDirectory: number;
  parentId: number;
  deletedAt: string;
  deleterId: number;
  expiresAt: string;
}

export interface TeamShare {
  id: number;
  fileId: number;
  shareToken: string;
  accessType: number;
  extractCode: string | null;
  expireTime: string | null;
  statusDesc: string;
  createTime: string;
  creatorId: number;
}

/* ------------------------------------------------------------------ *
 * 状态容器：四个域各自一个 teamId -> 子状态 Map 的两级结构
 * ------------------------------------------------------------------ */

const directoriesByTeam = new Map<number, Map<number, TeamDirectory>>();
const filesByTeam = new Map<number, Map<number, TeamFile>>();
const trashByTeam = new Map<number, Map<number, TeamTrashItem>>();
const sharesByTeam = new Map<number, Map<number, TeamShare>>();

export const teamDirSequence = createSequence(1000);
export const teamFileSequence = createSequence(2000);
export const teamTrashSequence = createSequence(3000);
export const teamShareSequence = createSequence(4000);

/** 取（必要时初始化）指定团队的目录子 Map */
export function getTeamDirs(teamId: number): Map<number, TeamDirectory> {
  let map = directoriesByTeam.get(teamId);
  if (!map) {
    map = new Map();
    directoriesByTeam.set(teamId, map);
  }
  return map;
}

/** 取（必要时初始化）指定团队的文件子 Map */
export function getTeamFiles(teamId: number): Map<number, TeamFile> {
  let map = filesByTeam.get(teamId);
  if (!map) {
    map = new Map();
    filesByTeam.set(teamId, map);
  }
  return map;
}

/** 取（必要时初始化）指定团队的回收站子 Map */
export function getTeamTrash(teamId: number): Map<number, TeamTrashItem> {
  let map = trashByTeam.get(teamId);
  if (!map) {
    map = new Map();
    trashByTeam.set(teamId, map);
  }
  return map;
}

/** 取（必要时初始化）指定团队的分享子 Map */
export function getTeamShares(teamId: number): Map<number, TeamShare> {
  let map = sharesByTeam.get(teamId);
  if (!map) {
    map = new Map();
    sharesByTeam.set(teamId, map);
  }
  return map;
}

/* ------------------------------------------------------------------ *
 * 目录树查询
 * ------------------------------------------------------------------ */

export const listTeamChildDirectories = (teamId: number, parentId: number): TeamDirectory[] =>
  Array.from(getTeamDirs(teamId).values()).filter((d) => d.parentId === parentId);

export const listTeamChildFiles = (teamId: number, parentId: number): TeamFile[] =>
  Array.from(getTeamFiles(teamId).values()).filter((f) => f.parentId === parentId);

/** 取从根到目标目录的 ID 链 */
export function teamPathIds(teamId: number, directoryId: number): number[] {
  const dirs = getTeamDirs(teamId);
  const chain: number[] = [];
  const guard = new Set<number>();
  let cursor = directoryId;
  while (cursor && dirs.has(cursor) && !guard.has(cursor)) {
    guard.add(cursor);
    chain.unshift(cursor);
    cursor = dirs.get(cursor)!.parentId;
  }
  return chain;
}

/** 构造面包屑（祖先目录链，不含根节点） */
export const buildTeamBreadcrumb = (teamId: number, parentId: number) =>
  teamPathIds(teamId, parentId).map((id) => ({
    id,
    name: getTeamDirs(teamId).get(id)!.name,
  }));

/** 收集目标目录及全部后代目录的 ID */
export function collectTeamDirSubtree(teamId: number, directoryId: number): number[] {
  const result: number[] = [directoryId];
  for (let i = 0; i < result.length; i += 1) {
    listTeamChildDirectories(teamId, result[i]).forEach((child) => result.push(child.id));
  }
  return result;
}

/* ------------------------------------------------------------------ *
 * 视图转换
 * ------------------------------------------------------------------ */

export function teamDirectoryToFileInfo(teamId: number, dir: TeamDirectory) {
  return {
    id: dir.id,
    originalName: dir.name,
    fileSize: 0,
    mimeType: "directory",
    createTime: dir.createTime,
    fileUrl: "",
    isDirectory: 1,
    parentId: dir.parentId,
    fullPath: teamPathIds(teamId, dir.id),
  };
}

export function teamFileToFileInfo(teamId: number, file: TeamFile) {
  return {
    id: file.id,
    originalName: file.originalName,
    fileSize: file.fileSize,
    mimeType: file.mimeType,
    createTime: file.createTime,
    fileUrl: file.fileUrl,
    isDirectory: 0,
    parentId: file.parentId,
    fullPath: teamPathIds(teamId, file.parentId),
    teamId,
    uploaderId: file.uploaderId,
  };
}

/** 列出目录下的全部条目（目录在前、文件在后） */
export const listTeamEntries = (teamId: number, parentId: number) => [
  ...listTeamChildDirectories(teamId, parentId).map((d) => teamDirectoryToFileInfo(teamId, d)),
  ...listTeamChildFiles(teamId, parentId).map((f) => teamFileToFileInfo(teamId, f)),
];

/** 个人文件树风格：目录递归 children，文件作为叶子 */
export function buildTeamFileTree(teamId: number, parentId = 0): any[] {
  const dirs = listTeamChildDirectories(teamId, parentId).map((dir) => ({
    ...teamDirectoryToFileInfo(teamId, dir),
    children: buildTeamFileTree(teamId, dir.id),
  }));
  const leafFiles = listTeamChildFiles(teamId, parentId).map((f) => teamFileToFileInfo(teamId, f));
  return [...dirs, ...leafFiles];
}

/* ------------------------------------------------------------------ *
 * 回收站
 * ------------------------------------------------------------------ */

/** 把文件移入团队回收站（从 files 中移除） */
export function moveTeamFileToTrash(
  teamId: number,
  file: TeamFile,
  deleterId: number
): TeamTrashItem {
  getTeamFiles(teamId).delete(file.id);
  const segments = teamPathIds(teamId, file.parentId).map(
    (id) => getTeamDirs(teamId).get(id)!.name
  );
  const item: TeamTrashItem = {
    id: teamTrashSequence.next(),
    originalName: file.originalName,
    path: `/${[...segments, file.originalName].join("/")}`,
    fileSize: file.fileSize,
    mimeType: file.mimeType,
    isDirectory: 0,
    parentId: file.parentId,
    deletedAt: now(),
    deleterId,
    expiresAt: daysFromNow(30),
  };
  getTeamTrash(teamId).set(item.id, item);
  return item;
}

/** 从回收站还原；原目录已不存在则还原到根目录 */
export function restoreTeamTrash(teamId: number, trashId: number): TeamFile | null {
  const trash = getTeamTrash(teamId);
  const item = trash.get(trashId);
  if (!item) return null;
  const parentId = item.parentId && getTeamDirs(teamId).has(item.parentId) ? item.parentId : 0;
  const file: TeamFile = {
    id: teamFileSequence.next(),
    originalName: item.originalName,
    fileSize: item.fileSize,
    mimeType: item.mimeType,
    createTime: now(),
    fileUrl: `/team/${teamId}/files/${item.originalName}`,
    parentId,
    uploaderId: item.deleterId,
  };
  getTeamFiles(teamId).set(file.id, file);
  trash.delete(trashId);
  return file;
}

/* ------------------------------------------------------------------ *
 * 配额 / 分享
 * ------------------------------------------------------------------ */

export const teamUsedSpace = (teamId: number): number =>
  Array.from(getTeamFiles(teamId).values()).reduce((sum, f) => sum + f.fileSize, 0);

/** 全团队范围唯一的分享 token */
export function uniqueTeamShareToken(): string {
  let token = randomToken();
  while (
    Array.from(sharesByTeam.values()).some((m) =>
      Array.from(m.values()).some((s) => s.shareToken === token)
    )
  ) {
    token = randomToken();
  }
  return token;
}

export function toTeamShareView(teamId: number, share: TeamShare) {
  const file = getTeamFiles(teamId).get(share.fileId);
  return {
    id: share.id,
    fileId: share.fileId,
    fileName: file?.originalName ?? "(文件已删除)",
    shareToken: share.shareToken,
    accessType: share.accessType,
    extractCode: share.extractCode,
    expireTime: share.expireTime,
    statusDesc:
      share.expireTime && new Date(share.expireTime).getTime() < Date.now()
        ? "expired"
        : share.statusDesc,
    createTime: share.createTime,
  };
}

/* ------------------------------------------------------------------ *
 * 角色 → 权限点映射（与 backend/sql/mysql/migration_20260514.sql 种子一致）
 * ------------------------------------------------------------------ */

import type { TeamRole } from "./team.state";

export const ROLE_PERMISSIONS: Record<TeamRole, string[]> = {
  Owner: [
    "team:manage",
    "team:dissolve",
    "owner:transfer",
    "member:invite",
    "member:remove",
    "role:update",
    "file:list",
    "file:detail",
    "file:download",
    "file:upload",
    "file:move",
    "file:copy",
    "file:delete",
    "share:create",
    "share:manage",
    "trash:list",
    "trash:restore",
    "trash:delete",
    "file:transfer:to-personal",
    "file:transfer:to-team",
  ],
  Admin: [
    "member:invite",
    "member:remove",
    "role:update",
    "file:list",
    "file:detail",
    "file:download",
    "file:upload",
    "file:move",
    "file:copy",
    "file:delete",
    "share:create",
    "share:manage",
    "trash:list",
    "trash:restore",
    "trash:delete",
    "file:transfer:to-personal",
    "file:transfer:to-team",
  ],
  Editor: [
    "file:list",
    "file:detail",
    "file:download",
    "file:upload",
    "file:move",
    "file:copy",
    "file:delete",
    "share:create",
  ],
  Viewer: ["file:list", "file:detail", "file:download"],
};

/* ------------------------------------------------------------------ *
 * 种子数据：演示团队（teamId=1）预置一组目录与文件
 * ------------------------------------------------------------------ */

(function seed() {
  const teamId = 1;
  const dirs = getTeamDirs(teamId);
  const files = getTeamFiles(teamId);
  const docsId = teamDirSequence.next();
  const meetingId = teamDirSequence.next();
  dirs.set(docsId, { id: docsId, name: "项目文档", parentId: 0, createTime: now() });
  dirs.set(meetingId, { id: meetingId, name: "会议纪要", parentId: docsId, createTime: now() });

  const introId = teamFileSequence.next();
  files.set(introId, {
    id: introId,
    originalName: "intro.md",
    fileSize: 1024,
    mimeType: "text/markdown",
    createTime: now(),
    fileUrl: `/team/${teamId}/files/intro.md`,
    parentId: docsId,
    uploaderId: 1,
  });
  const minutesId = teamFileSequence.next();
  files.set(minutesId, {
    id: minutesId,
    originalName: "kickoff.txt",
    fileSize: 2048,
    mimeType: "text/plain",
    createTime: now(),
    fileUrl: `/team/${teamId}/files/kickoff.txt`,
    parentId: meetingId,
    uploaderId: 1,
  });
})();
