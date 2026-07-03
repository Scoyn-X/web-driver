/**
 * 个人空间 mock：含公开个人空间的目录 move，以及完整的私密空间。
 *
 * 私密空间所有操作（除 password / session / status 外）要求当前用户处于解锁会话内，
 * 否则返回 `forbidden("私密空间未解锁")`。
 */
import { defineMock } from "vite-plugin-mock-dev-server";
import { badRequest, conflict, forbidden, notFound, now, ok } from "./shared";
import { currentUserId } from "./data/user.state";
import { collectDirectorySubtree, directories as personalDirectories } from "./data/file.state";
import {
  SESSION_DURATION_MIN,
  type PrivateDirectory,
  type PrivateFile,
  computeSpaceState,
  getPrivateDirs,
  getPrivateFiles,
  getPrivateTrash,
  getSession,
  hasPassword,
  isUnlocked,
  listPrivateChildDirectories,
  listPrivateEntries,
  movePrivateFileToTrash,
  privateDirSequence,
  privateDirectoryToFileInfo,
  privateFileSequence,
  privateFileToFileInfo,
  restorePrivateTrash,
} from "./data/private-space.state";

/** 解析上传请求体 */
function readUpload(body: any) {
  const read = (key: string) =>
    body && typeof body.get === "function" ? body.get(key) : body?.[key];
  const field: any = read("file");
  const name: string = field?.name || field?.originalFilename || field?.filename || "未命名文件";
  const realSize = Number(field?.size);
  const size =
    Number.isFinite(realSize) && realSize > 0
      ? realSize
      : Math.floor(50_000 + Math.random() * 2_000_000);
  const mimeType: string = field?.type || field?.mimetype || "application/octet-stream";
  return { name, size, mimeType, parentId: Number(read("parentId")) || 0 };
}

/** 私密空间所有内容操作的统一鉴权 */
function unlockGuard(req: any) {
  const me = currentUserId(req);
  if (!hasPassword(me)) {
    return { ok: false as const, response: forbidden("请先设置私密空间密码") };
  }
  if (!isUnlocked(me)) {
    return { ok: false as const, response: forbidden("私密空间未解锁") };
  }
  return { ok: true as const, me };
}

export default defineMock([
  /* ---------------------------- 个人目录移动 ---------------------------- */

  /** 移动个人目录（公开空间） */
  {
    url: "/dev-api/api/v1/personal/directories/:directoryId/move",
    method: "PUT",
    body: (req) => {
      const directory = personalDirectories.get(Number(req.params.directoryId));
      if (!directory) return notFound("目录不存在");
      const { targetDirectoryId } = (req.body || {}) as { targetDirectoryId?: number };
      const target = Number(targetDirectoryId) || 0;
      if (target && !personalDirectories.has(target)) return notFound("目标目录不存在");
      if (collectDirectorySubtree(directory.id).includes(target)) {
        return badRequest("不能把目录移动到自身或其子目录下");
      }
      directory.parentId = target;
      return ok(null);
    },
  },

  /* ---------------------------- 私密空间会话 ---------------------------- */

  /** 设置 / 修改密码 */
  {
    url: "/dev-api/api/v1/personal/private-space/password",
    method: "PUT",
    body: (req) => {
      const me = currentUserId(req);
      const { oldPassword, password } = (req.body || {}) as {
        oldPassword?: string;
        password?: string;
      };
      if (!password || password.length < 4) return badRequest("密码长度不能少于 4 位");
      const session = getSession(me);
      if (session.password) {
        if (!oldPassword || oldPassword !== session.password) {
          return forbidden("旧密码不正确");
        }
      }
      session.password = password;
      session.unlockedUntil = undefined;
      return ok(null);
    },
  },

  /** 解锁会话 */
  {
    url: "/dev-api/api/v1/personal/private-space/session",
    method: "POST",
    body: (req) => {
      const me = currentUserId(req);
      const { password } = (req.body || {}) as { password?: string };
      const session = getSession(me);
      if (!session.password) return conflict("尚未设置私密空间密码");
      if (password !== session.password) return forbidden("密码错误");

      const until = new Date(Date.now() + SESSION_DURATION_MIN * 60_000).toISOString();
      session.unlockedUntil = until;
      return ok({ unlockedUntil: until });
    },
  },

  /** 私密空间状态 */
  {
    url: "/dev-api/api/v1/personal/private-space/status",
    method: "GET",
    body: (req) => {
      const me = currentUserId(req);
      const session = getSession(me);
      const state = computeSpaceState(me);
      return ok({
        state,
        unlockedUntil: isUnlocked(me) ? session.unlockedUntil : undefined,
        graceExpireAt: undefined,
        reminderMessage:
          state === "DISABLED"
            ? "尚未启用私密空间"
            : state === "LOCKED"
              ? "私密空间已锁定，请输入密码解锁"
              : "",
      });
    },
  },

  /* ---------------------------- 私密空间目录 ---------------------------- */

  /** 列出私密目录 */
  {
    url: "/dev-api/api/v1/personal/private-space/directories",
    method: "GET",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const parentId = Number(req.query.parentId || 0);
      return ok(
        listPrivateChildDirectories(g.me, parentId).map((d) => ({
          id: d.id,
          name: d.name,
          parentId: d.parentId,
          hasChildren: listPrivateChildDirectories(g.me, d.id).length > 0,
        }))
      );
    },
  },

  /** 新建私密目录 */
  {
    url: "/dev-api/api/v1/personal/private-space/directories",
    method: "POST",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const { name, parentId } = (req.body || {}) as { name?: string; parentId?: number };
      if (!name || !name.trim()) return badRequest("目录名不能为空");
      const parent = Number(parentId) || 0;
      if (parent && !getPrivateDirs(g.me).has(parent)) return notFound("父目录不存在");
      if (listPrivateChildDirectories(g.me, parent).some((d) => d.name === name.trim())) {
        return conflict("同级目录下已存在同名目录");
      }

      const directory: PrivateDirectory = {
        id: privateDirSequence.next(),
        name: name.trim(),
        parentId: parent,
        createTime: now(),
      };
      getPrivateDirs(g.me).set(directory.id, directory);
      return ok(privateDirectoryToFileInfo(g.me, directory));
    },
  },

  /* ---------------------------- 私密空间文件 ---------------------------- */

  /** 列出私密文件（含目录条目，统一为 file-info） */
  {
    url: "/dev-api/api/v1/personal/private-space/files",
    method: "GET",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const parentId = Number(req.query.parentId || 0);
      return ok(listPrivateEntries(g.me, parentId));
    },
  },

  /** 上传私密文件 */
  {
    url: "/dev-api/api/v1/personal/private-space/files",
    method: "POST",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const { name, size, mimeType, parentId } = readUpload(req.body);
      if (parentId && !getPrivateDirs(g.me).has(parentId)) return notFound("父目录不存在");

      const file: PrivateFile = {
        id: privateFileSequence.next(),
        originalName: name,
        fileSize: size,
        mimeType,
        createTime: now(),
        fileUrl: `/private/${name}`,
        parentId,
      };
      getPrivateFiles(g.me).set(file.id, file);
      return ok(privateFileToFileInfo(g.me, file));
    },
  },

  /** 私密文件详情 */
  {
    url: "/dev-api/api/v1/personal/private-space/files/:fileId",
    method: "GET",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const file = getPrivateFiles(g.me).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      return ok(privateFileToFileInfo(g.me, file));
    },
  },

  /** 删除私密文件（移入私密回收站） */
  {
    url: "/dev-api/api/v1/personal/private-space/files/:fileId",
    method: "DELETE",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const file = getPrivateFiles(g.me).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      movePrivateFileToTrash(g.me, file);
      return ok(null);
    },
  },

  /** 下载私密文件 */
  {
    url: "/dev-api/api/v1/personal/private-space/files/:fileId/download",
    method: "GET",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const file = getPrivateFiles(g.me).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      return ok({ downloadUrl: file.fileUrl, fileName: file.originalName });
    },
  },

  /** 移动私密文件 */
  {
    url: "/dev-api/api/v1/personal/private-space/files/:fileId/move",
    method: "PUT",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const file = getPrivateFiles(g.me).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      const { targetDirectoryId } = (req.body || {}) as { targetDirectoryId?: number };
      const target = Number(targetDirectoryId) || 0;
      if (target && !getPrivateDirs(g.me).has(target)) return notFound("目标目录不存在");
      if (target === file.parentId) return conflict("文件已在目标目录中");
      file.parentId = target;
      return ok(null);
    },
  },

  /* ---------------------------- 私密回收站 ---------------------------- */

  /** 私密回收站列表 */
  {
    url: "/dev-api/api/v1/personal/private-space/trash",
    method: "GET",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      return ok(Array.from(getPrivateTrash(g.me).values()));
    },
  },

  /** 彻底删除私密回收站项 */
  {
    url: "/dev-api/api/v1/personal/private-space/trash/:trashId",
    method: "DELETE",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const trash = getPrivateTrash(g.me);
      const id = Number(req.params.trashId);
      if (!trash.has(id)) return notFound("回收站项不存在");
      trash.delete(id);
      return ok(null);
    },
  },

  /** 恢复私密回收站项 */
  {
    url: "/dev-api/api/v1/personal/private-space/trash/:trashId/restore",
    method: "POST",
    body: (req) => {
      const g = unlockGuard(req);
      if (!g.ok) return g.response;
      const restored = restorePrivateTrash(g.me, Number(req.params.trashId));
      if (!restored) return notFound("回收站项不存在");
      return ok(privateFileToFileInfo(g.me, restored));
    },
  },
]);
