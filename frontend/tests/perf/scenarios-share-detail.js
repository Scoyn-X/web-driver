// 压测场景：获取分享详情接口 GET /api/v1/s/{shareToken}（公开，无需登录）
// 运行：k6 run --summary-export=results/share-detail.json scenarios-share-detail.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  uploadFile,
  createShare,
  unique,
  buildOptions,
} from "./lib/common.js";

export const options = buildOptions();

export function setup() {
  const user = createUserAndToken("k6sd");
  const { id } = uploadFile(user.token, `${unique("sd")}.txt`, "share detail perf payload");
  if (!id) throw new Error("setup 阶段上传文件失败");
  const { shareToken } = createShare(user.token, id, 0, 7);
  if (!shareToken) throw new Error("setup 阶段创建分享失败");
  return { shareToken };
}

export default function (data) {
  // 公开分享详情无需鉴权
  const res = http.get(`${API}/s/${data.shareToken}`);
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
    返回文件名: (r) => !!r.json("data.fileName"),
  });
}
