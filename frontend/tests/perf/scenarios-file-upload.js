// 压测场景：文件上传接口 POST /api/v1/files（multipart）
// 运行：k6 run --summary-export=results/file-upload.json scenarios-file-upload.js
import http from "k6/http";
import { check } from "k6";
import { API, SUCCESS_CODE, createUserAndToken, unique, buildOptions } from "./lib/common.js";

// 上传是写操作，放宽 p95 阈值
export const options = buildOptions({ http_req_duration: ["p(95)<3000"] });

export function setup() {
  const user = createUserAndToken("k6up");
  return { token: user.token };
}

export default function (data) {
  const name = `${unique("perf")}.txt`;
  const payload = {
    parentId: "0",
    file: http.file("k6 upload performance payload\n".repeat(64), name, "text/plain"),
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
