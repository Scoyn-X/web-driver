/**
 * 文件分享 mock，对应 `src/api/share.api.ts`。
 *
 * - /shares    需要登录，管理自己创建的分享。
 * - /s/:token  公开访问入口（前端以 no-auth 调用），含提取码校验。
 */
import { defineMock } from "vite-plugin-mock-dev-server";
import {
  badRequest,
  daysFromNow,
  fail,
  formatSize,
  forbidden,
  notFound,
  now,
  ok,
  randomCode,
} from "./shared";
import {
  directories,
  files,
  findShareByToken,
  isShareExpired,
  listEntries,
  shareSequence,
  shares,
  toShareInfo,
  uniqueShareToken,
  type MockShare,
} from "./data/file.state";

/** accessType: 1 表示需要提取码 */
const ACCESS_WITH_CODE = 1;

export default defineMock([
  /** 我创建的分享列表 */
  {
    url: "/dev-api/api/v1/shares",
    method: "GET",
    body: () => ok(Array.from(shares.values()).map(toShareInfo)),
  },

  /** 创建分享 */
  {
    url: "/dev-api/api/v1/shares",
    method: "POST",
    body: (req) => {
      const {
        fileId: rawFileId,
        accessType,
        expireDays,
        code,
      } = (req.body || {}) as {
        fileId?: number | string;
        accessType?: number;
        expireDays?: number;
        code?: string;
      };
      const fileId = Number(rawFileId);
      if (!fileId || !files.has(fileId)) {
        return badRequest(`分享的文件不存在 (fileId=${JSON.stringify(rawFileId)})`);
      }

      const type = Number(accessType ?? 0);
      const extractCode = code?.trim()
        ? code.trim().toUpperCase()
        : type === ACCESS_WITH_CODE
          ? randomCode()
          : null;

      const share: MockShare = {
        id: shareSequence.next(),
        fileId,
        shareToken: uniqueShareToken(),
        accessType: type,
        extractCode,
        expireTime: expireDays && expireDays > 0 ? daysFromNow(expireDays) : null,
        statusDesc: "active",
        createTime: now(),
      };
      shares.set(share.id, share);
      return ok(toShareInfo(share));
    },
  },

  /** 取消分享 */
  {
    url: "/dev-api/api/v1/shares/:id",
    method: "DELETE",
    body: (req) => {
      const id = Number(req.params.id);
      if (!shares.has(id)) return notFound("分享不存在");
      shares.delete(id);
      return ok(null);
    },
  },

  /** 公开访问：查看分享内容信息 */
  {
    url: "/dev-api/api/v1/s/:token",
    method: "GET",
    body: (req) => {
      const share = findShareByToken(String(req.params.token));
      if (!share) return notFound("分享不存在或已被取消");
      if (isShareExpired(share)) return fail("分享已过期", "A0102");

      const file = files.get(share.fileId);
      if (!file) return notFound("分享的文件已被删除");
      return ok({
        fileName: file.originalName,
        fileSize: file.fileSize,
        fileSizeFormatted: formatSize(file.fileSize),
        mimeType: file.mimeType,
        isDirectory: false,
        requireExtractCode: !!share.extractCode,
        fileUploadTime: file.createTime,
      });
    },
  },

  /** 公开访问：校验提取码 */
  {
    url: "/dev-api/api/v1/s/:token/verify",
    method: "POST",
    body: (req) => {
      const share = findShareByToken(String(req.params.token));
      if (!share) return notFound("分享不存在或已被取消");
      if (isShareExpired(share)) return fail("分享已过期", "A0102");

      const { extractCode } = (req.body || {}) as { extractCode?: string };
      if (!share.extractCode) return ok(null); // 无需提取码
      if (
        String(extractCode || "")
          .trim()
          .toUpperCase() !== share.extractCode
      ) {
        return forbidden("提取码错误");
      }
      return ok(null);
    },
  },

  /** 公开访问：列出分享的目录内容 */
  {
    url: "/dev-api/api/v1/s/:token/children",
    method: "GET",
    body: (req) => {
      const share = findShareByToken(String(req.params.token));
      if (!share) return notFound("分享不存在或已被取消");
      if (isShareExpired(share)) return fail("分享已过期", "A0102");

      if (share.extractCode) {
        const code = String(req.query.code || "")
          .trim()
          .toUpperCase();
        if (code !== share.extractCode) return forbidden("提取码错误");
      }

      if (!directories.has(share.fileId)) return ok([]);
      const parentId = Number(req.query.parentId) || share.fileId;
      return ok(listEntries(parentId));
    },
  },

  /** 公开访问：下载分享文件 */
  {
    url: "/dev-api/api/v1/s/:token/download",
    method: "GET",
    body: (req) => {
      const share = findShareByToken(String(req.params.token));
      if (!share) return notFound("分享不存在或已被取消");
      if (isShareExpired(share)) return fail("分享已过期", "A0102");

      const file = files.get(share.fileId);
      if (!file) return notFound("分享的文件已被删除");
      if (share.extractCode) {
        const code = String(req.query.code || "")
          .trim()
          .toUpperCase();
        if (code !== share.extractCode) return forbidden("提取码错误，无法下载");
      }
      return ok({
        downloadUrl: `/dev-api/mock-download/${share.shareToken}`,
        fileName: file.originalName,
      });
    },
  },
]);
