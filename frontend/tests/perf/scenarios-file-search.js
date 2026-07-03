// 压测场景：文件搜索接口（个人 + 团队）
// 用法：VUS=50 DURATION=60s k6 run --summary-export=results/comprehensive/file-search.json scenarios-file-search.js
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
  uploadFile,
  unique,
} from "./lib/common.js";

export const options = buildConstantOptions();

export function setup() {
  const user = createUserAndToken("k6srch");
  // 个人空间预上传文件
  for (let i = 0; i < 5; i++) {
    uploadFile(user.token, `searchable_${i}.txt`, "search perf payload");
  }
  // 团队空间预上传
  const teamId = createTeam(user.token, `perf-srch-${Date.now()}`);
  for (let i = 0; i < 5; i++) {
    uploadTeamFile(user.token, teamId, `teamsearch_${i}.txt`, "team search perf");
  }
  return { token: user.token, teamId };
}

export default function (data) {
  const headers = authJson(data.token);
  // 50% 个人搜索，50% 团队搜索
  if (Math.random() < 0.5) {
    const res = http.get(`${API}/files/search?keyword=perf`, { headers });
    check(res, {
      "ps:status": (r) => r.status === 200,
      "ps:code": (r) => r.json("code") === SUCCESS_CODE,
    });
  } else {
    const res = http.get(`${API}/team/${data.teamId}/files/search?keyword=team`, { headers });
    check(res, {
      "ts:status": (r) => r.status === 200,
      "ts:code": (r) => r.json("code") === SUCCESS_CODE,
    });
  }
}
