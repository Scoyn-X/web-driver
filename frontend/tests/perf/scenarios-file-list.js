// 压测场景：文件列表接口 GET /api/v1/files
// 运行：k6 run --summary-export=results/file-list.json scenarios-file-list.js
//      VUS=50 DURATION=60s k6 run scenarios-file-list.js
import http from "k6/http";
import { check } from "k6";
import { API, SUCCESS_CODE, createUserAndToken, authJson, buildOptions } from "./lib/common.js";

export const options = buildOptions();

export function setup() {
  const user = createUserAndToken("k6list");
  return { token: user.token };
}

export default function (data) {
  const res = http.get(`${API}/files?parentId=0`, { headers: authJson(data.token) });
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
    "返回 items": (r) => Array.isArray(r.json("data.items")),
  });
}
