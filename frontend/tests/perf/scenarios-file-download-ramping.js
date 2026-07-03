// 压测场景：文件下载 — 阶梯加压找饱和点
// 用法：MAX_VUS=200 k6 run --summary-export=results/file-download-ramping.json scenarios-file-download-ramping.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  createUserAndToken,
  uploadFile,
  unique,
  buildRampingOptions,
  generateContent,
} from "./lib/common.js";

const MAX_VUS = Number(__ENV.MAX_VUS || 200);

export const options = buildRampingOptions(MAX_VUS, "210s", {
  http_req_duration: ["p(95)<3000"],
});

export function setup() {
  const user = createUserAndToken("k6dr");
  const { id } = uploadFile(user.token, `${unique("dl")}.txt`, generateContent(5 * 1024));
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
