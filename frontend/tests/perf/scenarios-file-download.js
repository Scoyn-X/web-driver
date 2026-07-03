// 压测场景：文件下载接口 GET /api/v1/files/{id}/download
// 运行：k6 run --summary-export=results/file-download.json scenarios-file-download.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  createUserAndToken,
  uploadFile,
  authJson,
  unique,
  buildOptions,
} from "./lib/common.js";

export const options = buildOptions();

export function setup() {
  const user = createUserAndToken("k6dl");
  const { id } = uploadFile(
    user.token,
    `${unique("dl")}.txt`,
    "download perf payload\n".repeat(128)
  );
  if (!id) throw new Error("setup 阶段上传文件失败，无法获取 fileId");
  return { token: user.token, fileId: id };
}

export default function (data) {
  const res = http.get(`${API}/files/${data.fileId}/download`, {
    headers: { Authorization: `Bearer ${data.token}` },
  });
  check(res, {
    "status 200": (r) => r.status === 200,
    有响应体: (r) => r.body && r.body.length > 0,
  });
}
