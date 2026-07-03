/**
 * 个人私密空间 Mock 状态：按 userId 索引的目录 / 文件 / 回收站，外加密码与解锁会话。
 *
 * dev server 重启即重置。私密空间未设密码视为 DISABLED；已设密码后未解锁视为 LOCKED；
 * 解锁会话过期回到 LOCKED。
 */
import { createSequence, daysFromNow, now } from "../shared";

export type PrivateSpaceState = "DISABLED" | "ACTIVE" | "GRACE_PERIOD" | "LOCKED" | "EXPIRED";

export interface PrivateDirectory {
  id: number;
  name: string;
  parentId: number;
  createTime: string;
}

export interface PrivateFile {
  id: number;
  originalName: string;
  fileSize: number;
  mimeType: string;
  createTime: string;
  fileUrl: string;
  parentId: number;
}

export interface PrivateTrashItem {
  id: number;
  originalName: string;
  path: string;
  fileSize: number;
  mimeType: string;
  isDirectory: number;
  parentId: number;
  deletedAt: string;
  expiresAt: string;
}

interface PrivateSession {
  password?: string;
  unlockedUntil?: string;
}

const dirsByUser = new Map<number, Map<number, PrivateDirectory>>();
const filesByUser = new Map<number, Map<number, PrivateFile>>();
const trashByUser = new Map<number, Map<number, PrivateTrashItem>>();
const sessionByUser = new Map<number, PrivateSession>();

export const privateDirSequence = createSequence(5000);
export const privateFileSequence = createSequence(6000);
export const privateTrashSequence = createSequence(7000);

/** 解锁会话有效期 */
export const SESSION_DURATION_MIN = 30;

export function getPrivateDirs(userId: number): Map<number, PrivateDirectory> {
  let map = dirsByUser.get(userId);
  if (!map) {
    map = new Map();
    dirsByUser.set(userId, map);
  }
  return map;
}

export function getPrivateFiles(userId: number): Map<number, PrivateFile> {
  let map = filesByUser.get(userId);
  if (!map) {
    map = new Map();
    filesByUser.set(userId, map);
  }
  return map;
}

export function getPrivateTrash(userId: number): Map<number, PrivateTrashItem> {
  let map = trashByUser.get(userId);
  if (!map) {
    map = new Map();
    trashByUser.set(userId, map);
  }
  return map;
}

export function getSession(userId: number): PrivateSession {
  let session = sessionByUser.get(userId);
  if (!session) {
    session = {};
    sessionByUser.set(userId, session);
  }
  return session;
}

/** 是否已设置密码 */
export const hasPassword = (userId: number): boolean => !!getSession(userId).password;

/** 当前会话是否在有效期内 */
export function isUnlocked(userId: number): boolean {
  const session = getSession(userId);
  if (!session.unlockedUntil) return false;
  return new Date(session.unlockedUntil).getTime() > Date.now();
}

/** 推导私密空间状态 */
export function computeSpaceState(userId: number): PrivateSpaceState {
  if (!hasPassword(userId)) return "DISABLED";
  return isUnlocked(userId) ? "ACTIVE" : "LOCKED";
}

/* ------------------------------------------------------------------ *
 * 目录树查询
 * ------------------------------------------------------------------ */

export const listPrivateChildDirectories = (userId: number, parentId: number) =>
  Array.from(getPrivateDirs(userId).values()).filter((d) => d.parentId === parentId);

export const listPrivateChildFiles = (userId: number, parentId: number) =>
  Array.from(getPrivateFiles(userId).values()).filter((f) => f.parentId === parentId);

export function privatePathIds(userId: number, directoryId: number): number[] {
  const dirs = getPrivateDirs(userId);
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

export function privateDirectoryToFileInfo(userId: number, dir: PrivateDirectory) {
  return {
    id: dir.id,
    originalName: dir.name,
    fileSize: 0,
    mimeType: "directory",
    createTime: dir.createTime,
    fileUrl: "",
    isDirectory: 1,
    parentId: dir.parentId,
    fullPath: privatePathIds(userId, dir.id),
  };
}

export function privateFileToFileInfo(userId: number, file: PrivateFile) {
  return {
    id: file.id,
    originalName: file.originalName,
    fileSize: file.fileSize,
    mimeType: file.mimeType,
    createTime: file.createTime,
    fileUrl: file.fileUrl,
    isDirectory: 0,
    parentId: file.parentId,
    fullPath: privatePathIds(userId, file.parentId),
  };
}

export const listPrivateEntries = (userId: number, parentId: number) => [
  ...listPrivateChildDirectories(userId, parentId).map((d) =>
    privateDirectoryToFileInfo(userId, d)
  ),
  ...listPrivateChildFiles(userId, parentId).map((f) => privateFileToFileInfo(userId, f)),
];

/* ------------------------------------------------------------------ *
 * 回收站
 * ------------------------------------------------------------------ */

export function movePrivateFileToTrash(userId: number, file: PrivateFile): PrivateTrashItem {
  getPrivateFiles(userId).delete(file.id);
  const segments = privatePathIds(userId, file.parentId).map(
    (id) => getPrivateDirs(userId).get(id)!.name
  );
  const item: PrivateTrashItem = {
    id: privateTrashSequence.next(),
    originalName: file.originalName,
    path: `/${[...segments, file.originalName].join("/")}`,
    fileSize: file.fileSize,
    mimeType: file.mimeType,
    isDirectory: 0,
    parentId: file.parentId,
    deletedAt: now(),
    expiresAt: daysFromNow(30),
  };
  getPrivateTrash(userId).set(item.id, item);
  return item;
}

export function restorePrivateTrash(userId: number, trashId: number): PrivateFile | null {
  const trash = getPrivateTrash(userId);
  const item = trash.get(trashId);
  if (!item) return null;
  const parentId = item.parentId && getPrivateDirs(userId).has(item.parentId) ? item.parentId : 0;
  const file: PrivateFile = {
    id: privateFileSequence.next(),
    originalName: item.originalName,
    fileSize: item.fileSize,
    mimeType: item.mimeType,
    createTime: now(),
    fileUrl: `/private/${item.originalName}`,
    parentId,
  };
  getPrivateFiles(userId).set(file.id, file);
  trash.delete(trashId);
  return file;
}
