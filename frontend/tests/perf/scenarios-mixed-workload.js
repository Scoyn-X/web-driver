// 压测场景：混合工作负载 — 模拟真实用户同时进行多种操作
// 权重分配：45% 浏览文件列表 / 10% 上传小文件 / 20% 下载文件 / 10% 创建分享 / 15% 查看分享详情
// 用法：VUS=50 DURATION=120s k6 run --summary-export=results/mixed-workload.json scenarios-mixed-workload.js
import http from "k6/http";
import { check } from "k6";
import {
  API,
  SUCCESS_CODE,
  createUserAndToken,
  uploadFile,
  createShare,
  unique,
  buildMixedOptions,
  generateContent,
} from "./lib/common.js";

export const options = buildMixedOptions(Number(__ENV.VUS || 50), __ENV.DURATION || "120s", {
  http_req_duration: ["p(95)<3000"],
});

export function setup() {
  const user = createUserAndToken("k6mix");
  // 预置一个可下载的文件和一个可访问的分享
  const { id } = uploadFile(user.token, `${unique("mix")}.txt`, generateContent(10 * 1024));
  if (!id) throw new Error("setup 阶段上传文件失败");
  const { shareToken } = createShare(user.token, id, 0, 7);
  if (!shareToken) throw new Error("setup 阶段创建分享失败");

  // 预上传少量文件让列表不空
  for (let i = 0; i < 5; i++) {
    const p = {
      parentId: "0",
      file: http.file(`mix filler ${i}`, `mixfiller_${i}.txt`, "text/plain"),
    };
    http.post(`${API}/files`, p, { headers: { Authorization: `Bearer ${user.token}` } });
  }

  return { token: user.token, fileId: id, shareToken };
}

export default function (data) {
  const rand = Math.random();
  const headers = { Authorization: `Bearer ${data.token}` };

  if (rand < 0.45) {
    // 45% — 浏览文件列表
    const res = http.get(`${API}/files?parentId=0`, { headers });
    check(res, {
      "list:status": (r) => r.status === 200,
      "list:code": (r) => r.json("code") === SUCCESS_CODE,
    });
  } else if (rand < 0.55) {
    // 10% — 上传小文件
    const name = `${unique("mixup")}.txt`;
    const payload = {
      parentId: "0",
      file: http.file("k6 mixed upload payload\n".repeat(8), name, "text/plain"),
    };
    const res = http.post(`${API}/files`, payload, { headers });
    check(res, {
      "up:status": (r) => r.status === 200,
      "up:code": (r) => r.json("code") === SUCCESS_CODE,
    });
  } else if (rand < 0.75) {
    // 20% — 下载文件
    const res = http.get(`${API}/files/${data.fileId}/download`, { headers });
    check(res, {
      "dl:status": (r) => r.status === 200,
    });
  } else if (rand < 0.85) {
    // 10% — 创建分享
    const newFile = unique("mixshare");
    const upPayload = {
      parentId: "0",
      file: http.file("shareable", `${newFile}.txt`, "text/plain"),
    };
    const upRes = http.post(`${API}/files`, upPayload, { headers });
    const fid = upRes.json()?.data?.id;
    if (fid) {
      const shareRes = http.post(
        `${API}/shares`,
        JSON.stringify({ fileId: fid, accessType: 0, expireDays: 7 }),
        {
          headers: { Authorization: `Bearer ${data.token}`, "Content-Type": "application/json" },
        }
      );
      check(shareRes, {
        "share:status": (r) => r.status === 200,
      });
    }
  } else {
    // 15% — 查看分享详情（公开接口）
    const res = http.get(`${API}/s/${data.shareToken}`);
    check(res, {
      "sdetail:status": (r) => r.status === 200,
      "sdetail:code": (r) => r.json("code") === SUCCESS_CODE,
    });
  }
}
