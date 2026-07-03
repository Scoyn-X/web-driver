// 压测场景：文件列表 — 阶梯加压找饱和点
// 用法：MAX_VUS=300 k6 run --summary-export=results/file-list-ramping.json scenarios-file-list-ramping.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  authJson,
  buildRampingOptions,
  generateContent,
} from "./lib/common.js";

const MAX_VUS = Number(__ENV.MAX_VUS || 200);

export const options = buildRampingOptions(MAX_VUS, "210s", {
  http_req_duration: ["p(95)<2000"],
});

export function setup() {
  const user = createUserAndToken("k6lr");
  // 预上传一些文件，让列表不是空的（更真实）
  for (let i = 0; i < 10; i++) {
    const payload = {
      parentId: "0",
      file: http.file(generateContent(500), `listfile_${i}.txt`, "text/plain"),
    };
    http.post(`${API}/files`, payload, {
      headers: { Authorization: `Bearer ${user.token}` },
    });
  }
  return { token: user.token };
}

export default function (data) {
  const res = http.get(`${API}/files?parentId=0`, { headers: authJson(data.token) });
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
  });
}
