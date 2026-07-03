# 后端接口压力测试（k6）

基于 [k6](https://grafana.com/docs/k6/latest/) 对网盘系统核心接口做性能压测，覆盖 Lab4 5.2.4 建议的 5 个核心接口。

## 目录结构

```
frontend/tests/perf/
├─ lib/common.js                  # 基础地址、注册/登录、上传、分享、通用 options
├─ scenarios-file-list.js         # GET  /api/v1/files          文件列表
├─ scenarios-file-upload.js       # POST /api/v1/files          文件上传(multipart)
├─ scenarios-file-download.js     # GET  /api/v1/files/{id}/download  文件下载
├─ scenarios-share-create.js      # POST /api/v1/shares         创建分享
├─ scenarios-share-detail.js      # GET  /api/v1/s/{token}      获取分享详情
└─ run-all.ps1                    # 一键串行运行全部场景（Windows）
```

## 前置条件

- 安装 k6：<https://grafana.com/docs/k6/latest/set-up/install-k6/>
- 后端运行在 `http://localhost:8989`，MySQL、MinIO 已启动（可用 `BASE_URL` 覆盖后端地址）

## 运行

```bash
cd frontend/tests/perf

# 单个场景（默认 VUS=20, DURATION=60s）
k6 run scenarios-file-list.js

# 自定义并发与时长，并导出汇总
VUS=50 DURATION=90s k6 run --summary-export=results/file-list.json scenarios-file-list.js
```

Windows PowerShell 一键全部：

```powershell
$env:VUS=50; $env:DURATION="90s"; ./run-all.ps1
```

## 设计要点

- **真实链路**：k6 直连后端 `:8989`，连真实 MySQL 与 MinIO，不做任何 mock。
- **鉴权**：`setup()` 阶段注册并登录一个全新用户拿 JWT，迭代请求携带 `Authorization: Bearer <token>`；下载/分享场景在 `setup()` 预置文件与分享。
- **单接口聚焦**：每个脚本只压测一个接口，故 k6 内置的 `http_req_duration` 即该接口延迟分布。
- **指标**：`summaryTrendStats` 输出 `avg / med / p(95) / p(99) / max`；吞吐量看 `http_reqs`（rate）；错误率看 `http_req_failed`。
- **阈值**：默认 `http_req_failed rate<0.01`、`http_req_duration p(95)<1500ms`（上传放宽到 3000ms）；超阈值 k6 以非零码退出，便于纳入 CI 判定。
