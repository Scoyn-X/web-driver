// 压测场景：分享详情（公开接口） — 阶梯加压找极限吞吐
// 用法：MAX_VUS=500 k6 run --summary-export=results/share-detail-ramping.json scenarios-share-detail-ramping.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  uploadFile,
  createShare,
  unique,
  buildRampingOptions,
} from "./lib/common.js";

const MAX_VUS = Number(__ENV.MAX_VUS || 500);

export const options = buildRampingOptions(MAX_VUS, "210s", {
  http_req_duration: ["p(95)<2000"],
});

export function setup() {
  const user = createUserAndToken("k6sdr");
  const { id } = uploadFile(user.token, `${unique("sd")}.txt`);
  if (!id) throw new Error("setup 阶段上传文件失败");
  const { shareToken } = createShare(user.token, id, 0, 7);
  if (!shareToken) throw new Error("setup 阶段创建分享失败");
  return { shareToken };
}

export default function (data) {
  const res = http.get(`${API}/s/${data.shareToken}`);
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
  });
}
