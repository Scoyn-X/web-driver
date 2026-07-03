// 压测场景：文件下载 — 不同文件大小对比（100KB / 1MB / 5MB）
// 用法：SIZE_KB=1024 VUS=20 DURATION=30s k6 run --summary-export=results/comprehensive/dl-size-1024kb.json scenarios-file-download-sizes.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  createUserAndToken,
  uploadFile,
  unique,
  buildConstantOptions,
  generateContent,
} from "./lib/common.js";

const SIZE_KB = Number(__ENV.SIZE_KB || 100);
const SIZE_BYTES = SIZE_KB * 1024;

export const options = buildConstantOptions(Number(__ENV.VUS || 20), __ENV.DURATION || "30s", {
  http_req_duration: ["p(95)<5000"],
});

export function setup() {
  const user = createUserAndToken(`k6dl${SIZE_KB}k`);
  const { id } = uploadFile(user.token, `${unique("dlsize")}.bin`, generateContent(SIZE_BYTES));
  if (!id) throw new Error("setup 阶段上传文件失败");
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
