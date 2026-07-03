/**
 * 测试用户工具：为每个用例生成唯一账户，避免与历史数据冲突。
 *
 * 账户名规则（与前端 Register.vue 校验一致）：2-20 位，仅中英文/数字/下划线。
 * 密码规则：≥8 位，必须同时包含英文与数字。
 */

let seq = 0;

export interface TestAccount {
  nickname: string;
  accountName: string;
  password: string;
  email: string;
  accountType: "personal" | "work" | "team";
}

/** 生成全局唯一后缀（时间戳 + 自增序号），保证并发与重跑不撞名 */
export function uniqueSuffix(): string {
  seq += 1;
  return `${Date.now().toString(36)}${seq}`;
}

/** 构造一个满足前端校验规则的随机测试账户 */
export function makeAccount(prefix = "e2e"): TestAccount {
  const suffix = uniqueSuffix();
  const accountName = `${prefix}_${suffix}`.slice(0, 20);
  return {
    nickname: `E2E ${suffix}`,
    accountName,
    password: "Passw0rd123",
    email: `${accountName}@e2e.test`,
    accountType: "personal",
  };
}

/** 生成唯一的文件名 / 目录名，便于在列表中精确定位 */
export function uniqueName(base: string, ext = ""): string {
  return `${base}_${uniqueSuffix()}${ext}`;
}
