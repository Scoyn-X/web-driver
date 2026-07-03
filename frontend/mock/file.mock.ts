/**
 * 网盘文件 / 目录 / 回收站 mock，对应 `src/api/file.api.ts`。
 *
 * 三个资源同属存储域，共享 `data/file.state.ts` 中的状态，因此：
 * - 上传后 listFiles / quota 立即可见；
 * - 删除文件进入回收站，可还原；
 * - 删除目录会级联把子文件移入回收站。
 */
import { defineMock } from "vite-plugin-mock-dev-server";
import { badRequest, conflict, notFound, now, ok } from "./shared";
import { currentUserId } from "./data/user.state";
import {
  type MockFile,
  buildBreadcrumb,
  buildDirectoryTree,
  collectDirectorySubtree,
  directories,
  directorySequence,
  directoryToFileInfo,
  fileSequence,
  fileToFileInfo,
  files,
  listChildDirectories,
  listChildFiles,
  listEntries,
  moveFileToRecycle,
  recycleBin,
  restoreFromRecycle,
  toDirectoryTreeNode,
} from "./data/file.state";

/** 从上传请求体中解析文件信息，兼容 FormData 与普通对象两种形态 */
function readUpload(body: any) {
  const read = (key: string) =>
    body && typeof body.get === "function" ? body.get(key) : body?.[key];
  const field: any = read("file");
  const name: string = field?.name || field?.originalFilename || field?.filename || "未命名文件";
  const realSize = Number(field?.size);
  // 取不到真实大小时（mock 环境常见），生成一个合理的随机大小，便于观察配额变化
  const size =
    Number.isFinite(realSize) && realSize > 0
      ? realSize
      : Math.floor(50_000 + Math.random() * 2_000_000);
  const mimeType: string = field?.type || field?.mimetype || "application/octet-stream";
  return { name, size, mimeType, parentId: Number(read("parentId")) || 0 };
}

/** 个人空间文件树：目录递归 children，文件作为叶子节点 */
function buildPersonalFileTree(parentId: number): any[] {
  const dirs = listChildDirectories(parentId).map((dir) => ({
    ...directoryToFileInfo(dir),
    children: buildPersonalFileTree(dir.id),
  }));
  const leafFiles = listChildFiles(parentId).map(fileToFileInfo);
  return [...dirs, ...leafFiles];
}

export default defineMock([
  /* ---------------------------- 文件 ---------------------------- */

  /** 列出目录下的文件与子目录（含面包屑） */
  {
    url: "/dev-api/api/v1/files",
    method: "GET",
    body: (req) => {
      const parentId = Number(req.query.parentId || 0);
      if (parentId && !directories.has(parentId)) return notFound("目录不存在");
      return ok({ breadcrumb: buildBreadcrumb(parentId), items: listEntries(parentId) });
    },
  },

  /** 上传文件 */
  {
    url: "/dev-api/api/v1/files",
    method: "POST",
    body: (req) => {
      const { name, size, mimeType, parentId } = readUpload(req.body);
      if (parentId && !directories.has(parentId)) return notFound("目标目录不存在");

      const file: MockFile = {
        id: fileSequence.next(),
        originalName: name,
        fileSize: size,
        mimeType,
        createTime: now(),
        fileUrl: `/files/${name}`,
        parentId,
      };
      files.set(file.id, file);
      return ok({ name: file.originalName, url: file.fileUrl });
    },
  },

  /** 搜索文件（按文件名模糊匹配，需置于 /files/:filePath 之前） */
  {
    url: "/dev-api/api/v1/files/search",
    method: "GET",
    body: (req) => {
      const keyword = String(req.query.keyword || "")
        .trim()
        .toLowerCase();
      if (!keyword) return ok([]);
      const result = Array.from(files.values())
        .filter((f) => f.originalName.toLowerCase().includes(keyword))
        .map(fileToFileInfo);
      return ok(result);
    },
  },

  /** 个人文件树（目录 + 文件，递归 children） */
  {
    url: "/dev-api/api/v1/files/tree",
    method: "GET",
    body: () => ok(buildPersonalFileTree(0)),
  },

  /** 下载文件（响应体兼容前端 blob 处理约定） */
  {
    url: "/dev-api/api/v1/files/:id/download",
    method: "GET",
    body: (req) => {
      const file = files.get(Number(req.params.id));
      if (!file) return notFound("文件不存在");
      return ok({ downloadUrl: file.fileUrl, fileName: file.originalName });
    },
  },

  /** 移动文件到目标目录 */
  {
    url: "/dev-api/api/v1/files/:id/move",
    method: "PUT",
    body: (req) => {
      const file = files.get(Number(req.params.id));
      if (!file) return notFound("文件不存在");
      const { targetDirectoryId } = (req.body || {}) as { targetDirectoryId?: number };
      const target = Number(targetDirectoryId) || 0;
      if (target && !directories.has(target)) return notFound("目标目录不存在");
      if (target === file.parentId) return conflict("文件已在目标目录中");

      file.parentId = target;
      return ok(null);
    },
  },

  /** 复制文件到目标目录 */
  {
    url: "/dev-api/api/v1/files/:id/copy",
    method: "POST",
    body: (req) => {
      const file = files.get(Number(req.params.id));
      if (!file) return notFound("文件不存在");
      const { targetDirectoryId } = (req.body || {}) as { targetDirectoryId?: number };
      const target = Number(targetDirectoryId) || 0;
      if (target && !directories.has(target)) return notFound("目标目录不存在");

      const copy: MockFile = {
        ...file,
        id: fileSequence.next(),
        parentId: target,
        createTime: now(),
      };
      files.set(copy.id, copy);
      return ok(null);
    },
  },

  /** 清空当前用户的全部文件（移入回收站） */
  {
    url: "/dev-api/api/v1/files",
    method: "DELETE",
    body: (req) => {
      const deleterId = currentUserId(req);
      Array.from(files.values()).forEach((file) => moveFileToRecycle(file, deleterId));
      return ok(null);
    },
  },

  /** 删除单个文件（移入回收站） */
  {
    url: "/dev-api/api/v1/files/:id",
    method: "DELETE",
    body: (req) => {
      const file = files.get(Number(req.params.id));
      if (!file) return notFound("文件不存在");
      moveFileToRecycle(file, currentUserId(req));
      return ok(null);
    },
  },

  /** 按 ID 或文件名查询单个文件 */
  {
    url: "/dev-api/api/v1/files/:filePath",
    method: "GET",
    body: (req) => {
      const raw = decodeURIComponent(String(req.params.filePath || ""));
      const byId = files.get(Number(raw));
      if (byId) return ok(fileToFileInfo(byId));
      const byName = Array.from(files.values()).find((f) => f.originalName === raw);
      if (byName) return ok(fileToFileInfo(byName));
      return notFound("文件不存在");
    },
  },

  /* ---------------------------- 目录 ---------------------------- */

  /** 列出子目录 */
  {
    url: "/dev-api/api/v1/directories",
    method: "GET",
    body: (req) => {
      const parentId = Number(req.query.parentId || 0);
      return ok(listChildDirectories(parentId).map(toDirectoryTreeNode));
    },
  },

  /** 完整目录树 */
  {
    url: "/dev-api/api/v1/directories/tree",
    method: "GET",
    body: () => ok(buildDirectoryTree()),
  },

  /** 新建目录 */
  {
    url: "/dev-api/api/v1/directories",
    method: "POST",
    body: (req) => {
      const { name, parentId } = (req.body || {}) as { name?: string; parentId?: number };
      if (!name || !name.trim()) return badRequest("目录名不能为空");
      const parent = Number(parentId) || 0;
      if (parent && !directories.has(parent)) return notFound("父目录不存在");
      if (listChildDirectories(parent).some((d) => d.name === name.trim())) {
        return conflict("同级目录下已存在同名目录");
      }

      const directory = {
        id: directorySequence.next(),
        name: name.trim(),
        parentId: parent,
        createTime: now(),
      };
      directories.set(directory.id, directory);
      return ok(directoryToFileInfo(directory));
    },
  },

  /** 重命名目录 */
  {
    url: "/dev-api/api/v1/directories/:id/rename",
    method: "PUT",
    body: (req) => {
      const directory = directories.get(Number(req.params.id));
      if (!directory) return notFound("目录不存在");
      const { name } = (req.body || {}) as { name?: string };
      if (!name || !name.trim()) return badRequest("目录名不能为空");
      if (
        listChildDirectories(directory.parentId).some(
          (d) => d.id !== directory.id && d.name === name.trim()
        )
      ) {
        return conflict("同级目录下已存在同名目录");
      }

      directory.name = name.trim();
      return ok(directoryToFileInfo(directory));
    },
  },

  /** 删除目录（级联：子文件入回收站，子目录直接移除） */
  {
    url: "/dev-api/api/v1/directories/:id",
    method: "DELETE",
    body: (req) => {
      const directory = directories.get(Number(req.params.id));
      if (!directory) return notFound("目录不存在");

      const deleterId = currentUserId(req);
      collectDirectorySubtree(directory.id).forEach((dirId) => {
        listChildFiles(dirId).forEach((file) => moveFileToRecycle(file, deleterId));
        directories.delete(dirId);
      });
      return ok(null);
    },
  },

  /* --------------------------- 回收站 --------------------------- */

  /** 回收站列表 */
  {
    url: "/dev-api/api/v1/recycle-bin",
    method: "GET",
    body: () => ok(Array.from(recycleBin.values())),
  },

  /** 从回收站还原 */
  {
    url: "/dev-api/api/v1/recycle-bin/:id/restore",
    method: "POST",
    body: (req) => {
      const restored = restoreFromRecycle(Number(req.params.id));
      if (!restored) return notFound("回收站项不存在");
      return ok(null);
    },
  },

  /** 从回收站彻底删除 */
  {
    url: "/dev-api/api/v1/recycle-bin/:id",
    method: "DELETE",
    body: (req) => {
      const id = Number(req.params.id);
      if (!recycleBin.has(id)) return notFound("回收站项不存在");
      recycleBin.delete(id);
      return ok(null);
    },
  },
]);
