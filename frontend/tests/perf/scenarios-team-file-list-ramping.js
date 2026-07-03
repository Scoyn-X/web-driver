// 压测场景：团队文件列表 — 阶梯加压，与个人空间 ramp 统一对比（MAX_VUS=300）
// 用法：MAX_VUS=300 k6 run --summary-export=results/comprehensive/ramp-team-filelist.json scenarios-team-file-list-ramping.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  createTeam,
  uploadTeamFile,
  authJson,
  buildRampingOptions,
} from "./lib/common.js";

const MAX_VUS = Number(__ENV.MAX_VUS || 300);

export const options = buildRampingOptions(MAX_VUS, "210s", {
  http_req_duration: ["p(95)<2000"],
});

export function setup() {
  const user = createUserAndToken("k6tramp");
  const teamId = createTeam(user.token, `perf-tramp-${Date.now()}`);
  for (let i = 0; i < 10; i++) {
    uploadTeamFile(user.token, teamId, `trampfile_${i}.txt`, "team ramp perf payload");
  }
  return { token: user.token, teamId };
}

export default function (data) {
  const res = http.get(`${API}/team/${data.teamId}/files?parentId=0`, {
    headers: authJson(data.token),
  });
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
  });
}
