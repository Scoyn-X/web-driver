// 压测场景：分享详情对比 — 公开分享 vs 提取码分享
// 都通过 GET /api/v1/s/{token} 访问（公开接口）
// 用法：VUS=50 DURATION=60s k6 run --summary-export=results/comprehensive/share-extract.json scenarios-share-detail-extract.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  uploadFile,
  unique,
  buildConstantOptions,
} from "./lib/common.js";

const JSON_HEADERS = { "Content-Type": "application/json" };

export const options = buildConstantOptions();

export function setup() {
  const user = createUserAndToken("k6ext2");
  const { id } = uploadFile(user.token, `${unique("ext")}.txt`);
  if (!id) throw new Error("setup: 上传文件失败");

  const authHeaders = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${user.token}`,
  };

  // 公开分享
  const pubRes = http.post(
    `${API}/shares`,
    JSON.stringify({ fileId: id, accessType: 0, expireDays: 7 }),
    { headers: authHeaders }
  );
  const pubToken = pubRes.json()?.data?.shareToken || null;

  // 提取码分享
  const extRes = http.post(
    `${API}/shares`,
    JSON.stringify({ fileId: id, accessType: 1, expireDays: 7 }),
    { headers: authHeaders }
  );
  const extToken = extRes.json()?.data?.shareToken || null;

  if (!pubToken) throw new Error("setup: 创建公开分享失败");
  if (!extToken) throw new Error("setup: 创建提取码分享失败");

  return { pubToken, extToken };
}

export default function (data) {
  // 50% 公开分享，50% 提取码分享（都走公开 GET 接口）
  const token = Math.random() < 0.5 ? data.pubToken : data.extToken;
  const res = http.get(`${API}/s/${token}`);
  check(res, {
    "status 200": (r) => r.status === 200,
    "code 00000": (r) => r.json("code") === SUCCESS_CODE,
  });
}
