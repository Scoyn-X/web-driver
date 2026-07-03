// 压测场景：小文件上传 — 多梯度恒定并发（1KB 文件）
// 用法：VUS=10 DURATION=60s k6 run --summary-export=results/comprehensive/upload-gradient-v10.json scenarios-file-upload-gradients.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  unique,
  buildConstantOptions,
} from "./lib/common.js";

export const options = buildConstantOptions(Number(__ENV.VUS || 10), __ENV.DURATION || "60s", {
  http_req_duration: ["p(95)<5000"],
});

export function setup() {
  const user = createUserAndToken("k6upg");
  return { token: user.token };
}

export default function (data) {
  const name = `${unique("upgrad")}.txt`;
  const payload = {
    parentId: "0",
    file: http.file("k6 upload gradient payload\n".repeat(2), name, "text/plain"),
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
