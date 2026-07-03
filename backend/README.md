# jiayuan-boot 后端服务

软件工程（H）课程项目 —— 轻量级个人网盘工具后端。

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.5.11 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7.2 |
| 对象存储 | MinIO | 8.5.10 |
| 接口文档 | Knife4j (OpenAPI 3) | 4.5.0 |
| 对象映射 | MapStruct | 1.6.3 |
| 工具库 | Hutool | 5.8.34 |

## 项目结构

```
src/main/java/com/jiayuan/boot/
├── config/                 # 配置类（Security、CORS、Redis、MyBatis等）
├── common/
│   ├── result/             # 统一响应封装（Result、ResultCode）
│   ├── exception/          # 全局异常处理
│   ├── constant/           # 常量定义
│   ├── enums/              # 通用枚举（OssTypeEnum等）
│   ├── annotation/         # 自定义注解
│   ├── aspect/             # AOP切面
│   ├── util/               # 工具类
│   └── base/               # 基础实体与枚举
├── system/
│   ├── counter/            # 访问计数模块
│   │   ├── controller/
│   │   └── service/
│   └── oss/                # 文件存储模块
│       ├── controller/
│       ├── service/
│       ├── mapper/
│       ├── converter/      # MapStruct转换器
│       └── model/          # 实体、VO、枚举
└── plugin/                 # 插件（MyBatis、Knife4j）
```

## 环境准备

### 前置要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose

### 启动基础设施

```bash
cd docker
docker compose up -d
```

将启动以下服务：

| 服务 | 端口 | 凭据 |
|------|------|------|
| MySQL 8.0 | 3306 | root / 123456 |
| Redis 7.2 | 6379 | 密码 123456 |
| MinIO | 9000 (API) / 9001 (控制台) | minioadmin / minioadmin |

### 初始化数据库

Docker 首次启动时会自动执行 `sql/mysql/` 下的建表脚本。若容器已存在，需手动执行：

```bash
mysql -h localhost -u root -p123456 youlai_boot < sql/mysql/sys_file.sql
```

## 启动项目

```bash
mvn spring-boot:run
```

服务启动后访问：
- 后端地址：http://localhost:8989
- 接口文档：http://localhost:8989/doc.html

## API 接口

### 访问计数

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/visits` | 获取当前访问次数 |
| POST | `/api/v1/visits` | 访问次数 +1 |
| DELETE | `/api/v1/visits` | 重置访问次数为 0 |

### 文件管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/files` | 上传文件（form-data，字段名 `file`） |
| GET | `/api/v1/files` | 获取文件列表 |
| GET | `/api/v1/files/{id}/download` | 按 ID 下载文件 |
| GET | `/api/v1/files/{*filePath}` | 按存储路径下载文件（如 `20260315/abc123.pdf`） |
| DELETE | `/api/v1/files/{id}` | 按 ID 删除文件 |
| DELETE | `/api/v1/files?filePath=...` | 按存储路径删除文件 |

### 响应格式

所有接口统一返回：

```json
{
  "code": "00000",
  "msg": "正常响应",
  "data": {}
}
```

## 配置说明

主要配置文件为 `src/main/resources/application-dev.yml`，关键配置项：

| 配置 | 说明 | 默认值 |
|------|------|--------|
| `server.port` | 服务端口 | 8989 |
| `oss.type` | 存储类型（minio / aliyun / local） | minio |
| `oss.minio.endpoint` | MinIO 地址 | http://localhost:9000 |
| `oss.minio.bucket-name` | 存储桶名称 | jiayuan-boot |
| `spring.servlet.multipart.max-file-size` | 单文件大小限制 | 10MB |
