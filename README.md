# Web Drive

协作网盘系统，支持个人空间、团队空间、RBAC 权限控制、文件分享与回收站。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.5, MyBatis-Plus, MySQL, Redis (Redisson), MinIO, Flowable |
| 前端 | Vue 3, TypeScript, Element Plus, UnoCSS, Pinia, Vite |
| 测试 | Playwright (E2E), k6 (压力测试) |

## 功能

- **个人空间**：文件上传/下载/删除/移动/复制，目录管理，搜索
- **团队空间**：创建团队，DB 驱动 RBAC（Owner/Admin/Editor/Viewer），20+ 权限点按角色分配
- **分享**：创建分享链接，提取码保护，匿名访问
- **回收站**：软删除，定时清理，恢复或彻底删除
- **配额**：用户及团队存储配额上限
- **VIP**：更高配额与并发上限
- **私密空间**：仅所有者可见的敏感文件区域
- **团队邀请流**：Flowable 工作流引擎驱动的邀请→接受/拒绝流程

## 项目结构

```
├── backend/                  # Spring Boot 后端
│   ├── src/main/java/        # 业务代码
│   ├── sql/                  # 数据库迁移脚本
│   └── docker/               # MySQL/MinIO/Redis 编排
├── frontend/                 # Vue 3 前端
│   ├── src/                  # 页面、组件、store
│   └── tests/
│       ├── e2e/              # Playwright 端到端测试（15 用例，6 类流程）
│       └── perf/             # k6 压力测试（19 脚本，13 类场景）
└── vue3-element-admin/       # 后台管理模板（参考）
```

## 快速启动

### 前置条件

JDK 17+, Node 18+, pnpm, MySQL, MinIO, Redis

### 后端

```bash
cd backend
# 启动 docker compose（MySQL + MinIO + Redis）
cd docker && docker compose up -d
# 导入数据
mysql -u root < sql/mysql/jiayuan_boot.sql
# 启动 Spring Boot
mvn spring-boot:run
```

### 前端

```bash
cd frontend
pnpm install
pnpm dev          # http://localhost:3000
```

## 测试

```bash
cd frontend

# Playwright E2E
pnpm test:e2e

# k6 压力测试
cd tests/perf
k6 run scenarios-file-list.js
```
