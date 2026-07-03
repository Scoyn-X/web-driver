/** System 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const SYSTEM_BASE_URL = `${API_BASE}/system`;

// ==================== 数据模型 ====================

/** 系统配置响应 */
export interface SystemConfigResponseVO {
  /** 回收站保留秒数 */
  trashRetentionSeconds?: number;
  /** VIP降级私密空间宽限期秒数 */
  privateGracePeriodSeconds?: number;
  /** 清理任务间隔秒数 */
  cleanupIntervalSeconds?: number;
  /** 普通用户总配额 */
  normalTotalQuota?: string;
  /** 普通用户总配数字节数 */
  normalTotalQuotaBytes?: number;
  /** 普通用户单文件大小限制 */
  normalSingleFileLimit?: string;
  /** 普通用户单文件大小限制字节数 */
  normalSingleFileLimitBytes?: number;
  /** 下载限速触发阈值 */
  downloadThrottleThreshold?: string;
  /** 下载限速触发阈值字节数 */
  downloadThrottleThresholdBytes?: number;
  /** 普通用户下载限速 */
  normalDownloadSpeed?: string;
  /** 普通用户下载限速字节/秒 */
  normalDownloadBytesPerSecond?: number;
  /** 当前生效的 YAML 配置（只读参考） */
  yamlDefaults?: Record<string, unknown>;
}

/** 系统配置更新请求 */
export interface SystemConfigUpdateRequestVO {
  /** 回收站保留秒数（1-31536000，最大365天） */
  trashRetentionSeconds?: number;
  /** VIP降级私密空间宽限期秒数（1-31536000，最大365天） */
  privateGracePeriodSeconds?: number;
  /** 清理任务间隔秒数（1-86400） */
  cleanupIntervalSeconds?: number;
  /** 普通用户总配额字节数 */
  normalTotalQuota?: number;
  /** 普通用户单文件大小限制字节数 */
  normalSingleFileLimit?: number;
  /** 下载限速触发阈值字节数 */
  downloadThrottleThreshold?: number;
  /** 普通用户下载限速字节/秒 */
  normalDownloadBytesPerSecond?: number;
}

/** system 模块接口集合 */
export const SystemAPI = {
  /**
   * 获取系统配置
   *
   * `GET /api/v1/system/config`
   * @returns 系统配置响应
   */
  getConfig() {
    return request<SystemConfigResponseVO>({
      url: `${SYSTEM_BASE_URL}/config`,
      method: "get",
    });
  },

  /**
   * 修改系统配置
   *
   * `PUT /api/v1/system/config`
   * @param data 请求体 —— 系统配置更新请求
   * @returns 系统配置响应
   */
  updateConfig(data: SystemConfigUpdateRequestVO) {
    return request<SystemConfigResponseVO>({
      url: `${SYSTEM_BASE_URL}/config`,
      method: "put",
      data,
    });
  },

  /**
   * 触发回收站清理
   *
   * `POST /api/v1/system/cleanup/trigger`
   */
  triggerCleanup() {
    return request<string>({
      url: `${SYSTEM_BASE_URL}/cleanup/trigger`,
      method: "post",
    });
  },
};

export default SystemAPI;
