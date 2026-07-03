/**
 * 文件展示相关的纯函数：类型判定、图标解析、表格列格式化。
 * 原先散落在 index.vue / FileGrid / RecycleBinPanel 中重复实现，此处统一收口。
 */
import type { FileInfoResponseVO as FileInfo } from "@/api/file.api";
import type { RecycleBinItemResponseVO } from "@/api/recycle-bin.api";
import { formatSize } from "@/utils/format-size";

/** 文件列表视图模式 */
export type ViewMode = "list" | "grid";

/** 仅需判定类型与展示的最小文件结构 */
type DisplayFile = Pick<FileInfo, "originalName" | "isDirectory"> & {
  mimeType?: string;
};

/** 归一化目录 ID：非法值（NaN、负数）一律回退为根目录 0 */
export function normalizeDirectoryId(value: unknown): number {
  const num = Number(value);
  return Number.isFinite(num) && num >= 0 ? num : 0;
}

/** 是否为目录 */
export function isDirectory(file: Pick<FileInfo, "isDirectory">): boolean {
  return Number(file.isDirectory) === 1;
}

/** 解析文件名后缀（小写，无后缀返回空串） */
export function getExtension(name: string): string {
  return name.split(".").pop()?.trim().toLowerCase() ?? "";
}

/** 解析 FileIcon 组件所需的类型标识 */
export function resolveFileType(file: DisplayFile | RecycleBinItemResponseVO): string {
  if (Number(file.isDirectory) === 1) return "folder";
  const ext = getExtension(file.originalName);
  if (ext) return ext;
  const mimeType = "mimeType" in file ? file.mimeType : undefined;
  if (mimeType?.startsWith("image/")) return "image";
  if (mimeType?.startsWith("audio/")) return "mp3";
  if (mimeType?.startsWith("video/")) return "mp4";
  return "file";
}

/** 文件类型展示文案（表格“类型”列） */
export function formatFileType(file: Pick<FileInfo, "originalName" | "isDirectory">): string {
  if (Number(file.isDirectory) === 1) return "文件夹";
  const ext = getExtension(file.originalName);
  return ext ? ext.toUpperCase() : "文件";
}

/** 文件大小展示文案，目录返回占位符 */
export function formatFileSize(
  file: Pick<FileInfo, "isDirectory">,
  bytes: number,
  placeholder = ""
): string {
  if (Number(file.isDirectory) === 1) return placeholder;
  return formatSize(bytes || 0);
}
