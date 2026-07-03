// k6 压力测试共享库：基础地址、注册/登录、鉴权头、上传、团队等公共逻辑。
import http from "k6/http";
import { sleep } from "k6";

/** 后端基础地址（k6 直连后端，绕过前端代理）。可用 BASE_URL 覆盖。 */
export const BASE = __ENV.BASE_URL || "http://localhost:8989";
export const API = `${BASE}/api/v1`;

/** 统一响应成功码（com.jiayuan.boot.common.result.ResultCode.SUCCESS） */
export const SUCCESS_CODE = "00000";

/** 并发与时长参数：可用 VUS / DURATION 环境变量覆盖 */
export const VUS = Number(__ENV.VUS || 20);
export const DURATION = __ENV.DURATION || "60s";

// ===================== 通用工具 =====================

const JSON_HEADERS = { "Content-Type": "application/json" };

/** 生成唯一字符串，避免账户/文件重名 */
export function unique(prefix) {
  return `${prefix}_${Date.now().toString(36)}_${Math.floor(Math.random() * 1e6)}`;
}

/** 生成指定字节大小的文本内容（重复模式） */
export function generateContent(sizeBytes) {
  const pattern = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\n"; // 37 bytes
  const repeats = Math.ceil(sizeBytes / pattern.length);
  return pattern.repeat(repeats).substring(0, sizeBytes);
}

/** 随机整数 [min, max) */
export function randInt(min, max) {
  return Math.floor(Math.random() * (max - min)) + min;
}

// ===================== 账户操作 =====================

/** 注册一个测试账户 */
export function register(account) {
  return http.post(`${API}/auth/register`, JSON.stringify(account), { headers: JSON_HEADERS });
}

/** 登录并返回 JWT token（失败返回 null） */
export function login(accountName, password) {
  const res = http.post(`${API}/auth/login`, JSON.stringify({ accountName, password }), {
    headers: JSON_HEADERS,
  });
  const body = res.json();
  return body && body.data ? body.data.token : null;
}

/** 带 Bearer token 的 JSON 请求头 */
export function authJson(token) {
  return { Authorization: `Bearer ${token}`, "Content-Type": "application/json" };
}

/** 注册并登录一个全新用户，返回 { accountName, password, token } */
export function createUserAndToken(prefix = "k6") {
  const accountName = unique(prefix).slice(0, 20);
  const password = "Passw0rd123";
  register({
    nickname: accountName,
    accountName,
    password,
    email: `${accountName}@k6.test`,
    accountType: "personal",
  });
  const token = login(accountName, password);
  if (!token) {
    throw new Error("setup 阶段登录失败，无法获取 token，请确认后端/数据库已就绪");
  }
  return { accountName, password, token };
}

// ===================== 团队操作 =====================

/** 创建团队，返回 teamId */
export function createTeam(token, name) {
  const res = http.post(`${API}/team`, JSON.stringify({ name, description: "k6 perf test team" }), {
    headers: authJson(token),
  });
  const body = res.json();
  if (body && body.data && body.data.id) return body.data.id;
  // 有时候返回的字段名可能是 teamId
  if (body && body.data && body.data.teamId) return body.data.teamId;
  throw new Error(`创建团队失败: ${JSON.stringify(body)}`);
}

/** 上传文件到团队空间，返回 { res, fileId } */
export function uploadTeamFile(token, teamId, name, content = "k6 perf payload", parentId = 0) {
  const payload = {
    parentId: String(parentId),
    file: http.file(content, name, "text/plain"),
  };
  const res = http.post(`${API}/team/${teamId}/files`, payload, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = res.json();
  return { res, fileId: body && body.data ? body.data.fileId || body.data.id : null };
}

// ===================== 个人文件操作 =====================

/** 上传一个内存文件到个人空间，返回 { res, id } */
export function uploadFile(token, name, content = "k6 perf payload", parentId = 0) {
  const payload = {
    parentId: String(parentId),
    file: http.file(content, name, "text/plain"),
  };
  const res = http.post(`${API}/files`, payload, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = res.json();
  return { res, id: body && body.data ? body.data.id : null };
}

/** 创建分享，返回 { res, shareToken } */
export function createShare(token, fileId, accessType = 0, expireDays = 7) {
  const res = http.post(`${API}/shares`, JSON.stringify({ fileId, accessType, expireDays }), {
    headers: authJson(token),
  });
  const body = res.json();
  return { res, shareToken: body && body.data ? body.data.shareToken : null };
}

// ===================== Options 工厂 =====================

/** 恒定并发 options */
export function buildConstantOptions(vus, duration, extraThresholds = {}) {
  return {
    scenarios: {
      load: {
        executor: "constant-vus",
        vus: vus || VUS,
        duration: duration || DURATION,
      },
    },
    thresholds: {
      http_req_failed: ["rate<0.01"],
      http_req_duration: ["p(95)<1500"],
      ...extraThresholds,
    },
    summaryTrendStats: ["avg", "min", "med", "p(95)", "p(99)", "max"],
  };
}

/** 阶梯加压 options —— 用于找到系统饱和点 */
export function buildRampingOptions(maxVUs, duration, extraThresholds = {}) {
  const stages = [
    { duration: "30s", target: Math.ceil(maxVUs * 0.1) }, // 10%
    { duration: "30s", target: Math.ceil(maxVUs * 0.25) }, // 25%
    { duration: "30s", target: Math.ceil(maxVUs * 0.5) }, // 50%
    { duration: "30s", target: Math.ceil(maxVUs * 0.75) }, // 75%
    { duration: "60s", target: maxVUs }, // 100% — 稳态
    { duration: "30s", target: 0 }, // cool-down
  ];
  return {
    scenarios: {
      ramp: {
        executor: "ramping-vus",
        startVUs: 0,
        stages,
        gracefulRampDown: "10s",
      },
    },
    thresholds: {
      http_req_failed: ["rate<0.02"], // 放宽——加压时偶有失败可接受
      http_req_duration: ["p(95)<2000"], // 加压时放宽
      ...extraThresholds,
    },
    summaryTrendStats: ["avg", "min", "med", "p(95)", "p(99)", "max"],
  };
}

/** 混合场景 options（多接口组合） */
export function buildMixedOptions(vus, duration, extraThresholds = {}) {
  return {
    scenarios: {
      mixed: {
        executor: "constant-vus",
        vus: vus || VUS,
        duration: duration || DURATION,
      },
    },
    thresholds: {
      http_req_failed: ["rate<0.01"],
      http_req_duration: ["p(95)<2000"],
      ...extraThresholds,
    },
    summaryTrendStats: ["avg", "min", "med", "p(95)", "p(99)", "max"],
  };
}

// ===================== 兼容旧接口（现有脚本调用） =====================

/** @deprecated — 请使用 buildConstantOptions() */
export function buildOptions(extraThresholds = {}) {
  return buildConstantOptions(VUS, DURATION, extraThresholds);
}
