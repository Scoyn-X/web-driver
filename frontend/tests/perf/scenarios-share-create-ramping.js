// 压测场景：创建分享 — 阶梯加压找饱和点
// 用法：MAX_VUS=200 k6 run --summary-export=results/comprehensive/ramp-sharecreate.json scenarios-share-create-ramping.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  uploadFile,
  unique,
  buildRampingOptions,
} from "./lib/common.js";

const MAX_VUS = Number(__ENV.MAX_VUS || 200);

export const options = buildRampingOptions(MAX_VUS, "210s", {
  http_req_duration: ["p(95)<3000"],
});

export function setup() {
  const user = createUserAndToken("k6scr");
  const { id } = uploadFile(user.token, `${unique("scramp")}.txt`);
  if (!id) throw new Error("setup 阶段上传文件失败");
  return { token: user.token, fileId: id };
}

export default function (data) {
  const res = http.post(
    `${API}/shares`,
    JSON.stringify({ fileId: data.fileId, accessType: 0, expireDays: 7 }),
    { headers: { Authorization: `Bearer ${data.token}`, "Content-Type": "application/json" } }
  );
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
  });
}
