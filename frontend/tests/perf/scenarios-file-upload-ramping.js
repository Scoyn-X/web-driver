// 压测场景：小文件上传 — 阶梯加压找饱和点（1KB 文件）
// 用法：MAX_VUS=100 k6 run --summary-export=results/comprehensive/ramp-upload.json scenarios-file-upload-ramping.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  unique,
  buildRampingOptions,
} from "./lib/common.js";

const MAX_VUS = Number(__ENV.MAX_VUS || 100);

export const options = buildRampingOptions(MAX_VUS, "210s", {
  http_req_duration: ["p(95)<5000"],
});

export function setup() {
  const user = createUserAndToken("k6upr");
  return { token: user.token };
}

export default function (data) {
  const name = `${unique("upramp")}.txt`;
  const payload = {
    parentId: "0",
    file: http.file("k6 upload ramp payload\n".repeat(2), name, "text/plain"),
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
