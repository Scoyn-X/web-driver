// 压测场景：文件上传 — 不同文件大小对比（1KB / 100KB / 1MB / 5MB）
// 运行时会依次测试各档大小，每档独立统计。
// 用法：VUS=20 DURATION=30s k6 run --summary-export=results/file-upload-sizes.json scenarios-file-upload-sizes.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  unique,
  buildConstantOptions,
  generateContent,
} from "./lib/common.js";

// 通过环境变量切换文件大小；runner 会多次调用此脚本并设置 SIZE_KB
const SIZE_KB = Number(__ENV.SIZE_KB || 1);
const SIZE_BYTES = SIZE_KB * 1024;

export const options = buildConstantOptions(Number(__ENV.VUS || 20), __ENV.DURATION || "30s", {
  http_req_duration: [`p(95)<${SIZE_KB > 500 ? 5000 : 3000}`],
});

export function setup() {
  const user = createUserAndToken(`k6up${SIZE_KB}k`);
  return {
    token: user.token,
    content: generateContent(SIZE_BYTES),
    name: `${unique(`up${SIZE_KB}k`)}.txt`,
  };
}

export default function (data) {
  const payload = {
    parentId: "0",
    file: http.file(data.content, data.name, "text/plain"),
  };
  const res = http.post(`${API}/files`, payload, {
    headers: { Authorization: `Bearer ${data.token}` },
  });
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
    "返回文件 id": (r) => !!r.json("data.id"),
  });
}
