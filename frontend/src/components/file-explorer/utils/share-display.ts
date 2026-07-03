/**
 * 分享展示相关的纯函数：分享链接拼接、状态归一化、提取码取值。
 * 原先散落在 QuickShareDialog / ShareDialog 中重复实现，此处统一收口。
 */
import type { ShareInfoResponseVO } from "@/api/share.api";

type TagType = "success" | "warning" | "danger";

/** 分享状态枚举 */
export type ShareStatus = "valid" | "expired" | "deleted";

const STATUS_LABEL: Record<ShareStatus, string> = {
  valid: "有效",
  expired: "已过期",
  deleted: "已删除",
};

const STATUS_TAG: Record<ShareStatus, TagType> = {
  valid: "success",
  expired: "warning",
  deleted: "danger",
};

/** 拼接分享访问链接，无 token 时返回占位符 */
export function buildShareLink(token?: string): string {
  return token ? `${window.location.origin}/#/s/${token}` : "-";
}

/** 根据后端 statusDesc 归一化分享状态 */
export function resolveShareStatus(share: ShareInfoResponseVO): ShareStatus {
  const desc = share.statusDesc ?? "";
  if (desc.includes("删除")) return "deleted";
  if (desc.includes("过期")) return "expired";
  return "valid";
}

/** 分享状态展示文案 */
export function shareStatusLabel(share: ShareInfoResponseVO): string {
  return STATUS_LABEL[resolveShareStatus(share)];
}

/** 分享状态对应的 el-tag 类型 */
export function shareStatusTagType(share: ShareInfoResponseVO): TagType {
  return STATUS_TAG[resolveShareStatus(share)];
}

/** 分享条目的提取码，缺失时返回占位符 */
export function shareExtractCode(share: ShareInfoResponseVO): string {
  return share.extractCode || "-";
}

/** 分享文件是否已被删除（后端以文件名含“已删除”标记） */
export function isSharedFileDeleted(share: ShareInfoResponseVO): boolean {
  return (share.fileName ?? "").includes("已删除");
}

/** 解析分享条目的 FileIcon 类型标识 */
export function resolveShareFileType(share: ShareInfoResponseVO): string {
  if (isSharedFileDeleted(share)) return "file";
  if (share.isDirectory) return "folder";
  const ext = (share.fileName ?? "").split(".").pop()?.toLowerCase() ?? "";
  return ext || "file";
}

/** 分享条目的文件类型展示文案 */
export function formatShareFileType(share: ShareInfoResponseVO): string {
  if (isSharedFileDeleted(share)) return "-";
  if (share.isDirectory) return "文件夹";
  const ext = (share.fileName ?? "").split(".").pop()?.trim().toLowerCase() ?? "";
  return ext ? ext.toUpperCase() : "文件";
}
