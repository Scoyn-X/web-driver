// 压测场景：团队文件上传 POST /api/v1/team/{teamId}/files
// 用法：VUS=20 DURATION=60s k6 run --summary-export=results/team-file-upload.json scenarios-team-file-upload.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  createTeam,
  unique,
  buildConstantOptions,
} from "./lib/common.js";

export const options = buildConstantOptions(Number(__ENV.VUS || 20), __ENV.DURATION || "60s", {
  http_req_duration: ["p(95)<3000"],
});

export function setup() {
  const user = createUserAndToken("k6tup");
  const teamId = createTeam(user.token, `perf-team-tup-${Date.now()}`);
  return { token: user.token, teamId };
}

export default function (data) {
  const name = `${unique("tup")}.txt`;
  const payload = {
    parentId: "0",
    file: http.file("k6 team upload perf payload\n".repeat(32), name, "text/plain"),
  };
  const res = http.post(`${API}/team/${data.teamId}/files`, payload, {
    headers: { Authorization: `Bearer ${data.token}` },
  });
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
    "返回文件 id": (r) => !!r.json("data.fileId") || !!r.json("data.id"),
  });
}
