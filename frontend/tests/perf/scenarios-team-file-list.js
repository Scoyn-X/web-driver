// 压测场景：团队文件列表 GET /api/v1/team/{teamId}/files
// 用法：VUS=50 DURATION=60s k6 run --summary-export=results/team-file-list.json scenarios-team-file-list.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  createTeam,
  uploadTeamFile,
  authJson,
  buildConstantOptions,
} from "./lib/common.js";

export const options = buildConstantOptions();

export function setup() {
  const user = createUserAndToken("k6tfl");
  const teamId = createTeam(user.token, `perf-team-tfl-${Date.now()}`);
  // 预上传一些文件让列表不空
  for (let i = 0; i < 5; i++) {
    uploadTeamFile(user.token, teamId, `teamfile_${i}.txt`, "team perf payload");
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
