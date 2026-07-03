/** Share 模块接口 */

import request from "@/utils/request";
import { API_BASE } from "./config";

const S_BASE_URL = `${API_BASE}/s`;
const SHARES_BASE_URL = `${API_BASE}/shares`;

// ==================== 数据模型 ====================

/** 目录路径面包屑节点 */
export interface BreadcrumbItemResponseVO {
  /** 目录ID（0表示根目录） */
  id?: number;
  /** 目录名称 */
  name?: string;
}

/** 文件信息视图对象 */
export interface FileInfoResponseVO {
  /** 文件ID */
  id?: number;
  /** 文件原始名称 */
  originalName?: string;
  /** 文件大小（字节） */
  fileSize?: number;
  /** 文件MIME类型 */
  mimeType?: string;
  /** 上传时间 */
  createTime?: string;
  /** 文件访问URL */
  fileUrl?: string;
  /** 是否为目录（0=文件 1=目录） */
  isDirectory?: number;
  /** 父目录ID（0表示根目录） */
  parentId?: number;
  /** 祖先ID路径 */
  fullPath?: unknown[];
}

export interface FileListResponseVO {
  /** 从根到当前目录的面包屑（按层级顺序） */
  breadcrumb?: BreadcrumbItemResponseVO[];
  /** 当前目录下的文件与子目录混合列表 */
  items?: FileInfoResponseVO[];
}

/** 分享访问页响应 */
export interface ShareAccessResponseVO {
  /** 文件名 */
  fileName?: string;
  /** 文件大小（字节） */
  fileSize?: number;
  /** 文件大小易读展示，如 3.45 MB */
  fileSizeFormatted?: string;
  /** 文件 MIME 类型 */
  mimeType?: string;
  /** 是否为目录 */
  isDirectory?: boolean;
  /** 是否需要输入提取码 */
  requireExtractCode?: boolean;
  /** 文件上传时间 */
  fileUploadTime?: string;
}

/** 创建分享请求 */
export interface ShareCreateRequestVO {
  /** 被分享的文件/目录ID */
  fileId: number;
  /** 访问方式(0-全公开 1-分享码访问) */
  accessType: number;
  /** 有效天数(1/7/30)，null 表示永久有效 */
  expireDays?: number;
}

/** 分享下载响应 */
export interface ShareDownloadResponseVO {
  /** 预签名下载 URL（5 分钟有效） */
  downloadUrl?: string;
  /** 文件名（便于前端设置下载文件名） */
  fileName?: string;
}

/** 分享信息 */
export interface ShareInfoResponseVO {
  /** 分享记录ID */
  id?: number;
  /** 被分享的文件/目录ID */
  fileId?: number;
  /** 文件名；若文件已删除，显示 [已删除] */
  fileName?: string;
  /** 是否为目录；文件记录缺失时为 null */
  isDirectory?: boolean;
  /** 分享 token，前端拼接为 /s/{token} */
  shareToken?: string;
  /** 访问方式(0-全公开 1-分享码访问) */
  accessType?: number;
  /** 提取码（仅 accessType=1 时返回给创建者） */
  extractCode?: string;
  /** 过期时间，null 表示永久有效 */
  expireTime?: string;
  /** 当前状态文案：有效 / 已过期 / 已取消 */
  statusDesc?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 校验分享提取码请求 */
export interface ShareVerifyRequestVO {
  /** 4-6 位字母数字提取码 */
  extractCode: string;
}

/** share 模块接口集合 */
export const ShareAPI = {
  /**
   * 列出个人分享
   *
   * `GET /api/v1/shares`
   */
  listMyShares() {
    return request<ShareInfoResponseVO[]>({
      url: SHARES_BASE_URL,
      method: "get",
    });
  },

  /**
   * 创建个人分享
   *
   * `POST /api/v1/shares`
   * @param data 请求体 —— 创建分享请求
   * @returns 分享信息
   */
  createShare(data: ShareCreateRequestVO) {
    return request<ShareInfoResponseVO>({
      url: SHARES_BASE_URL,
      method: "post",
      data,
    });
  },

  /**
   * 取消个人分享
   *
   * `DELETE /api/v1/shares/{id}`
   * @param id 路径参数
   */
  cancelShare(id: number) {
    return request<void>({
      url: `${SHARES_BASE_URL}/${id}`,
      method: "delete",
    });
  },

  /**
   * 校验分享提取码
   *
   * `POST /api/v1/s/{shareToken}/verify`
   * @param shareToken 路径参数
   * @param data 请求体 —— 校验分享提取码请求
   */
  verifyExtractCode(shareToken: string, data: ShareVerifyRequestVO) {
    return request<void>({
      url: `${S_BASE_URL}/${shareToken}/verify`,
      method: "post",
      data,
    });
  },

  /**
   * 访问分享链接，获取被分享文件的基本信息
   *
   * `GET /api/v1/s/{shareToken}`
   * @param shareToken 路径参数
   * @returns 分享访问页响应
   */
  getShare(shareToken: string) {
    return request<ShareAccessResponseVO>({
      url: `${S_BASE_URL}/${shareToken}`,
      method: "get",
    });
  },

  /**
   * 获取分享文件的预签名下载 URL
   *
   * `GET /api/v1/s/{shareToken}/download`
   * @param shareToken 路径参数
   * @param code 查询参数
   * @returns 分享下载响应
   */
  download(shareToken: string, code?: string, fileId?: number) {
    return request<ShareDownloadResponseVO>({
      url: `${S_BASE_URL}/${shareToken}/download`,
      method: "get",
      params: { code, fileId },
    });
  },

  /**
   * 列出分享目录内容
   *
   * `GET /api/v1/s/{shareToken}/children`
   * @param shareToken 路径参数
   * @param parentId 查询参数
   * @param code 查询参数
   */
  getSShareTokenChildren(shareToken: string, parentId: number = 0, code?: string) {
    return request<FileListResponseVO>({
      url: `${S_BASE_URL}/${shareToken}/children`,
      method: "get",
      params: { parentId, code },
    });
  },

  /**
   * SSE 进度下载分享文件
   *
   * `GET /api/v1/s/{shareToken}/download/progress`
   * @param shareToken 路径参数
   * @param code 查询参数
   * @param fileId 分享目录内的文件ID，null 表示分享根节点
   * @param onStart 开始回调
   * @param onProgress 进度回调
   * @param onChunk Base64 数据块回调
   * @param onComplete 完成回调
   * @param onError 错误回调
   * @returns 取消函数
   */
  downloadWithProgress(
    shareToken: string,
    code: string | undefined,
    fileId: number | undefined,
    onStart: (fileName: string, totalSize: number) => void,
    onProgress: (percent: number, speed: number) => void,
    onChunk: (base64: string) => void,
    onComplete: () => void,
    onError: (message: string) => void
  ): () => void {
    const params = new URLSearchParams();
    if (code) params.set("code", code);
    if (fileId) params.set("fileId", String(fileId));
    const base = import.meta.env.VITE_APP_BASE_API || "";
    const url = `${base}/api/v1/s/${shareToken}/download/progress?${params}`;
    const token = localStorage.getItem("token");
    const controller = new AbortController();
    let aborted = false;

    fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) {
          const text = await response.text().catch(() => "");
          onError(text || `服务器错误 (${response.status})`);
          return;
        }
        const reader = response.body?.getReader();
        if (!reader) {
          onError("浏览器不支持流式读取");
          return;
        }

        const decoder = new TextDecoder();
        let buffer = "";
        const chunks: string[] = [];

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() || "";

          let eventType = "";
          for (const line of lines) {
            if (line.startsWith("event:")) {
              eventType = line.slice(6).trim();
            } else if (line.startsWith("data:")) {
              const raw = line.slice(5).trim();
              try {
                const data = JSON.parse(raw);
                switch (eventType || data.type) {
                  case "start":
                    onStart(data.fileName || "", data.totalSize || 0);
                    break;
                  case "progress":
                    onProgress(data.percent || 0, data.speed || 0);
                    break;
                  case "chunk": {
                    const b64 = data.raw ?? data.data ?? raw;
                    chunks.push(b64);
                    onChunk(b64);
                    break;
                  }
                  case "complete":
                    onComplete();
                    // Assemble blob and trigger download
                    if (chunks.length) {
                      const bytes = chunks.map((c) =>
                        Uint8Array.from(atob(c), (ch) => ch.charCodeAt(0))
                      );
                      const totalLen = bytes.reduce((s, a) => s + a.length, 0);
                      const merged = new Uint8Array(totalLen);
                      let offset = 0;
                      for (const arr of bytes) {
                        merged.set(arr, offset);
                        offset += arr.length;
                      }
                      const blob = new Blob([merged]);
                      const blobUrl = URL.createObjectURL(blob);
                      const a = document.createElement("a");
                      a.href = blobUrl;
                      a.download = data.fileName || "download";
                      a.click();
                      URL.revokeObjectURL(blobUrl);
                    }
                    break;
                  case "error":
                    onError(data.message || "下载失败");
                    break;
                }
              } catch {
                // non-JSON line, skip
              }
              eventType = "";
            }
          }
        }
      })
      .catch((err) => {
        if (err.name === "AbortError") {
          aborted = true;
          return;
        }
        onError(err.message || "网络请求失败");
      });

    return () => {
      controller.abort();
    };
  },
};

export default ShareAPI;
