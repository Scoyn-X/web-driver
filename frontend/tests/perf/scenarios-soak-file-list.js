// 压测场景：文件列表 — 长稳测试（soak test），检测内存泄漏/连接泄漏
// 用法：VUS=50 DURATION=600s k6 run --summary-export=results/comprehensive/soak-filelist.json scenarios-soak-file-list.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  authJson,
  buildConstantOptions,
} from "./lib/common.js";

export const options = buildConstantOptions(Number(__ENV.VUS || 50), __ENV.DURATION || "600s", {
  http_req_duration: ["p(95)<1500"],
  // 长稳测试关注错误率是否随时间增长
  http_req_failed: ["rate<0.01"],
});

export function setup() {
  const user = createUserAndToken("k6soak");
  return { token: user.token };
}

export default function (data) {
  const res = http.get(`${API}/files?parentId=0`, { headers: authJson(data.token) });
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
  });
}
