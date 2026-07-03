// 压测场景：创建分享接口 POST /api/v1/shares
// 运行：k6 run --summary-export=results/share-create.json scenarios-share-create.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  uploadFile,
  authJson,
  unique,
  buildOptions,
} from "./lib/common.js";

export const options = buildOptions();

export function setup() {
  const user = createUserAndToken("k6sc");
  const { id } = uploadFile(user.token, `${unique("share")}.txt`, "share perf payload");
  if (!id) throw new Error("setup 阶段上传文件失败，无法获取 fileId");
  return { token: user.token, fileId: id };
}

export default function (data) {
  const res = http.post(
    `${API}/shares`,
    JSON.stringify({ fileId: data.fileId, accessType: 0, expireDays: 7 }),
    { headers: authJson(data.token) }
  );
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
    "返回 shareToken": (r) => !!r.json("data.shareToken"),
  });
}
