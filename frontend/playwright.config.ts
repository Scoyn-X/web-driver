import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright 端到端测试配置（Lab4）。
 *
 * 被测系统为真实运行的前后端：
 *   - 前端 dev server：http://localhost:3000（hash 路由）
 *   - 后端 API：http://localhost:8989（由前端 /dev-api 代理）
 *   - 依赖真实 MySQL 与 MinIO
 *
 * 运行前请先启动数据库、MinIO、后端服务与前端 dev server。
 * 可用环境变量 E2E_BASE_URL 覆盖前端地址。
 */
export default defineConfig({
  testDir: "./tests/e2e/specs",
  // 用例之间共享后端状态，且部分用例需注册多个用户，串行执行更稳定、报告更可读
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },

  // 失败时保留充分的定位信息：截图、trace、视频
  reporter: [["list"], ["html", { outputFolder: "tests/e2e/report", open: "never" }]],

  // trace/截图/视频等产物统一输出到已被 .gitignore 忽略的目录
  outputDir: "tests/e2e/test-results",

  use: {
    baseURL: process.env.E2E_BASE_URL || "http://localhost:3000",
    actionTimeout: 15_000,
    navigationTimeout: 20_000,
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    video: "retain-on-failure",
    locale: "zh-CN",
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],

  // 若 3000 端口已有 dev server 则直接复用；否则自动拉起（仍需自行启动后端/DB/MinIO）
  webServer: {
    command: "pnpm dev",
    url: "http://localhost:3000",
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
