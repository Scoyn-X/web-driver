# 前端端到端测试（Playwright）

基于 [Playwright](https://playwright.dev/) 的端到端测试，从真实用户视角验证网盘系统前后端协同的核心流程。

## 目录结构

```
frontend/
├─ playwright.config.ts          # Playwright 配置（baseURL、报告、trace/截图/视频）
└─ tests/e2e/
   ├─ helpers/users.ts           # 唯一测试账户 / 文件名生成
   ├─ pages/                     # Page Object
   │  ├─ LoginPage.ts            # 注册 / 登录 / 登出
   │  ├─ FilesPage.ts            # 文件浏览器（上传/下载/删除/目录/复制/移动/分享）
   │  ├─ SharePage.ts            # 分享访问页（提取码、下载）
   │  └─ TeamsPage.ts            # 团队列表/详情/邀请/接受/回收站
   └─ specs/                     # 测试用例
      ├─ 01-auth.spec.ts         # 注册 / 登录 / 登出
      ├─ 02-files.spec.ts        # 个人文件 上传/浏览/下载/删除
      ├─ 03-directory.spec.ts    # 目录 创建/进入/复制/移动
      ├─ 04-share.spec.ts        # 创建分享/访问/提取码/下载
      ├─ 05-team.spec.ts         # 创建团队/邀请/接受/按角色访问
      └─ 06-team-trash.spec.ts   # 团队文件删除入回收站/还原/彻底删除
```

## 前置条件

测试为**黑盒端到端测试**，需连接真实环境，运行前请确保：

1. MySQL、MinIO 已启动
2. 后端服务运行在 `http://localhost:8989`
3. 前端 dev server 运行在 `http://localhost:3000`（`pnpm dev`）

> 配置中 `webServer.reuseExistingServer = true`：若 3000 端口已有 dev server 会直接复用，否则自动 `pnpm dev` 拉起前端（数据库/MinIO/后端仍需自行启动）。

## 安装与运行

```bash
cd frontend
pnpm install                      # 安装依赖（含 @playwright/test）
npx playwright install chromium   # 安装浏览器内核（首次）

pnpm test:e2e                     # 运行全部用例
pnpm test:e2e -- 02-files         # 仅运行某个文件
pnpm test:e2e:ui                  # UI 模式调试
pnpm test:e2e:report              # 打开 HTML 报告
```

## 设计要点

- **数据隔离**：每个用例通过 `makeAccount()` / `uniqueName()` 注册全新用户、使用唯一文件名，避免与历史数据或用例间相互污染；用例串行执行（`workers: 1`）保证报告可读与后端状态可控。
- **不修改被测源码**：测试**不向前端注入任何 `data-testid` 等测试标记**，保持被测版本一致。文件行的纯图标操作按钮采用非侵入定位：删除=行内唯一 `button.el-button--danger`；下载/分享/移动/复制=操作列按既有渲染顺序的第 0/1/3/4 个按钮（顺序来自源码模板并经探针确认）。
- **失败证据**：失败时自动保留 **截图、trace、视频**（见 `playwright.config.ts` 的 `screenshot/trace/video`），可用 `pnpm test:e2e:report` 或 `npx playwright show-trace` 定位。
- **未登录访问**：分享访问用例通过 `browser.newContext()` 开全新无登录态上下文，真实验证公开链接链路。
