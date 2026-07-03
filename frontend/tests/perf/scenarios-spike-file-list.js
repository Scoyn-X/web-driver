// 压测场景：文件列表 — spike test（突发冲击）
// 0 VU 瞬间跳至 200 VU，观察首 10s 错误率和恢复行为
// 用法：SPIKE_VUS=200 k6 run --summary-export=results/comprehensive/spike-filelist.json scenarios-spike-file-list.js
import http from "k6/http";
import { check } from "k6";
import { API, SUCCESS_CODE, createUserAndToken, authJson } from "./lib/common.js";

const SPIKE_VUS = Number(__ENV.SPIKE_VUS || 200);

export const options = {
  scenarios: {
    spike: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "5s", target: 0 }, // 5s 预热期（无负载）
        { duration: "1s", target: SPIKE_VUS }, // 1s 内跳到 200 VU（冲击！）
        { duration: "60s", target: SPIKE_VUS }, // 保持 200 VU 观察恢复
        { duration: "30s", target: 0 }, // cool-down
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"], // 冲击期放宽
    http_req_duration: ["p(95)<2000"],
  },
  summaryTrendStats: ["avg", "min", "med", "p(95)", "p(99)", "max"],
};

export function setup() {
  const user = createUserAndToken("k6spike");
  return { token: user.token };
}

export default function (data) {
  const res = http.get(`${API}/files?parentId=0`, { headers: authJson(data.token) });
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
  });
}
