/**
 * 团队存储 mock，对应 `src/api/generated/team.api.ts` 的文件 / 目录 / 回收站 / 分享 / 配额 / 权限部分。
 *
 * 状态来自 `data/team-storage.state.ts`；个人 ↔ 团队的转存复用 `data/file.state.ts`。
 */
import { defineMock } from "vite-plugin-mock-dev-server";
import {
  badRequest,
  conflict,
  daysFromNow,
  forbidden,
  notFound,
  now,
  ok,
  randomCode,
} from "./shared";
import { currentUserId } from "./data/user.state";
import { files as personalFiles, fileSequence as personalFileSequence } from "./data/file.state";
import { isDissolved, roleOf, teams } from "./data/team.state";
import {
  ROLE_PERMISSIONS,
  type TeamDirectory,
  type TeamFile,
  type TeamShare,
  buildTeamBreadcrumb,
  buildTeamFileTree,
  collectTeamDirSubtree,
  getTeamDirs,
  getTeamFiles,
  getTeamShares,
  getTeamTrash,
  listTeamChildDirectories,
  listTeamChildFiles,
  listTeamEntries,
  moveTeamFileToTrash,
  restoreTeamTrash,
  teamDirSequence,
  teamFileSequence,
  teamFileToFileInfo,
  teamDirectoryToFileInfo,
  teamShareSequence,
  teamUsedSpace,
  toTeamShareView,
  uniqueTeamShareToken,
} from "./data/team-storage.state";

const ACCESS_WITH_CODE = 1;

/** 解析上传请求体（兼容 FormData 与对象） */
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

/** 校验 teamId 有效并且当前用户是成员；返回成员角色或失败响应 */
function memberGuard(req: any) {
  const teamId = Number(req.params.id);
  const team = teams.get(teamId);
  if (!team) return { ok: false as const, response: notFound("团队不存在") };
  if (team.dissolved) return { ok: false as const, response: conflict("团队已解散") };
  const me = currentUserId(req);
  const role = roleOf(teamId, me);
  if (!role) return { ok: false as const, response: forbidden("无权限访问该团队") };
  return { ok: true as const, teamId, me, role };
}

/** 校验当前角色拥有指定权限点 */
function requirePerm(role: string, perm: string): boolean {
  return (ROLE_PERMISSIONS[role as keyof typeof ROLE_PERMISSIONS] || []).includes(perm);
}

export default defineMock([
  /* ---------------------------- 配额 / 权限 ---------------------------- */

  /** 团队配额 */
  {
    url: "/dev-api/api/v1/team/:id/quota",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      const team = teams.get(g.teamId)!;
      const used = teamUsedSpace(g.teamId);
      return ok({
        totalQuota: team.totalQuota,
        usedSpace: used,
        remainingSpace: Math.max(0, team.totalQuota - used),
      });
    },
  },

  /** 我在团队内的权限 */
  {
    url: "/dev-api/api/v1/team/:id/permissions",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      const team = teams.get(g.teamId)!;
      return ok({
        teamId: g.teamId,
        teamName: team.name,
        teamStatus: team.dissolved ? "DISSOLVED" : "ACTIVE",
        role: g.role,
        permissions: ROLE_PERMISSIONS[g.role] ?? [],
        quotaState: teamUsedSpace(g.teamId) >= team.totalQuota ? "OVER_LIMIT" : "NORMAL",
        vipState: "NORMAL",
        singleFileLimit: 100 * 1024 * 1024,
        downloadLimited: false,
      });
    },
  },

  /* ---------------------------- 目录 ---------------------------- */

  /** 列出子目录 */
  {
    url: "/dev-api/api/v1/team/:id/directories",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      const parentId = Number(req.query.parentId || 0);
      return ok(
        listTeamChildDirectories(g.teamId, parentId).map((d) => ({
          id: d.id,
          name: d.name,
          parentId: d.parentId,
          hasChildren: listTeamChildDirectories(g.teamId, d.id).length > 0,
        }))
      );
    },
  },

  /** 新建目录 */
  {
    url: "/dev-api/api/v1/team/:id/directories",
    method: "POST",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:upload")) return forbidden("无权创建目录");

      const { name, parentId } = (req.body || {}) as { name?: string; parentId?: number };
      if (!name || !name.trim()) return badRequest("目录名不能为空");
      const parent = Number(parentId) || 0;
      if (parent && !getTeamDirs(g.teamId).has(parent)) return notFound("父目录不存在");
      if (listTeamChildDirectories(g.teamId, parent).some((d) => d.name === name.trim())) {
        return conflict("同级目录下已存在同名目录");
      }

      const directory: TeamDirectory = {
        id: teamDirSequence.next(),
        name: name.trim(),
        parentId: parent,
        createTime: now(),
      };
      getTeamDirs(g.teamId).set(directory.id, directory);
      return ok(teamDirectoryToFileInfo(g.teamId, directory));
    },
  },

  /** 重命名目录 */
  {
    url: "/dev-api/api/v1/team/:id/directories/:directoryId/rename",
    method: "PUT",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:move")) return forbidden("无权重命名目录");

      const directory = getTeamDirs(g.teamId).get(Number(req.params.directoryId));
      if (!directory) return notFound("目录不存在");
      const { name } = (req.body || {}) as { name?: string };
      if (!name || !name.trim()) return badRequest("目录名不能为空");
      if (
        listTeamChildDirectories(g.teamId, directory.parentId).some(
          (d) => d.id !== directory.id && d.name === name.trim()
        )
      ) {
        return conflict("同级目录下已存在同名目录");
      }
      directory.name = name.trim();
      return ok(teamDirectoryToFileInfo(g.teamId, directory));
    },
  },

  /* ---------------------------- 文件 ---------------------------- */

  /** 列出文件（含面包屑） */
  {
    url: "/dev-api/api/v1/team/:id/files",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:list")) return forbidden("无权查看文件");

      const parentId = Number(req.query.parentId || 0);
      return ok({
        breadcrumb: buildTeamBreadcrumb(g.teamId, parentId),
        items: listTeamEntries(g.teamId, parentId),
      });
    },
  },

  /** 上传文件 */
  {
    url: "/dev-api/api/v1/team/:id/files",
    method: "POST",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:upload")) return forbidden("无权上传文件");

      const { name, size, mimeType, parentId } = readUpload(req.body);
      if (parentId && !getTeamDirs(g.teamId).has(parentId)) return notFound("父目录不存在");

      const file: TeamFile = {
        id: teamFileSequence.next(),
        originalName: name,
        fileSize: size,
        mimeType,
        createTime: now(),
        fileUrl: `/team/${g.teamId}/files/${name}`,
        parentId,
        uploaderId: g.me,
      };
      getTeamFiles(g.teamId).set(file.id, file);
      return ok({ id: file.id, name: file.originalName, url: file.fileUrl });
    },
  },

  /** 搜索文件（按文件名模糊匹配，需置于 /files/:fileId 之前） */
  {
    url: "/dev-api/api/v1/team/:id/files/search",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:list")) return forbidden("无权搜索文件");

      const keyword = String(req.query.keyword || "")
        .trim()
        .toLowerCase();
      if (!keyword) return ok([]);
      const result = Array.from(getTeamFiles(g.teamId).values())
        .filter((f) => f.originalName.toLowerCase().includes(keyword))
        .map((f) => teamFileToFileInfo(g.teamId, f));
      return ok(result);
    },
  },

  /** 文件树 */
  {
    url: "/dev-api/api/v1/team/:id/files/tree",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:list")) return forbidden("无权查看文件树");
      return ok(buildTeamFileTree(g.teamId, 0));
    },
  },

  /** 转存个人文件到团队（需置于 /files/:fileId 之前） */
  {
    url: "/dev-api/api/v1/team/:id/files/from-personal",
    method: "POST",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:transfer:to-team")) return forbidden("无权转存到团队");

      const { sourceFileId, targetDirectoryId } = (req.body || {}) as {
        sourceFileId?: number;
        targetDirectoryId?: number;
      };
      const source = sourceFileId ? personalFiles.get(sourceFileId) : undefined;
      if (!source) return notFound("个人文件不存在");
      const target = Number(targetDirectoryId) || 0;
      if (target && !getTeamDirs(g.teamId).has(target)) return notFound("目标目录不存在");

      const file: TeamFile = {
        id: teamFileSequence.next(),
        originalName: source.originalName,
        fileSize: source.fileSize,
        mimeType: source.mimeType,
        createTime: now(),
        fileUrl: `/team/${g.teamId}/files/${source.originalName}`,
        parentId: target,
        uploaderId: g.me,
      };
      getTeamFiles(g.teamId).set(file.id, file);
      return ok(teamFileToFileInfo(g.teamId, file));
    },
  },

  /** 文件详情 */
  {
    url: "/dev-api/api/v1/team/:id/files/:fileId",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:detail")) return forbidden("无权查看文件");

      const file = getTeamFiles(g.teamId).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      return ok(teamFileToFileInfo(g.teamId, file));
    },
  },

  /** 删除文件（移入回收站） */
  {
    url: "/dev-api/api/v1/team/:id/files/:fileId",
    method: "DELETE",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:delete")) return forbidden("无权删除文件");

      const file = getTeamFiles(g.teamId).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      moveTeamFileToTrash(g.teamId, file, g.me);
      return ok(null);
    },
  },

  /** 复制文件 */
  {
    url: "/dev-api/api/v1/team/:id/files/:fileId/copy",
    method: "POST",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:copy")) return forbidden("无权复制文件");

      const file = getTeamFiles(g.teamId).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      const { targetDirectoryId } = (req.body || {}) as { targetDirectoryId?: number };
      const target = Number(targetDirectoryId) || 0;
      if (target && !getTeamDirs(g.teamId).has(target)) return notFound("目标目录不存在");

      const copy: TeamFile = {
        ...file,
        id: teamFileSequence.next(),
        parentId: target,
        createTime: now(),
      };
      getTeamFiles(g.teamId).set(copy.id, copy);
      return ok(null);
    },
  },

  /** 下载文件 */
  {
    url: "/dev-api/api/v1/team/:id/files/:fileId/download",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:download")) return forbidden("无权下载文件");

      const file = getTeamFiles(g.teamId).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      return ok({ downloadUrl: file.fileUrl, fileName: file.originalName });
    },
  },

  /** 移动文件 */
  {
    url: "/dev-api/api/v1/team/:id/files/:fileId/move",
    method: "PUT",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:move")) return forbidden("无权移动文件");

      const file = getTeamFiles(g.teamId).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      const { targetDirectoryId } = (req.body || {}) as { targetDirectoryId?: number };
      const target = Number(targetDirectoryId) || 0;
      if (target && !getTeamDirs(g.teamId).has(target)) return notFound("目标目录不存在");
      if (target === file.parentId) return conflict("文件已在目标目录中");
      file.parentId = target;
      return ok(null);
    },
  },

  /** 转存团队文件到个人空间 */
  {
    url: "/dev-api/api/v1/team/:id/files/:fileId/save-to-personal",
    method: "POST",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "file:transfer:to-personal")) return forbidden("无权转存到个人");

      const file = getTeamFiles(g.teamId).get(Number(req.params.fileId));
      if (!file) return notFound("文件不存在");
      const { targetDirectoryId } = (req.body || {}) as { targetDirectoryId?: number };

      const copy = {
        id: personalFileSequence.next(),
        originalName: file.originalName,
        fileSize: file.fileSize,
        mimeType: file.mimeType,
        createTime: now(),
        fileUrl: `/files/${file.originalName}`,
        parentId: Number(targetDirectoryId) || 0,
      };
      personalFiles.set(copy.id, copy);
      return ok(null);
    },
  },

  /* ---------------------------- 回收站 ---------------------------- */

  /** 回收站列表 */
  {
    url: "/dev-api/api/v1/team/:id/trash",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "trash:list")) return forbidden("无权查看回收站");
      return ok(Array.from(getTeamTrash(g.teamId).values()));
    },
  },

  /** 彻底删除回收站项 */
  {
    url: "/dev-api/api/v1/team/:id/trash/:trashId",
    method: "DELETE",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "trash:delete")) return forbidden("无权清空回收站");

      const trash = getTeamTrash(g.teamId);
      const id = Number(req.params.trashId);
      if (!trash.has(id)) return notFound("回收站项不存在");
      trash.delete(id);
      return ok(null);
    },
  },

  /** 恢复回收站项 */
  {
    url: "/dev-api/api/v1/team/:id/trash/:trashId/restore",
    method: "POST",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "trash:restore")) return forbidden("无权恢复回收站");

      const restored = restoreTeamTrash(g.teamId, Number(req.params.trashId));
      if (!restored) return notFound("回收站项不存在");
      return ok(teamFileToFileInfo(g.teamId, restored));
    },
  },

  /* ---------------------------- 分享 ---------------------------- */

  /** 团队分享列表（可选 mineOnly） */
  {
    url: "/dev-api/api/v1/team/:id/shares",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "share:create")) return forbidden("无权查看分享");

      const mineOnly = String(req.query.mineOnly || "false") === "true";
      const list = Array.from(getTeamShares(g.teamId).values())
        .filter((s) => !mineOnly || s.creatorId === g.me)
        .map((s) => toTeamShareView(g.teamId, s));
      return ok(list);
    },
  },

  /** 创建团队分享 */
  {
    url: "/dev-api/api/v1/team/:id/shares",
    method: "POST",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "share:create")) return forbidden("无权创建分享");

      const { fileId, accessType, expireDays, code } = (req.body || {}) as {
        fileId?: number;
        accessType?: number;
        expireDays?: number;
        code?: string;
      };
      if (!fileId || !getTeamFiles(g.teamId).has(fileId)) return badRequest("分享的文件不存在");
      const type = Number(accessType ?? 0);
      const extractCode = code?.trim()
        ? code.trim().toUpperCase()
        : type === ACCESS_WITH_CODE
          ? randomCode()
          : null;

      const share: TeamShare = {
        id: teamShareSequence.next(),
        fileId,
        shareToken: uniqueTeamShareToken(),
        accessType: type,
        extractCode,
        expireTime: expireDays && expireDays > 0 ? daysFromNow(expireDays) : null,
        statusDesc: "active",
        createTime: now(),
        creatorId: g.me,
      };
      getTeamShares(g.teamId).set(share.id, share);
      return ok(toTeamShareView(g.teamId, share));
    },
  },

  /** 取消团队分享 */
  {
    url: "/dev-api/api/v1/team/:id/shares/:shareId",
    method: "DELETE",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "share:manage")) return forbidden("无权取消分享");

      const map = getTeamShares(g.teamId);
      const id = Number(req.params.shareId);
      const share = map.get(id);
      if (!share) return notFound("分享不存在");
      if (g.role !== "Owner" && g.role !== "Admin" && share.creatorId !== g.me) {
        return forbidden("只能取消自己创建的分享");
      }
      map.delete(id);
      return ok(null);
    },
  },

  /** 团队分享详情 */
  {
    url: "/dev-api/api/v1/team/:id/shares/:shareId",
    method: "GET",
    body: (req) => {
      const g = memberGuard(req);
      if (!g.ok) return g.response;
      if (!requirePerm(g.role, "share:create")) return forbidden("无权查看分享");

      const share = getTeamShares(g.teamId).get(Number(req.params.shareId));
      if (!share) return notFound("分享不存在");
      return ok(toTeamShareView(g.teamId, share));
    },
  },
]);
