/**
 * Mock 服务公共模块：统一响应体、业务状态码与通用工具。
 *
 * 所有 *.mock.ts 路由文件与 data/*.state.ts 状态文件共享这里的定义，
 * 以保证 mock 返回的数据结构与后端 `Result<T>` 完全一致。
 */

/** 后端统一响应体 */
export interface ApiResult<T = unknown> {
  code: string;
  msg: string;
  data: T;
}

/** 业务状态码（对齐后端 ResultCode 约定） */
export const ResultCode = {
  /** 成功 */
  SUCCESS: "00000",
  /** 参数校验失败 */
  PARAM_INVALID: "A0001",
  /** 资源不存在 */
  NOT_FOUND: "A0100",
  /** 资源冲突（已存在 / 重复操作） */
  CONFLICT: "A0102",
  /** 目标对象非法（如目标用户不是成员） */
  TARGET_INVALID: "A0103",
  /** 无操作权限 */
  FORBIDDEN: "A0301",
  /** 业务规则不允许该操作 */
  NOT_ALLOWED: "A0302",
  /** 兜底错误 */
  ERROR: "B0001",
} as const;

/** 成功响应 */
export function ok<T>(data: T): ApiResult<T> {
  return { code: ResultCode.SUCCESS, msg: "ok", data };
}

/** 失败响应（默认兜底错误码） */
export function fail(msg: string, code: string = ResultCode.ERROR): ApiResult<null> {
  return { code, msg, data: null };
}

/** 参数校验失败 */
export const badRequest = (msg: string) => fail(msg, ResultCode.PARAM_INVALID);
/** 资源不存在 */
export const notFound = (msg: string) => fail(msg, ResultCode.NOT_FOUND);
/** 资源冲突 */
export const conflict = (msg: string) => fail(msg, ResultCode.CONFLICT);
/** 无权限 */
export const forbidden = (msg: string) => fail(msg, ResultCode.FORBIDDEN);

/** 当前时间 ISO 字符串 */
export const now = () => new Date().toISOString();
/** n 天后的 ISO 字符串（n 为负即 n 天前） */
export const daysFromNow = (days: number) =>
  new Date(Date.now() + days * 86_400_000).toISOString();
/** n 天前的 ISO 字符串 */
export const daysAgo = (days: number) => daysFromNow(-days);

/**
 * 自增 ID 生成器，替代各处手写的 `++counter`。
 * @param start 起始值，首次 next() 返回 start + 1
 */
export function createSequence(start: number) {
  let value = start;
  return {
    /** 取下一个 ID */
    next: () => (value += 1),
    /** 查看当前值（不自增） */
    peek: () => value,
  };
}

/** 把字节数格式化为带单位的可读字符串，如 1536 -> "1.5 KB" */
export function formatSize(bytes: number): string {
  if (!bytes || bytes < 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${unit === 0 ? value : Number(value.toFixed(2))} ${units[unit]}`;
}

/** 生成随机提取码（大写字母+数字，去除易混字符） */
export function randomCode(length = 4): string {
  const chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
  let out = "";
  for (let i = 0; i < length; i += 1) {
    out += chars[Math.floor(Math.random() * chars.length)];
  }
  return out;
}

/** 生成随机分享 token */
export const randomToken = () => Math.random().toString(36).slice(2, 12);
