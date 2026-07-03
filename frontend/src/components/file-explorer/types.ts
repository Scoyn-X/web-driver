/**
 * system/file 包内共享类型定义。
 */

/** 文件转移操作（移动 / 复制） */
export type TransferAction = "move" | "copy";

/** 文件管理页签标识 */
export type FileManagerTab = "files" | "share" | "recycle";

export type { ViewMode } from "./utils/file-display";
