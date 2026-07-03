# Apifox 测试使用指南（Lab2 后端）

> 目标读者：本组所有开发成员。看完这份文档你应当能：独立导入测试用例、在本机跑通全量接口回归、排查失败、给新接口补测试用例。

---

## 0. 一分钟速览

```
backend/apifox/
├── backend-tests.postman_collection.json   # 45 个接口用例 + 断言脚本
├── lab2-local.postman_environment.json     # 本地环境变量 (baseUrl=8989)
└── README.md                               # 本文件
```

基本流程：**启动后端 → 打开 Apifox → 导入 collection + environment → 选择环境 → 运行集合 / 测试场景**。

---

## 1. 前置准备

### 1.1 启动依赖服务
后端依赖 **MySQL、Redis、MinIO**。项目已提供 Docker Compose，在 `backend/` 执行：

```bash
docker compose -f docker/docker-compose.yml up -d
```

确认三个容器都在跑：
```bash
docker ps
# 应看到 mysql / redis / minio
```

### 1.2 初始化数据库（仅首次或需要重置时）
```powershell
cd D:\Code\Software_Engineering\backend\sql\mysql
docker exec -i mysql mysql -uroot -p123456 -e "DROP DATABASE IF EXISTS youlai_boot;"
Get-Content 00_init_db.sql       | docker exec -i mysql mysql -uroot -p123456
Get-Content 01_sys_user.sql      | docker exec -i mysql mysql -uroot -p123456 youlai_boot
Get-Content 02_sys_account.sql   | docker exec -i mysql mysql -uroot -p123456 youlai_boot
Get-Content sys_file.sql         | docker exec -i mysql mysql -uroot -p123456 youlai_boot
Get-Content sys_file_object.sql  | docker exec -i mysql mysql -uroot -p123456 youlai_boot
Get-Content sys_share.sql        | docker exec -i mysql mysql -uroot -p123456 youlai_boot
Get-Content user_quota.sql       | docker exec -i mysql mysql -uroot -p123456 youlai_boot
```

> **什么时候需要重置？** 修改了表结构、或测试留下脏数据影响回归时。正常日常跑用例**不需要**重置。

### 1.3 启动后端
```bash
cd D:\Code\Software_Engineering\backend
mvn spring-boot:run
# 或在 IDEA 里右键 YoulaiBootApplication → Run
```

看到日志 `Started YoulaiBootApplication in X.X seconds`，访问 `http://localhost:8989/doc.html` 能打开 Knife4j 文档就算起来了。

---

## 2. 导入到 Apifox

### 2.1 导入接口用例（collection）

1. 打开 Apifox → 打开目标项目
2. 左侧菜单最底下 **⚙️ 项目设置 → 导入数据**
3. **"Postman"** 标签页 → **导入文件** → 选 `backend-tests.postman_collection.json`
4. 重要设置：
   - **导入至**：选"**根目录**"或新建一个目录叫 `Lab2 测试用例`（避免和团队现有接口混在一起）
   - **导入模式**：首次导入选"**仅新增**"；后续更新选"**增量覆盖**"
5. 点"确认导入"

导入后在"**接口管理**"下会看到 6 个文件夹：
```
01_Auth 认证          (9 例)
02_Quota 配额         (2 例)
03_Directory 目录     (10 例)
04_File 文件          (10 例)
05_RecycleBin 回收站  (10 例)
06_Visit 访问计数     (4 例)
```

> **注意**：Apifox 会把 Postman 的"请求"映射为"接口"，每个接口下的"测试脚本"保留为**接口用例的后置操作**。所以用例都躺在接口管理里，这是正常设计。

### 2.2 导入环境变量（environment）

1. 还是 **⚙️ 项目设置 → 导入数据 → Postman**
2. 上传 `lab2-local.postman_environment.json`
3. Apifox 会自动识别为环境，导入到 **环境管理**

### 2.3 切换环境

顶部右上角有一个环境下拉框，切到 **"Lab2 Local"**。这时 `{{baseUrl}}` 等变量才会生效。

---

## 3. 运行测试

### 3.1 单例调试（排查某个接口）

1. 接口管理 → 找到目标接口 → 点进去
2. **"运行"**标签页 → 填好参数 → **发送**
3. 响应下方有**"后置操作"**标签，会显示脚本输出和断言结果（绿勾=通过，红叉=失败）

### 3.2 集合运行（最简单的批量回归）

1. 接口管理 → 选中 `Lab2 测试用例` 根目录
2. 右键 → **"测试套件运行"** 或 **"批量运行"**
3. 确认环境是 **Lab2 Local** → 点"开始运行"
4. 运行完后显示报告：总数 / 通过 / 失败，每条失败都能点进去看断言消息

> 这种跑法是**按文件夹顺序**执行，适合快速回归。

### 3.3 测试场景（推荐，可编排依赖关系）

这是 Apifox 的核心能力，和简单合集运行相比优势：
- 可以跨文件夹组合用例（比如"注册 A → 登录 A → 上传 → 删除"）
- 每个步骤之间可以插入"等待"、"脚本步骤"、"条件分支"
- 失败可以标记"继续"或"中断"
- 可重复执行 N 次测压力

**创建场景**：
1. 左侧 **自动化测试** 菜单 → 新建测试场景
2. 起名如 `冒烟测试-完整回收站生命周期`
3. 点"添加步骤" → 选"接口用例" → 勾选要加入的接口 → 确认
4. 上下拖动调整顺序
5. 点"开始运行"

**预定义的 3 个推荐场景**（文档 4 节有详细步骤）：
- 场景 A：冒烟（最短路径验证核心功能）
- 场景 B：权限隔离（越权验证）
- 场景 C：参数边界（参数校验）

### 3.4 定时/CI 运行

**定时**：在测试场景里有"定时任务"，可以配每天 9:00 跑一次冒烟，失败发企业微信/邮箱。

**CI**：用 Apifox CLI（详见 6 节）。

---

## 4. 三个推荐测试场景

### 4.1 场景 A：冒烟测试

**目的**：每次提交前跑一次，5 分钟内验证所有核心路径。

**步骤**：
| # | 用例 | 写入变量 | 断言 |
|---|---|---|---|
| 1 | 注册用户A | — | code=00000 |
| 2 | 注册用户B | — | code=00000 |
| 3 | 登录用户A | tokenA, userAId | token 非空 |
| 4 | 登录用户B | tokenB, userBId | token 非空 |
| 5 | 获取配额 (A) | — | 含 usedSpace/totalSpace |
| 6 | 创建根目录 A | dirAId | code=00000 |
| 7 | 创建子目录 B | dirBId | code=00000 |
| 8 | 上传文件到根 | fileId | id 非空 |
| 9 | 上传到目录 B | fileInDirId | id 非空 |
| 10 | 文件树 | — | code=00000 |
| 11 | 删除目录 B | — | code=00000 |
| 12 | 列回收站 | — | **仅含 dirBId，不展开 fileInDirId** ⚡ |
| 13 | 单独恢复子文件 | — | code≠00000，提示"父目录" ⚡ |
| 14 | 恢复目录 B | — | code=00000 |
| 15 | 列回收站（验证清空）| — | 不含 dirBId |

⚡ 标记的是**关键回归用例**，对应这次修复的回收站 bug。

### 4.2 场景 B：权限隔离

| # | 用例 | 断言 |
|---|---|---|
| 1 | 注册 A / B | — |
| 2 | 登录 A / B | 两个 token |
| 3 | A 创建目录 + 上传文件 | — |
| 4 | B 下载 A 的文件 | code≠00000（越权） |
| 5 | B 删除 A 的目录 | code≠00000 |
| 6 | A 删除后 B 操作 A 的回收站项 | code≠00000 |

### 4.3 场景 C：参数边界

完全独立（不依赖其他场景数据），专测参数校验：
- 注册：邮箱非法 / 密码空 / 同名账号
- 目录：空名 / `../evil` / 51 字超长 / 重名
- 搜索：空串 / 仅空格
- 文件：下载不存在的 id / 上传未登录

---

## 5. 变量与脚本

### 5.1 变量的生命周期

Apifox 有三层变量：
| 层级 | 作用域 | 用途 |
|---|---|---|
| 环境变量 | 整个环境（Lab2 Local） | `baseUrl`、账号密码这种跨场景固定的 |
| 集合变量 | 整个集合 / 整个 Apifox 项目 | `tokenA`、`dirAId` 这种运行时写入的 |
| 临时变量 | 单次运行 | 仅本次场景使用 |

我们的用例使用**集合变量**（对应 Postman 的 `pm.collectionVariables`）。

### 5.2 常见脚本模板

**写入变量（通常放"后置操作"）**：
```js
const j = pm.response.json();
pm.collectionVariables.set('fileId', j.data.id);
```

**断言响应码**：
```js
pm.test('code=00000', () => pm.expect(pm.response.json().code).to.eql('00000'));
pm.test('HTTP 200', () => pm.response.to.have.status(200));
```

**断言业务失败**：
```js
pm.test('code != 00000', () => pm.expect(pm.response.json().code).to.not.eql('00000'));
```

**断言字段存在**：
```js
pm.test('含 id', () => pm.expect(pm.response.json().data).to.have.property('id'));
```

**断言数组不包含某值**（回收站不展开子文件的核心断言）：
```js
const ids = (pm.response.json().data || []).map(x => String(x.id));
pm.test('不含 fileInDirId', () =>
  pm.expect(ids).to.not.include(String(pm.collectionVariables.get('fileInDirId')))
);
```

### 5.3 为什么我们的响应成功是 HTTP 200 + `code="00000"`

后端用统一响应结构 `Result<T>`，**业务失败也返回 HTTP 200，错误码在 body 的 `code` 字段里**（阿里规范）。所以断言要看 `code`，不能只看 HTTP 状态。

唯一的例外是 **未登录/token 无效**，这是 Spring Security 过滤器直接返 401/403 的原生响应，不走 Result 包装。

---

## 6. Apifox CLI（用于 CI）

### 6.1 安装
```bash
npm i -g apifox-cli
```

### 6.2 跑集合
```bash
cd backend/apifox
apifox run backend-tests.postman_collection.json \
  -e lab2-local.postman_environment.json \
  -r html --out-dir ./report
```

生成的 `report/index.html` 可以挂到 CI artifact 下载。

### 6.3 GitHub Actions 参考
```yaml
- name: Start backend stack
  run: docker compose -f backend/docker/docker-compose.yml up -d

- name: Init DB
  run: bash backend/sql/mysql/init.sh  # 需自行写一个脚本包一下

- name: Start backend
  run: mvn -f backend/pom.xml spring-boot:run &

- name: Wait for health
  run: until curl -sf http://localhost:8989/actuator/health; do sleep 2; done

- name: Run Apifox tests
  run: |
    npm i -g apifox-cli
    apifox run backend/apifox/backend-tests.postman_collection.json \
      -e backend/apifox/lab2-local.postman_environment.json \
      -r html --out-dir apifox-report

- uses: actions/upload-artifact@v4
  with:
    name: apifox-report
    path: apifox-report
```

---

## 7. 常见问题（FAQ）

### Q1: 响应返回 403，body 是 SQL 语法错误 / "Table doesn't exist"
**原因**：数据库表结构不匹配（可能 DB 里是旧表）。
**解决**：按 1.2 节**完全重置数据库**，重跑初始化脚本。

### Q2: URL 里出现 `%7B%7BfileId%7D%7D`
**原因**：`{{fileId}}` 变量没被替换——说明前置用例没跑成功或没执行。
**解决**：
1. 确认当前环境是 **Lab2 Local**（不是"无环境"）
2. 在**测试场景里运行**，不要单条执行依赖型用例
3. 先跑"上传文件到根"，它会把 `fileId` 写入集合变量

### Q3: 上传文件接口返回"同一目录下已存在同名文件或目录"
**说明**：后端现已改为**自动改名**（`a.txt` → `a(1).txt`），正常上传同名文件应该成功。如果还是报错说明后端未重启或版本不对。

### Q4: 文件上传用例点发送时报"file 参数缺失"
**原因**：Postman Collection 里二进制文件路径没法序列化，导入 Apifox 后 file 字段是空的。
**解决**：点开用例 → Body → form-data → file 字段 → 重新选择一个本地文件（任意小 txt 都行）。Apifox 会记住这个选择。

### Q5: 跑到某个用例突然所有后面都挂了
**排查**：Apifox 测试场景支持"失败后继续"和"失败后停止"两种模式。默认是停止——适合调试；回归时建议改继续，一次看到所有问题。

### Q6: 如何清理测试产生的脏数据？
方法一：每次用集合变量 `randomSuffix = Date.now()` 生成唯一账号（我们已这么做，见 collection 的 `prerequest` 脚本）。
方法二：每轮跑完手动执行：
```sql
DROP DATABASE youlai_boot;
```
再按 1.2 节重建。

### Q7: 新接口怎么加测试？
1. 在 Apifox 接口管理里找到（或新建）该接口
2. 点**"添加用例"** → 填好请求参数
3. "后置操作"里加断言脚本（照 5.2 节模板）
4. 需要写入集合变量供后续用例用，也写在后置操作
5. 把这条用例加进对应的测试场景

---

## 8. 已覆盖范围一览

| 模块 | Happy path | 参数校验 | 权限 | 业务边界 |
|---|---|---|---|---|
| Auth | 注册/登录 | 邮箱、密码、同名 | — | 账号不存在 |
| Quota | 查询 | — | 未登录 | — |
| Directory | 增/改名/树 | 空名、`../`、超长 | 越权删 | 循环移动 |
| File | 上传/搜索/复制/下载 | 搜索空串 | 越权下载、未登录上传 | 同名自动改名、不存在 ID |
| RecycleBin | 列/恢复/永久删 | — | 越权 | **目录列表不展开、单独恢复子节点被拒** |
| Visit | 增/查/重置闭环 | — | — | — |

**未覆盖（可扩展）**：
- 大文件上传（需约定阈值）
- 配额耗尽场景（需先降低配额）
- 并发上传去重（走 Apifox 压力测试模式）
- 分享模块（本轮不做）

---

## 9. 更新与协作流程

1. 后端接口有改动时，先本地重跑测试场景，确认回归通过
2. 如果新增/修改接口，同步更新 `backend-tests.postman_collection.json`：
   - 方法一：在 Apifox 里手动加用例 → 项目设置 → 导出数据 → Postman 格式 → 覆盖 collection 文件
   - 方法二：直接编辑 JSON（适合熟手）
3. 提交 PR 时附带 Apifox CLI 的 html 报告截图
4. Merge 前 Reviewer 本地至少跑一次"场景 A：冒烟"
