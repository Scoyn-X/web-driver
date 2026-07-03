# 后端代码规范检查规则

本文档列出 `script/code_check.py` 实装的全部规则及其判定逻辑，用于团队成员理解脚本会在什么场景下报错、以及如何关闭或调优规则。

---

## 概览

- **入口脚本**：`script/code_check.py`
- **运行方式**：`python script/code_check.py [project_root] [--mode {full|incremental}] [--base <ref>]`，默认 `project_root = .`
- **配置文件**：`script/config.json`（与脚本同目录）
- **忽略文件**：项目根 `.checkignore`，每行 `<glob>` 或 `<glob>:<rule_id>[,<rule_id>...]`
- **退出码**：发现缺陷返回 `1`，无缺陷返回 `0`，CLI 用法错误（未知 mode、git 失败等）返回 `2`
- **报告格式**：默认 ESLint 风格；通过 `output.format` 切换

### 检查模式

| 模式 | 含义 | 配置方式 |
|---|---|---|
| `full`（默认） | 扫描 `src/**/*.java`、`src/main/resources/mapper/*.xml` 与 `sql/mysql/*.sql` 的全部文件 | `--mode full` 或 `scan.mode = "full"` |
| `incremental` | 仅扫描与 `--base`（默认 `master`）有差异的文件 | `--mode incremental` 或 `scan.mode = "incremental"` |

- CLI 参数 `--mode` / `--base` 优先级高于 `config.json` 中的 `scan.mode` / `scan.incrementalBaseRef`。
- 增量模式合并三种 git diff 的并集（`<base>...HEAD` + 已 stage + 未 stage），并按 `--diff-filter=ACMR` 过滤掉已删除文件。
- 增量模式下 `duplication/block` 只能检测「变更文件之间」的重复，不会与未变更的旧代码比对。

### 输出格式

| 格式 | 含义 | 示例 |
|---|---|---|
| `eslint`（默认） | 按文件分组列出每条缺陷，含行号、级别、消息、规则 ID | `src/.../Foo.java\n  12:1  error  ...  rule/id` |
| `angry` | 只输出问题总数，不显示文件 / 行号 / 消息 | `102 problems (102 errors, 0 warnings)` |

通过 `output.format` 设置；`output.colors` 控制是否启用 ANSI 颜色。

### 包识别约定

脚本通过「源文件 `package` 声明的路径段集合」来判断当前文件属于哪一类，集合中只要包含相应关键字即视为命中：

| 关键字 | 含义 |
|---|---|
| `vo` | VO 包（请求 / 响应模型） |
| `entity` | Entity 包（持久化模型） |
| `mapper` | MyBatis Mapper 包 |
| `service` | Service 接口 / 实现 |
| `converter` | MapStruct / 手写 Converter |
| `controller` | Spring Controller |
| `enums` | 业务枚举 |
| `model` | 触发 `model/package-structure` 检查（详见对应规则） |

例如包 `com.jiayuan.boot.system.team.model.vo` 的路径段集合 `{com, jiayuan, boot, system, team, model, vo}` 包含 `vo`，因此被识别为 VO 包。

### 规则分类总览（24 条）

| 类别 | 规则 ID | 默认开关 |
|---|---|---|
| naming | `naming/vo-suffix` | on |
| naming | `naming/no-dto` | on |
| naming | `naming/package-no-multi-word` | on |
| api | `api/summary-length` | on |
| api | `api/summary-banned-word` | on |
| api | `interface/id-naming` | on |
| model | `model/field-comment` | on |
| model | `model/package-structure` | on |
| model | `model/entity-no-schema` | on |
| vo | `vo/field-schema` | on |
| enum | `enum/base-enum` | on |
| converter | `converter/single-param` | on |
| converter | `converter/name-format` | on |
| service | `vo/use-converter` | on |
| service | `service/complex-lambda` | on |
| service | `service/interface-docs` | on |
| service | `service/public-method-comment` | on |
| mapper | `mapper/no-inline-sql` | on |
| mapper | `mapper/short-query` | on |
| sql | `sql/file-naming` | on |
| duplication | `duplication/block` | on |
| size | `size/file-length` | on |
| size | `size/class-length` | on |
| size | `size/method-length` | on |

### 通用忽略方式

在项目根 `.checkignore` 追加一行：

```
src/main/java/com/example/Foo.java                 # 忽略该文件下所有规则
src/main/java/com/example/Bar.java:duplication/block,vo/field-schema   # 仅忽略指定规则
```

支持 glob，例如 `src/test/**/*.java:duplication/block`。

---

## naming —— 命名规范

### `naming/vo-suffix`

- **默认**：`enabled: true`
- **简要说明**：`vo` 包下的类名必须以 `RequestVO` 或 `ResponseVO` 结尾。
- **触发范围**：`package` 路径段包含 `vo` 的 Java 文件。
- **判定逻辑**：
  1. 解析 Java 文件，定位首个 `class` / `interface` 声明。
  2. 取该类型的简单名 `name`。
  3. 如果 `name` 既不以 `RequestVO` 结尾、也不以 `ResponseVO` 结尾，在该类型声明所在行报错。
- **可配置项**：无。
- **违例**：

  ```java
  package com.jiayuan.boot.system.team.model.vo;
  public class TeamInfo { ... }                  // ❌ 缺少 RequestVO/ResponseVO 后缀
  ```

- **正例**：

  ```java
  public class TeamInfoResponseVO { ... }
  public class TeamCreateRequestVO { ... }
  ```

### `naming/no-dto`

- **默认**：`enabled: true`
- **简要说明**：类名中不允许出现 `dto`（统一使用 VO 命名）。
- **触发范围**：所有 Java 文件中的首个 `class` / `interface` 声明（不区分包）。
- **判定逻辑**：取首个类型声明的简单名，做 `lower()` 转换；若包含子串 `"dto"` 则在类型声明所在行报错（同时匹配 `Dto`、`DTO`、`xxDtoYy` 等大小写组合）。
- **可配置项**：无。
- **违例**：`class UserDTO`、`class CreateUserDto`、`class TeamDtoMapper`。
- **正例**：`class UserResponseVO`、`class CreateUserRequestVO`。

---

### `naming/package-no-multi-word`

- **默认**：`enabled: true`
- **简要说明**：包名中的单个段不得使用下划线或其它多词分隔符（例如 `private_space`），应使用小写驼峰或单一词段（例如 `private` 或 `privatespace`，项目约定统一为单词或小写连写）。
- **触发范围**：所有 Java 文件（根据 `package` 声明判断）。
- **判定逻辑**：把文件的 `package` 按 `.` 切分为段列表，对每个段做检测：若段中包含 `_` 则在 `package` 声明所在行报错，消息为英语：`Package segments must not contain underscores; found '<segment>' in '<package_name>'`。
- **可配置项**：无。
- **违例**：`package com.jiayuan.boot.system.private_space.mapper;`（段 `private_space` 含下划线）
- **正例**：`package com.jiayuan.boot.system.privatespace.mapper;` 或 `package com.jiayuan.boot.system.private.mapper;`


## api —— 接口与控制器

### `api/summary-length`

- **默认**：`enabled: true`，`maxLength: 20`
- **简要说明**：`@Operation` 注解的 `summary` 长度不能超过 `maxLength` 个字符。
- **触发范围**：所有 Java 文件中带 `@Operation` 注解的方法。
- **判定逻辑**：
  1. 遍历当前文件的所有 `MethodDeclaration`。
  2. 在其注解列表中查找 `@Operation`。
  3. 提取 `summary` 命名参数的字符串字面量值（脚本会去除外层引号）。
  4. 当 `len(summary) > maxLength` 时，在 `@Operation` 注解所在行（解析失败回退到方法声明行）报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `maxLength` | `20` | 最大允许字符数 |

- **违例**：

  ```java
  @Operation(summary = "这是一个非常非常长的接口摘要，远远超过二十个字符")
  public Result<Void> doSomething() { ... }
  ```

- **正例**：

  ```java
  @Operation(summary = "登录")
  public Result<TokenVO> login(@RequestBody LoginRequestVO req) { ... }
  ```

### `api/summary-banned-word`

- **默认**：`enabled: true`
- **简要说明**：`@Operation` 的 `summary` 中不能出现违禁词。
- **触发范围**：同 `api/summary-length`。
- **判定逻辑**：与 `api/summary-length` 共享对 `@Operation summary` 的遍历；只要 `summary` 中以子串方式包含 `bannedWords` 列表中任一词，即报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `bannedWords` | `["新增","添加","更新","编辑","更改","彻底删除"]` | 违禁词列表 |

- **违例**：`@Operation(summary = "新增团队")`。
- **正例**：`@Operation(summary = "创建团队")`。

### `interface/id-naming`

- **默认**：`enabled: true`，`allowedNames: ["id","parentId"]`
- **简要说明**：Controller 方法中作为 ID 传入的参数命名必须使用 `id` 或 `parentId`。
- **触发范围**：`package` 路径段包含 `controller` 的 Java 文件。
- **判定逻辑**：
  1. 遍历方法的每个参数。
  2. 收集参数上的注解简单名集合，若与 `{PathVariable, RequestParam}` 没有交集，跳过该参数。
  3. 若参数名在 `allowedNames` 中，跳过。
  4. 若参数名（小写）包含子串 `"id"`，则报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `allowedNames` | `["id","parentId"]` | 允许的 ID 参数名 |

- **违例**：

  ```java
  @GetMapping("/{teamId}")
  public Result<TeamInfoResponseVO> get(@PathVariable Long teamId) { ... }   // ❌ 应改为 id
  ```

- **正例**：

  ```java
  @GetMapping("/{id}")
  public Result<TeamInfoResponseVO> get(@PathVariable Long id) { ... }
  ```

---

## model —— 字段注释

### `model/field-comment`

- **默认**：`enabled: true`
- **简要说明**：VO / Entity 包内的字段必须有注释或 `@Schema`。
- **触发范围**：`package` 路径段包含 `vo` 或 `entity` 的 Java 文件。
- **判定逻辑**：遍历每个 `FieldDeclaration`：
  1. 若任一注解的简单名是 `Schema`，视为已文档化。
  2. 否则，从字段所在行向上查找紧邻的非空、非注解 (`@`) 行；若该行属于 `//` / `/*` / `/**` / `*` 起始或以 `*/` 结尾，视为已文档化。
  3. 若以上两条都不满足，则对该字段的每个 declarator 报错。`serialVersionUID`、`log`、`logger` 跳过。
- **可配置项**：无。
- **违例**：

  ```java
  public class UserResponseVO {
      private Long id;                  // ❌ 既无 @Schema 也无注释
  }
  ```

- **正例**：

  ```java
  /** 用户 ID */
  private Long id;
  ```

  或

  ```java
  @Schema(description = "用户 ID")
  private Long id;
  ```

### `model/entity-no-schema`

- **默认**：`enabled: true`
- **简要说明**：`entity` 包下的类与字段不得使用 `@Schema` 注解；实体的文档化应通过数据库字段注释、DTO/VO 文档或迁移脚本而非实体注解完成。
- **触发范围**：`package` 路径段包含 `entity` 的 Java 文件（类级与字段级均检测）。
- **判定逻辑**：
  1. 若类型声明上包含 `@Schema` 注解，在注解所在行报错 `Entity classes must not use @Schema annotations`。
  2. 若字段声明上包含 `@Schema` 注解，在注解所在行报错 `Entity fields must not use @Schema annotations`。
- **可配置项**：无。
- **违例**：实体类或其字段带有 `@Schema(description = "...")` 注解。
- **正例**：实体类 / 字段不使用 `@Schema`，相应文档移到 VO/DTO 或 API 层。


### `model/package-structure`

- **默认**：`enabled: true`，`allowedSubPackages: ["vo", "bo", "enums", "entity"]`
- **简要说明**：`model` 包下只允许出现 `vo` / `bo` / `enums` / `entity` 子包，不允许独立的 Java 文件，也不允许 `form` / `query` / `dto` 等其它子包。
- **触发范围**：`package` 声明的路径段序列中包含 `model` 的所有 Java 文件（不要求是末尾段）。
- **判定逻辑**：
  1. 把当前文件的包名按 `.` 切分得到有序段列表 `segments`。
  2. 取第一个等于 `"model"` 的段的索引 `idx`。
  3. **位于 model 下的独立文件**：若 `idx == len(segments) - 1`（即 `model` 是包名最后一段，类文件直接挂在 `model` 包下），在首个类型声明所在行报错 `Files directly under model package are not allowed; place them in one of [...]`。
  4. **不合法的子包**：否则取 `sub = segments[idx + 1]`，若 `sub` 不在 `allowedSubPackages` 中，在首个类型声明所在行报错 `Sub-package '<sub>' under model is not allowed; only [...] are permitted`。
  5. 文件没有首个类型声明（如 `package-info.java`）时定位行回退到第 1 行。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `allowedSubPackages` | `["vo", "bo", "enums", "entity"]` | `model` 下允许出现的直接子包名 |

- **违例**：

  ```
  src/main/java/.../system/oss/model/FileInfo.java               // ❌ 直接位于 model 包下
  src/main/java/.../system/role/model/form/RoleForm.java         // ❌ form 不在允许列表
  src/main/java/.../system/role/model/query/RolePageQuery.java   // ❌ query 不在允许列表
  ```

- **正例**：

  ```
  src/main/java/.../system/oss/model/vo/FileInfoResponseVO.java
  src/main/java/.../system/oss/model/entity/SysFile.java
  src/main/java/.../system/oss/model/enums/FileStatus.java
  src/main/java/.../system/oss/model/bo/FileUploadBO.java
  ```

- **忽略方式**：在 `.checkignore` 追加 `src/main/java/.../FileInfo.java:model/package-structure`，或对整个目录使用 glob 例如 `src/main/java/.../system/role/model/form/*.java:model/package-structure`。

---

## vo —— VO 字段约束

### `vo/field-schema`

- **默认**：`enabled: true`
- **简要说明**：VO 字段必须使用 `@Schema` 作为首个注解，且不允许以普通注释（`//`、`/* */`、`/** */`）替代。
- **触发范围**：`package` 路径段包含 `vo` 的 Java 文件。
- **判定逻辑**：对每个 `FieldDeclaration` 的每个 declarator（跳过 `serialVersionUID` / `log` / `logger`）：
  1. 若字段上方存在普通注释（同 `model/field-comment` 的「向上查找」），报错 `must not use plain comments; use @Schema`。
  2. 否则若字段没有任何注解，报错 `must have @Schema annotation`。
  3. 否则若首个注解的简单名不是 `Schema`，报错 `@Schema must be the first annotation`。
- **可配置项**：无。
- **违例**：

  ```java
  /** 用户 ID */                                   // ❌ 不允许裸注释
  private Long id;

  private String name;                             // ❌ 没有 @Schema

  @JsonIgnore @Schema(description = "...")         // ❌ @Schema 不是首个注解
  private String token;
  ```

- **正例**：

  ```java
  @Schema(description = "用户 ID")
  private Long id;

  @Schema(description = "Token") @JsonIgnore
  private String token;
  ```

---

## enum —— 枚举约束

### `enum/base-enum`

- **默认**：`enabled: true`
- **简要说明**：`enums` 包下的枚举必须实现 `BaseEnum` 接口。
- **触发范围**：`package` 路径段包含 `enums` 的 Java 文件。
- **判定逻辑**：遍历文件中的所有 `EnumDeclaration`。若该枚举的 `implements` 列表中没有任何接口的简单名为 `BaseEnum`，在枚举声明行报错。
- **可配置项**：无。
- **违例**：

  ```java
  package com.jiayuan.boot.system.team.model.enums;
  public enum MemberRole { OWNER, MEMBER }                 // ❌ 未实现 BaseEnum
  ```

- **正例**：

  ```java
  public enum MemberRole implements BaseEnum<Integer> { ... }
  ```

---

## converter —— Converter 类

### `converter/single-param`

- **默认**：`enabled: true`，`maxParameters: 5`
- **简要说明**：Converter 方法的入参数量不允许超过 `maxParameters`。
- **触发范围**：`package` 路径段包含 `converter` 的 Java 文件。
- **判定逻辑**：遍历文件中所有 `MethodDeclaration`，若 `len(parameters) > maxParameters`，在方法声明行报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `maxParameters` | `5` | 单个 Converter 方法允许的最大入参数 |

- **违例**：方法签名带 10 个入参的 `toSysFile(...)`。
- **正例**：将多个入参聚合到一个 RequestVO 后再传入。

### `converter/name-format`

- **默认**：`enabled: true`，`prefix: "to"`
- **简要说明**：Converter 公开方法的方法名必须以指定前缀开头。
- **触发范围**：`package` 路径段包含 `converter` 的 Java 文件。
- **判定逻辑**：只检查首个类型声明的直接成员（不深入嵌套类型），对每个 `MethodDeclaration`：
  1. 跳过构造器（`constructor=True`）。
  2. 接口方法默认视为 public；类方法要求修饰符包含 `public`，否则跳过。
  3. 若方法名不以 `prefix` 开头，在方法声明行报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `prefix` | `"to"` | Converter 公开方法的命名前缀 |

- **违例**：`stringToIdList`、`directoryCreateRequestVOToSysFile`。
- **正例**：`toIdList`、`toSysFile`。

---

## service —— Service 包约束

### `vo/use-converter`

- **默认**：`enabled: true`
- **简要说明**：`service` 包内不允许手写 get/set 进行 VO/Entity 之间的字段映射，必须改用 Converter。**测试代码自动豁免。**
- **注意**：该规则也将以 `VO`/`BO`/`Entity`/`DTO` 等命名风格的 model 类型视为映射目标（例如 `UserVipProfileBO`），对这些类型的 `new` + setter 填充写法同样会被检测并报错。
- **触发范围**：`package` 路径段包含 `service`，且文件路径不含 `/test/`。
- **判定逻辑**：遍历每个有方法体的 `MethodDeclaration`，按以下顺序判定：
  1. **使用了 Converter**：方法体中存在调用方／被调用方名包含子串 `"converter"` 的 `MethodInvocation`，或 `new XxxConverter()` 形式的 `ClassCreator`。如果是，跳过本方法。
  2. **使用了 Wrapper update**：方法体内 `new LambdaUpdateWrapper()` 或 `new UpdateWrapper()` 后链式调用了 `.setXxx(...)`。如果是，跳过本方法。
  3. **直接构造检测**：方法体内出现 `new XxxVO()` 且无构造参数（即先 `new` 后用 setter 填充的写法），在方法声明行报 `Direct VO/entity construction detected; use a converter instead`。命中后继续下一个方法。
  4. **手写映射检测**：
     - 收集所有「以 `new XxxVO()` 初始化的局部变量名」放入 `model_targets`。
     - 遍历所有 `MethodInvocation`，过滤掉 qualifier 为空或 `this` 的调用。
     - `setXxx(...)` 调用累计到 `setter_counts[qualifier]`，前提是 qualifier 在 `model_targets` 内（或 `model_targets` 为空）。
     - `getXxx(...)`、`isXxx(...)`、`hasXxx(...)` 的 qualifier 收集到 `getter_sources`。
     - 同时满足：`model_targets` 非空、setter 总数 ≥ 2、getter 来源非空、且至少一个 getter 来源不在 setter 目标集合中，则在方法声明行报 `Manual get/set mapping detected; use a converter instead`。
- **可配置项**：无。
- **违例**：

  ```java
  public TeamInfoResponseVO toResponse(TeamEntity entity) {
      TeamInfoResponseVO vo = new TeamInfoResponseVO();
      vo.setId(entity.getId());
      vo.setName(entity.getName());
      vo.setOwner(entity.getOwner());
      return vo;
  }
  ```

- **正例**：

  ```java
  public TeamInfoResponseVO toResponse(TeamEntity entity) {
      return teamConverter.toResponseVO(entity);
  }
  ```

### `service/complex-lambda`

- **默认**：`enabled: true`，`maxWrapperChainLength: 5`
- **简要说明**：`service` 中 `LambdaWrapper` 的链式调用长度不允许超过 `maxWrapperChainLength`，超过应拆分到 mapper XML。
- **触发范围**：`package` 路径段包含 `service` 的 Java 文件。
- **判定逻辑**：对每个有方法体的 `MethodDeclaration`：
  1. 收集方法内所有 `ClassCreator`，若类型简单名属于 `{LambdaQueryWrapper, LambdaUpdateWrapper, LambdaQueryChainWrapper}`，记录其 `selectors` 链长度。
  2. 若任一 wrapper 的链长度 `> maxWrapperChainLength`，在方法声明行报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `maxWrapperChainLength` | `5` | 单个 wrapper 允许的链式 `.eq(...).like(...)` 等调用数 |

- **违例**：`new LambdaQueryWrapper<>().eq(...).eq(...).like(...).ge(...).le(...).orderByDesc(...)`（链长 6+）。
- **正例**：抽到 mapper XML 中的 `<select>`。

### `service/interface-docs`

- **默认**：`enabled: true`
- **简要说明**：`service` 包内的 **接口** 方法必须有 Javadoc，且参数 / 返回值 / 异常都要在 Javadoc 中声明。
- **触发范围**：`package` 路径段包含 `service`，且文件首个类型声明是 `interface`。
- **判定逻辑**：遍历接口中的每个 `MethodDeclaration`，找方法上方的注释块（向上跳过空行和 `@` 注解，识别 `//` 与 `/* */` 块）：
  1. 若没有任何注释，报 `missing Javadoc`，本方法结束。
  2. 若注释块不以 `/**` 开头，报 `must use Javadoc /** */ style`。
  3. 对每个参数 `pname`，若注释中不含子串 `@param pname`，报 `missing @param pname`。
  4. 若 `return_type` 非空且不是 `void`，注释中不含 `@return` 时报 `missing @return`。
  5. 若方法 `throws` 列表非空，注释中既不含 `@throws` 也不含 `@exception` 时报 `missing @throws/@exception`。
- **可配置项**：无。
- **违例**：缺 Javadoc，或缺 `@param`、`@return` 等。
- **正例**：

  ```java
  /**
   * 创建团队。
   *
   * @param req  团队创建参数
   * @return 创建后的团队 ID
   * @throws BusinessException 名称冲突时抛出
   */
  Long create(TeamCreateRequestVO req) throws BusinessException;
  ```

### `service/public-method-comment`

- **默认**：`enabled: true`
- **简要说明**：`service` 包内的所有 public 方法都必须有注释。
- **触发范围**：`package` 路径段包含 `service` 的 Java 文件（接口和实现都检查）。
- **判定逻辑**：遍历每个 `MethodDeclaration`：
  1. 修饰符不含 `public`，跳过。
  2. 方法名属于 `{hashCode, equals, toString, get, set}`，跳过。
  3. 在方法声明行上方查找紧邻的非空、非注解 (`@`) 行；若该行不是 `//` / `/*` / `/**` / `*` 起始也不以 `*/` 结尾，则报 `Public method <name> is missing a comment`。
- **可配置项**：无。
- **注**：本规则只检查「上方有无注释行」，不要求 Javadoc 风格；对接口方法的 Javadoc 完整性由 `service/interface-docs` 单独负责。

---

## mapper —— Mapper 包约束

### `mapper/no-inline-sql`

- **默认**：`enabled: true`
- **简要说明**：mapper 类禁止使用 `@Select` / `@Insert` / `@Update` / `@Delete` 内联 SQL，应改写到 mapper XML。
- **触发范围**：`package` 路径段包含 `mapper` 的 Java 文件。
- **判定逻辑**：遍历每个方法的注解，对简单名属于 `{Select, Insert, Update, Delete}` 的注解：
  1. 序列化注解的 `element`（拼接字符串字面量、解析三元、拼成扁平字符串）。
  2. 转大写后查找子串 `"SELECT "`、`"INSERT "`、`"UPDATE "`、`"DELETE "`（注意末尾空格）。
  3. 任一关键字命中，在注解行报 `Mapper classes must not contain inline SQL; use mapper XML instead`，每个方法只报一次（命中后 `break`）。
- **可配置项**：无。
- **违例**：`@Select("SELECT id FROM sys_user WHERE name = #{name}")`。
- **正例**：方法不带 SQL 注解，对应 SQL 写在 `resources/mapper/XxxMapper.xml`。

### `mapper/short-query`

- **默认**：`enabled: true`，`maxWhereConditions: 2`
- **简要说明**：对位于 `src/main/resources/mapper/` 的 `.xml` 文件中的 `<select>`，当查询为单表、无 JOIN/UNION/GROUP/HAVING/复杂表达式且 WHERE 子句为少量简单等值条件（<= `maxWhereConditions`）时，建议使用 MyBatis-Plus 的 LambdaWrapper 或内置方法替代手写 XML，脚本会以英语提示该查询可优化。
- **触发范围**：`src/main/resources/mapper/` 下的 `.xml` 文件中的 `<select>` 块。
- **判定逻辑（简要）**：
  1. 提取 `<select>...</select>` 文本并去除 XML 注释与内嵌 XML 元素。
  2. 要求以 `SELECT` 开头且包含 `FROM`，且不包含 `JOIN` / `UNION` / `GROUP BY` / `HAVING` / `WITH`。
  3. WHERE 子句必须存在、且不包含 `OR`、`IN`、`EXISTS`、`LIKE`、`BETWEEN`、`CASE` 等复杂语义。
  4. 把 WHERE 按 `AND` 切分得到条件列表，若条件数 `> maxWhereConditions` 则不判为短查询。
  5. 对每个条件做简单正则匹配，仅接受 `column = #{param}`、`column = NULL`、`column = TRUE/FALSE`、字面量或数字等简单形式。
  6. 满足上述条件则在 `<select>` 起始行报英语提示：`Short mapper queries should be replaced with a LambdaWrapper or a built-in MyBatis-Plus mapper method`。


---

## sql —— SQL 文件命名

### `sql/file-naming`

- **默认**：`enabled: true`
- **简要说明**：`sql/mysql` 目录下只允许出现初始化 SQL 与符合命名约定的迁移 SQL。
- **触发范围**：项目根的 `sql/mysql/` 目录下所有 `.sql` 文件。
- **判定逻辑**：对每个 `.sql` 文件：
  1. 若文件名出现在 `allowedFiles` 中，通过。
  2. 否则尝试每个 `allowedPatterns`：
     - 内置快速路径：当 pattern 为 `migration_\d{8}\.sql` / `migration_\\d{8}\\.sql` 时，要求文件名形如 `migration_<8 位数字>.sql`。
     - 否则按 PurePosixPath glob 匹配（`*` 等通配）。
  3. 全部不通过则在文件第 1 行报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `allowedFiles` | `["jiayuan_boot.sql"]` | 显式允许的固定文件名 |
  | `allowedPatterns` | `["migration_\\d{8}\\.sql"]` | 允许的命名模式 |

- **违例**：`sql/mysql/sys_user.sql`、`sql/mysql/init.sql`。
- **正例**：`sql/mysql/jiayuan_boot.sql`、`sql/mysql/migration_20260514.sql`。

---

## duplication —— 重复代码

### `duplication/block`

- **默认**：`enabled: true`，`minLines: 10`
- **简要说明**：检测连续 `minLines` 行（去除注释 / 包导入 / 纯括号噪声后）跨文件 / 同一文件出现的重复代码块。
- **触发范围**：所有 `.java` 文件，跨文件比较。
- **判定逻辑**：
  1. **归一化**：对每个文件去掉 `//` 行注释、`/* */` 块注释（含跨行）、`package` / `import` 行，以及孤立的 `{`、`}`、`});`、`})`、`};`、`);` 噪声行；对剩余每行做 `" ".join(text.split())` 折叠空白；保留每行的原始行号。
  2. **指纹**：对每个文件用滑动窗口取连续 `minLines` 行，计算 MD5，将出现位置记录到 `by_hash[digest]`。
  3. **筛选重复组**：保留出现 ≥ 2 次的指纹；按「出现次数降序」「首次起始索引升序」排序。
  4. **去重叠**：维护 `covered[file_path]` 已经报过的行号区间。若一个重复组里所有出现位置都已落在已覆盖区间内，跳过该组（避免大块重复被切成多段重复报）。
  5. **报告**：对组内每个出现位置 `(file, idx, start_line, end_line)`，构造 `siblings = [<rel>:<sl>-<el>, ...]`；当前位置自身从 siblings 中过滤掉后，作为 `see` 列表。报错文案为 `Duplicate code block (<minLines> lines), see <sibling list>`，定位到 `start_line`。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `minLines` | `10` | 触发重复检测的最小连续行数（归一化后） |

- **忽略方式**：在 `.checkignore` 中将相关文件追加 `: duplication/block`。例如：

  ```
  src/main/java/com/jiayuan/boot/system/oss/model/vo/FileInfoResponseVO.java:duplication/block
  src/test/java/.../*FileOpsTest.java:duplication/block
  ```

---

## size —— 长度限制

### `size/file-length`

- **默认**：`enabled: true`，`maxLines: 1000`
- **简要说明**：单个 `.java` 文件的总行数不允许超过 `maxLines`。
- **触发范围**：所有 `.java` 文件。
- **判定逻辑**：`len(content.splitlines()) > maxLines` 时，在文件第 1 行报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `maxLines` | `1000` | 单文件最大物理行数；设为 `0` 等价于禁用本规则 |

### `size/class-length`

- **默认**：`enabled: true`，`maxLines: 300`
- **简要说明**：单个类 / 接口 / 枚举的源代码行数不允许超过 `maxLines`。
- **触发范围**：所有 `.java` 文件中的所有 `ClassDeclaration` / `InterfaceDeclaration` / `EnumDeclaration`（含嵌套类型）。
- **判定逻辑**：
  1. 取类型声明行 `start`。
  2. 从 `start` 起向下找首个 `{`，按 `{` / `}` 配对找到匹配的 `}` 所在行 `end`。
  3. 若 `end - start + 1 > maxLines`，在 `start` 行报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `maxLines` | `300` | 单类型最大行数；设为 `0` 等价于禁用本规则 |

### `size/method-length`

- **默认**：`enabled: true`，`maxLines: 80`
- **简要说明**：单个方法的源代码行数不允许超过 `maxLines`。
- **触发范围**：所有 `.java` 文件的所有 `MethodDeclaration`。
- **判定逻辑**：
  1. 取方法声明行 `start`。
  2. 取方法 AST 子树中所有节点的最大 `position.line` 作为 `end` 候选。
  3. 同时通过大括号配对找到方法体 `}` 所在行；取两者较大值作为 `end`。
  4. 若 `end - start + 1 > maxLines`，在 `start` 行报错。
- **可配置项**：

  | 字段 | 默认 | 含义 |
  |---|---|---|
  | `maxLines` | `80` | 单方法最大行数；设为 `0` 等价于禁用本规则 |

---

## 添加 / 修改规则的开发流程

1. 在 `script/code_check.py` 第 8 节实现一个继承自 `Rule`、`ProjectRule` 或 `SqlRule` 的类，至少实现 `id`、可选 `default_config`、`applies_to`（仅 `Rule`）和 `check`。规则若不依赖首个类型声明（如包结构 / 枚举类规则），将类属性 `requires_type_decl = False`。
2. 把实例追加到第 9 节的对应列表（`JAVA_RULES` / `PROJECT_RULES` / `SQL_RULES`）。
3. 在 `script/config.json` 同步加入条目（`enabled` + 可调参数 + `description`）。
4. 在本文档对应分类下新增小节，沿用「默认 / 简要说明 / 触发范围 / 判定逻辑 / 可配置项 / 违例 / 正例」结构，并更新概览表中的规则计数。
5. 重跑 `python script/code_check.py .`，必要时更新 `.checkignore`。
