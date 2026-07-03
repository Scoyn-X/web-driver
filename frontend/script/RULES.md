# 前端代码规范检查规则

本文档列出 `script/code_check.py`（前端 Linter）实装的全部规则及其判定逻辑，用于团队成员理解脚本会在什么场景下报错、以及如何关闭或调优规则。

---

## 概览

* **入口脚本**：`script/code_check.py`
* **运行方式**：`python script/code_check.py [-d target_dir]`，默认目标目录为 `./src`
* **配置文件**：`script/config.json`（读取 `thresholds`、`hardcoded`、`auto_imports`、`unocss` 等前端相关节点）
* **忽略文件**：脚本同目录的 `.checkignore`，支持 Glob 匹配（被匹配到的文件将完全跳过检查）
* **退出码**：发现 Error 级别缺陷返回 `1`，无缺陷或仅有 Warning 返回 `0`
* **检查范围**：目标目录下后缀为 `.vue`、`.jsx`、`.tsx`、`.html` 的文件，自动跳过 `node_modules`、`dist`、`.git`、`build` 目录。
* **报告格式**：采用标准 ESLint 控制台输出风格，按文件分组显示行号、列号、级别、消息和规则 ID。

### 规则分类总览（11 条）

| 类别 | 规则 ID | 默认阈值 / 状态 |
| --- | --- | --- |
| size | `max-file-lines` | 1000 行 |
| size | `max-style-block-lines` | 100 行 |
| style | `max-inline-styles` | 3 个 |
| style | `max-style-classes` | 8 个 |
| style | `prefer-unocss-shortcut` | on |
| style | `prefer-unocss` | 声明数 ≤ 3 |
| style | `no-hardcoded-color` | on |
| style | `no-repeated-hardcoded-size` | 全局重复 ≥ 3 次 |
| import | `no-redundant-auto-import` | on |
| import | `no-redundant-auto-component` | on |
| duplication | `no-duplicated-block` | 窗口 30 行，重复 > 2 处 |

### 通用忽略方式

在 `script/.checkignore` 追加一行：

```
src/assets/**/*.vue
src/legacy/OldComponent.vue

```

注意：前端版 `.checkignore` 当前仅支持直接忽略整个文件（基于 glob 路径匹配），暂不支持后缀 `:rule_id` 的细粒度规则忽略。

---

## size —— 文件与代码块大小控制

### `max-file-lines`

* **默认**：`thresholds.max_file_lines: 1000`
* **简要说明**：单个文件的总物理行数不允许超过阈值。
* **触发范围**：所有目标源文件（`.vue`, `.jsx`, `.tsx`, `.html`）。
* **判定逻辑**：按 `\n` 切分文件文本，若总行数大于配置的最大行数，在文件第 1 行报错。
* **违例**：文件达到 1001 行。

### `max-style-block-lines`

* **默认**：`thresholds.max_local_style_lines: 100`
* **简要说明**：单个 `<style>` 样式块的行数不允许超过阈值。
* **触发范围**：文件中所有 `<style>` ... `</style>` 闭合块。
* **判定逻辑**：提取 `<style>` 标签内部的内容，若以换行符切分后的行数大于阈值，在该 `<style>` 标签起始行报错。
* **正例**：将超长样式提取为外部 `.css` / `.scss` 文件并引入，或重构拆分组件。

---

## style —— CSS 与样式规范

### `max-inline-styles`

* **默认**：`thresholds.max_inline_styles_per_file: 3`
* **简要说明**：单个文件内允许出现的内联样式（`style="..."` 或 `:style="{...}"`）次数不能超过阈值。
* **判定逻辑**：使用正则匹配形如 `style="`、`style='` 或 `:style="{` 的语法，统计文件内匹配总次数，超限则在文件第 1 行报错。

### `max-style-classes`

* **默认**：`thresholds.max_style_classes: 8`
* **简要说明**：单文件内所有的 `<style>` 块中定义的自定义 Class 数量总和不能超过限制。
* **判定逻辑**：通过正则收集所有 `<style>` 块中定义的形如 `.class-name` 的选择器，去重后计算数量，超限在文件第 1 行报错。
* **目的**：鼓励使用原子化 CSS（UnoCSS），避免在文件中定义过多局部样式类。

### `prefer-unocss-shortcut`

* **默认**：`enabled: true`
* **简要说明**：若某个 CSS 类包含的声明完全等价于 `config.json` 中配置的 UnoCSS Shortcut，则必须替换为该 Shortcut。
* **判定逻辑**：

1. 提取顶层样式规则（跳过嵌套规则和覆写样式）。
2. 若选择器为 UI 库覆写（命中 `unocss.override_class_prefixes` 或包含 `::v-deep` 等 `override_selector_patterns`），跳过。
3. 将类的 CSS 声明标准化后（转小写），与 `unocss.shortcuts` 配置项进行集合比对。
4. 若完全匹配某个 Shortcut，在类定义所在行报错，提示替换。

### `prefer-unocss`

* **默认**：`unocss.max_replaceable_decls: 3`
* **简要说明**：若某个 CSS 类的声明数不超过阈值，且 **每一条** 声明都有对应的 UnoCSS 工具类可替换（在 `unocss.utilities` 中已配置），则要求将其直接替换为 UnoCSS 原子类。
* **判定逻辑**：

1. 类似 Shortcut 规则提取纯净的 CSS 声明集合。
2. 判断声明总数是否 `≤ max_replaceable_decls`。
3. 如果所有声明都在 `unocss.utilities` 映射表内，报错并直接在提示信息中给出可替换的 UnoCSS class 列表。

### `no-hardcoded-color`

* **默认**：`enabled: true`
* **简要说明**：代码中禁止硬编码 16 进制颜色值（如 `#FFF`, `#333333`），应统一提取并使用 CSS 变量（`var(--xxx)`）。
* **判定逻辑**：

1. 逐行扫描源文件，排除单行/多行注释。
2. 正则查找符合 16 进制颜色规则的值，若前缀不为 `var(`。
3. 将颜色转为小写，如果在 `hardcoded.color_whitelist` 白名单中，跳过。
4. 否则立即在该行报错。

### `no-repeated-hardcoded-size`

* **默认**：`hardcoded.size_repeat_threshold: 3`
* **简要说明**：代码中硬编码的尺寸（包含 px, rem, em, vh, vw, % 单位的数值）在 **全项目** 范围内重复出现次数达到阈值时，强制要求提取为 CSS 变量。
* **判定逻辑**：

1. 单文件扫描阶段：收集所有未在 `hardcoded.size_whitelist` 中的硬编码尺寸。
2. 汇总阶段：计算每个硬编码尺寸（如 `14px`）在全体检查文件中的出现总数。
3. 若 `总数 >= size_repeat_threshold`（默认 3），则追溯其出现的所有文件和行号，分别报错。

---

## import —— 模块导入规范

### `no-redundant-auto-import`

* **默认**：`enabled: true`
* **简要说明**：禁止手动导入已在全局配置自动导入的 Vue 核心 API 或 UI 库组件（如 `vue`，`element-plus` 等）。
* **判定逻辑**：

1. 正则匹配 `import ... from '...'`（自动忽略 `import type`）。
2. 提取来源包名（如 `vue`），比对 `auto_imports.packages` 与 `auto_imports.ui_packages`。
3. 若匹配，在导入行报错，提示移除该冗余引入。

* **违例**：`import { ref, reactive } from 'vue';`

### `no-redundant-auto-component`

* **默认**：`enabled: true`
* **简要说明**：禁止手动导入 `src/components/` 或 `@/components/` 目录下的共享组件（框架已支持组件自动按需注册）。
* **判定逻辑**：

1. 正则查找来源属于 `(@|.*src)/components/...` 的导入语句。
2. 提取导入绑定的变量名，与组件文件名（或 index.vue 的父目录名）比对。
3. 若名称一致，在导入行报错，提示直接在模板中使用，无需 import。

* **正例**：无需 import，直接 `<CustomTable />`。

---

## duplication —— 重复代码检查

### `no-duplicated-block`

* **默认**：`thresholds.duplication_window_size: 30`, `max_duplication_instances: 2`
* **简要说明**：检测项目内连续超过一定行数的代码块是否存在重复复制粘贴。
* **触发范围**：所有目标检查文件，全量跨文件 / 同文件比对。
* **判定逻辑**：

1. **归一化**：去除每行的单行/多行注释以及两端空格。
2. **滑动窗口**：以 `duplication_window_size` (默认 30) 为窗口大小滑动，提取连续代码块。若代码块字符总长度满足下限，计算其 MD5 哈希值。
3. **重复判定**：若某个 MD5 哈希出现的独立位置（去重后）数量 `> max_duplication_instances` (默认 2)。
4. **报告**：在首个出现该代码块的文件行报错，并在报错信息中列出其他重复出现的具体文件路径和行号。

* **配置与忽略**：如果某些特定的模板或复杂组件不可避免地存在一定结构相似，可通过调整 `thresholds` 节点或将其加入 `.checkignore` 绕过。
