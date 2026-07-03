import os
import re
import sys
import json
import hashlib
import fnmatch
from collections import defaultdict
from pathlib import Path
import argparse

# Windows 终端启用 ANSI 颜色 & UTF-8 输出
if sys.platform == "win32":
    os.system("")
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

# ==========================================
# 所有规则参数集中在 script/config.json 中维护
# ==========================================
CONFIG_FILE = "config.json"

REGEX_PATTERNS = {
    "inline_style": re.compile(r'style\s*=\s*(?:["\']|{\s*{)'),
    "local_style_block": re.compile(r'<style[^>]*>(.*?)</style>', re.DOTALL),
    "hardcoded_vars": re.compile(r'(?<!var\()(#(?:[0-9a-fA-F]{3}){1,2}\b|-?\b\d+(?:\.\d+)?(?:px|rem|em|vh|vw|%)\b)'),
    "import_stmt": re.compile(r'import\s+(?:(type)\s+)?([^;]+?)\s+from\s+["\']([^"\']+)["\']\s*;?', re.DOTALL),
    "shared_component_import": re.compile(
        r'import\s+(\w+)\s+from\s+["\'](?:@|[./\\]*src)\/components\/([^"\']+\.vue)["\']'
    ),
}

# 辅助正则：CSS 声明、class 选择器
RE_CSS_DECL = re.compile(r'^\s*([a-z-]+)\s*:\s*([^;{}]+?)\s*;?\s*$')
RE_CSS_CLASS = re.compile(r'(?<![\w-])\.([a-zA-Z_][\w-]*)')

# ANSI 颜色（ESLint 风格输出用）
class C:
    RESET = "\033[0m"; BOLD = "\033[1m"; DIM = "\033[2m"
    RED = "\033[31m"; YELLOW = "\033[33m"; GREEN = "\033[32m"
    CYAN = "\033[36m"; UNDERLINE = "\033[4m"

class FrontendLinter:
    # 默认阈值（仅在 config.json 缺项时回退使用）
    _DEFAULT_THRESHOLDS = {
        "max_file_lines": 1000,
        "max_inline_styles_per_file": 3,
        "max_local_style_lines": 100,
        "duplication_window_size": 30,
        "max_duplication_instances": 2,
        "max_style_classes": 8,
    }

    def __init__(self, target_dir, deprecate_file=".checkignore"):
        self.target_dir = Path(target_dir).resolve()
        script_dir = Path(__file__).resolve().parent
        self.script_dir = script_dir
        self.project_root = script_dir.parent
        self.deprecate_file_path = script_dir / deprecate_file
        self.errors = defaultdict(list)
        self.code_hashes = defaultdict(list)
        self.deprecated_patterns = []
        self.deprecated_skipped_count = 0
        self._load_deprecate_config()
        self._load_config()

    def _load_config(self):
        """加载统一配置文件 (script/config.json)"""
        path = self.script_dir / CONFIG_FILE
        cfg = {}
        if not path.exists():
            print(f"{C.YELLOW}⚠ Missing config file: {path}, using defaults{C.RESET}")
        else:
            try:
                with open(path, 'r', encoding='utf-8') as f:
                    cfg = json.load(f)
            except Exception as e:
                print(f"{C.YELLOW}⚠ Load config failed: {e}{C.RESET}")
        # thresholds
        t = {**self._DEFAULT_THRESHOLDS, **(cfg.get("thresholds") or {})}
        self.thresholds = t
        # 硬编码白名单 / 检查策略
        hc = cfg.get("hardcoded") or {}
        self.color_whitelist = {v.lower() for v in hc.get("color_whitelist", [])}
        self.size_whitelist = {v.lower() for v in hc.get("size_whitelist", [])}
        self.size_repeat_threshold = int(hc.get("size_repeat_threshold", 3))
        self.size_occurrences = defaultdict(list)  # value(lower) -> [(file, line, col, raw)]
        # 自动导入包
        ai = cfg.get("auto_imports") or {}
        self.auto_imported_packages = set(ai.get("packages", []))
        self.auto_imported_ui_packages = set(ai.get("ui_packages", []))
        # UnoCSS
        uno = cfg.get("unocss") or {}
        self.unocss_utilities = {k.lower(): v for k, v in (uno.get("utilities") or {}).items()}
        self.unocss_shortcuts = {
            name: frozenset(d.lower() for d in decls)
            for name, decls in (uno.get("shortcuts") or {}).items()
        }
        self.unocss_max_decls = int(uno.get("max_replaceable_decls", 3))
        self.unocss_override_patterns = [p.lower() for p in uno.get("override_selector_patterns", [])]
        self.unocss_override_prefixes = [p.lower() for p in uno.get("override_class_prefixes", [])]

    def _load_deprecate_config(self):
        """加载豁免配置文件 (类似 .gitignore)"""
        if not self.deprecate_file_path.exists(): return
        try:
            with open(self.deprecate_file_path, 'r', encoding='utf-8') as f:
                self.deprecated_patterns = [l.strip() for l in f if l.strip() and not l.startswith('#')]
        except Exception as e:
            print(f"⚠️ Load config error: {e}")

    def is_deprecated(self, file_path):
        """检查文件是否在废弃/豁免名单中"""
        try:
            rel_path = file_path.relative_to(self.project_root).as_posix()
            for p in self.deprecated_patterns:
                if fnmatch.fnmatch(rel_path, p) or rel_path.startswith(p.rstrip('/')):
                    return True
        except: pass
        return False

    def is_target_file(self, file_path):
        return file_path.suffix in ['.vue', '.jsx', '.tsx', '.html']

    def normalize_line(self, line):
        return re.sub(r'//.*|/\*.*?\*/|', '', line).strip()

    def scan_file(self, file_path):
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
                lines = content.split('\n')
        except: return

        # 1. 检查文件大小
        if len(lines) > self.thresholds["max_file_lines"]:
            self._add(file_path, 1, 1,
                f"File has {len(lines)} lines (max {self.thresholds['max_file_lines']})",
                "max-file-lines")

        # 2. 检查内联样式
        inline_matches = REGEX_PATTERNS["inline_style"].findall(content)
        if len(inline_matches) > self.thresholds["max_inline_styles_per_file"]:
            self._add(file_path, 1, 1,
                f"{len(inline_matches)} inline styles found (max {self.thresholds['max_inline_styles_per_file']})",
                "max-inline-styles")

        # 3. 检查 <style> 块：行数、UnoCSS 可替代样式、class 定义数量
        all_classes = set()
        for sm in REGEX_PATTERNS["local_style_block"].finditer(content):
            block = sm.group(1)
            block_start_line = content[:sm.start(1)].count('\n') + 1

            # 3a. <style> 块行数
            l_count = len(block.strip().split('\n'))
            if l_count > self.thresholds["max_local_style_lines"]:
                self._add(file_path, block_start_line, 1,
                    f"<style> block has {l_count} lines (max {self.thresholds['max_local_style_lines']})",
                    "max-style-block-lines")

            # 3b. 以"类规则"为粒度检查 UnoCSS 可替代项
            for sel, body, sel_offset in self._iter_top_level_rules(block):
                if self._is_override_selector(sel): continue  # 覆写样式跳过
                class_names = RE_CSS_CLASS.findall(sel)
                if not class_names: continue
                if any(self._is_override_class(c) for c in class_names): continue  # 含 UI 库 class 视为覆写
                decls = self._extract_top_level_decls(body)
                if not decls: continue
                # 标准化为 "prop:value" (小写)
                norm = []
                for d in decls:
                    if ':' not in d: continue
                    p, v = d.split(':', 1)
                    norm.append(f"{p.strip().lower()}:{v.strip().lower()}")
                if not norm: continue
                rule_line = block_start_line + block[:sel_offset].count('\n')
                primary = class_names[0]
                # i) 与某个自定义 shortcut 等价 -> 必须替换
                decl_set = frozenset(norm)
                matched_shortcut = next(
                    (name for name, s in self.unocss_shortcuts.items() if s == decl_set),
                    None,
                )
                if matched_shortcut:
                    self._add(file_path, rule_line, 1,
                        f"Class '.{primary}' is equivalent to UnoCSS shortcut '{matched_shortcut}', replace with it",
                        "prefer-unocss-shortcut")
                    continue
                # ii) <=N 条声明 且 全部可被 UnoCSS 工具类替代 -> 必须替换
                if len(norm) <= self.unocss_max_decls and all(d in self.unocss_utilities for d in norm):
                    utils = " ".join(self.unocss_utilities[d] for d in norm)
                    self._add(file_path, rule_line, 1,
                        f"Class '.{primary}' ({len(norm)} decl{'s' if len(norm)!=1 else ''}) is fully replaceable by UnoCSS: {utils}",
                        "prefer-unocss")

            # 3c. 收集 class 选择器
            for cm in RE_CSS_CLASS.finditer(block):
                all_classes.add(cm.group(1))

        if len(all_classes) > self.thresholds["max_style_classes"]:
            self._add(file_path, 1, 1,
                f"{len(all_classes)} CSS classes defined across <style> blocks (max {self.thresholds['max_style_classes']})",
                "max-style-classes")

        # 4. 检查硬编码：颜色立即报错；尺寸聚合到全项目层面，仅当重复出现 >= N 次时建议提取为变量
        for idx, line in enumerate(lines):
            if line.strip().startswith(('//', '/*', '*')): continue
            for m in REGEX_PATTERNS["hardcoded_vars"].finditer(line):
                raw = m.group(0)
                lv = raw.lower()
                col = m.start() + 1
                if raw.startswith('#'):
                    if lv in self.color_whitelist: continue
                    self._add(file_path, idx + 1, col,
                        f"Hardcoded color '{raw}' should use a CSS variable",
                        "no-hardcoded-color")
                else:
                    if lv in self.size_whitelist: continue
                    self.size_occurrences[lv].append((str(file_path), idx + 1, col, raw))

        # 5. 检查重复导入全局自动导入的内容
        self._check_redundant_imports(file_path, content)

        # 6. 收集哈希用于重复检测
        self._collect_duplication_hashes(file_path, lines)

    def _add(self, file_path, line, col, message, rule, severity="error"):
        self.errors[str(file_path)].append({
            "line": line, "col": col, "severity": severity,
            "message": message, "rule": rule,
        })

    def _is_override_selector(self, selector):
        """判断选择器是否为覆写样式（如 :deep(...)、::v-deep 等）"""
        sl = selector.lower()
        return any(p in sl for p in self.unocss_override_patterns)

    def _is_override_class(self, class_name):
        """判断 class 名是否为 UI 库覆写（如 el-button、ant-input 等）"""
        cl = class_name.lower()
        return any(cl.startswith(p) for p in self.unocss_override_prefixes)

    def _iter_top_level_rules(self, block):
        """yield (selector, body, selector_offset)，遍历样式块顶层规则（不进入嵌套）"""
        i, n, sel_start = 0, len(block), 0
        while i < n:
            c = block[i]
            if c == '{':
                selector = block[sel_start:i].strip()
                j, depth = i + 1, 1
                while j < n and depth > 0:
                    if block[j] == '{': depth += 1
                    elif block[j] == '}': depth -= 1
                    j += 1
                body = block[i + 1:j - 1] if j > i + 1 else ""
                if selector:
                    yield (selector, body, sel_start)
                sel_start, i = j, j
                continue
            if c == '}':
                # 顶层意外的 '}'：跳过
                sel_start = i + 1
            i += 1

    def _extract_top_level_decls(self, body):
        """提取规则体里仅属于本规则（非嵌套）的 `prop: value` 声明"""
        decls, buf, i, n = [], [], 0, len(body)
        while i < n:
            c = body[i]
            if c == '{':
                # 该 buffer 实为嵌套规则的 selector，整体丢弃；并跳过其完整 body
                buf = []
                depth, i = 1, i + 1
                while i < n and depth > 0:
                    if body[i] == '{': depth += 1
                    elif body[i] == '}': depth -= 1
                    i += 1
                continue
            if c == ';':
                d = ''.join(buf).strip()
                if d and ':' in d: decls.append(d)
                buf = []
            else:
                buf.append(c)
            i += 1
        tail = ''.join(buf).strip()
        if tail and ':' in tail:
            decls.append(tail)
        return decls

    def _extract_value_names(self, clause):
        """从 import 子句中提取值名称（排除 `type Foo` 形式）"""
        clause = clause.strip()
        default_part, named_part = None, None
        if clause.startswith("{"):
            named_part = clause
        elif "{" in clause:
            idx = clause.find("{")
            head = clause[:idx].rstrip(", \t\n")
            default_part = head if head else None
            named_part = clause[idx:]
        else:
            default_part = clause
        names = []
        if default_part and not default_part.startswith("*"):
            names.append(default_part.split(" as ")[0].strip())
        if named_part:
            inner = named_part.strip().strip("{}")
            for item in inner.split(","):
                item = item.strip()
                if not item: continue
                if item.startswith("type "): continue
                names.append(item.split(" as ")[0].strip())
        return [n for n in names if n]

    def _check_redundant_imports(self, file_path, content):
        """检查是否重复导入了已全局自动导入的 vue 函数 / UI 组件"""
        for m in REGEX_PATTERNS["import_stmt"].finditer(content):
            if m.group(1): continue  # `import type ... from ...` 仅类型导入，跳过
            clause, source = m.group(2), m.group(3)
            line_no = content[:m.start()].count('\n') + 1
            names = self._extract_value_names(clause)
            if not names: continue
            if source in self.auto_imported_packages or source in self.auto_imported_ui_packages:
                self._add(file_path, line_no, 1,
                    f"'{', '.join(names)}' is auto-imported from '{source}', remove this import",
                    "no-redundant-auto-import")
        # 仅检查顶级共享组件目录 src/components/ 下的组件
        for m in REGEX_PATTERNS["shared_component_import"].finditer(content):
            binding, rel = m.group(1), m.group(2)
            stem = Path(rel).stem
            if stem == "index":
                stem = Path(rel).parent.name
            # 别名重命名情况保留
            if binding == stem:
                line_no = content[:m.start()].count('\n') + 1
                self._add(file_path, line_no, 1,
                    f"Component '{binding}' is auto-imported from 'src/components/', remove this import",
                    "no-redundant-auto-component")

    def _collect_duplication_hashes(self, file_path, lines):
        ws = self.thresholds["duplication_window_size"]
        valid_lines = [self.normalize_line(l) for l in lines]
        for i in range(len(valid_lines) - ws + 1):
            block = "".join(valid_lines[i:i+ws])
            if len(block) > ws * 5:
                h = hashlib.md5(block.encode('utf-8')).hexdigest()
                self.code_hashes[h].append((str(file_path), i + 1))

    def analyze_duplication(self):
        for h, locs in self.code_hashes.items():
            unique_locs = list(set(locs))
            if len(unique_locs) > self.thresholds["max_duplication_instances"]:
                first_file, first_line = unique_locs[0]
                others = [f"{Path(p).name}:{l}" for p, l in unique_locs]
                self._add(first_file, first_line, 1,
                    f"Duplicated block found in: {', '.join(others)}",
                    "no-duplicated-block")

    def run(self):
        for root, _, files in os.walk(self.target_dir):
            if any(x in root for x in ['node_modules', 'dist', '.git', 'build']): continue
            for f in files:
                f_path = Path(root) / f
                if self.is_target_file(f_path):
                    if self.is_deprecated(f_path):
                        self.deprecated_skipped_count += 1
                        continue
                    self.scan_file(f_path)
        self.analyze_duplication()
        self._finalize_hardcoded_sizes()
        self.report()

    def _finalize_hardcoded_sizes(self):
        """汇总尺寸硬编码：同值在全项目重复使用 >= 阈值次才报错，提示提取为 CSS 变量"""
        t = self.size_repeat_threshold
        if t <= 0: return  # 0 / 负值 = 关闭尺寸检查
        for value, occs in self.size_occurrences.items():
            if len(occs) < t: continue
            for fp, line, col, raw in occs:
                self._add(fp, line, col,
                    f"Hardcoded size '{raw}' used {len(occs)} times across project, extract to a CSS variable",
                    "no-repeated-hardcoded-size")

    def _fmt_path(self, file_path):
        """相对项目根目录的路径，便于阅读"""
        try:
            return Path(file_path).resolve().relative_to(self.project_root).as_posix()
        except Exception:
            return str(file_path)

    def report(self):
        # 去重 & 按文件 -> 行号 -> 列号排序
        for f in self.errors:
            seen, uniq = set(), []
            for e in self.errors[f]:
                key = (e["line"], e["col"], e["rule"], e["message"])
                if key in seen: continue
                seen.add(key); uniq.append(e)
            uniq.sort(key=lambda e: (e["line"], e["col"]))
            self.errors[f] = uniq

        err_count = sum(1 for f in self.errors for e in self.errors[f] if e["severity"] == "error")
        warn_count = sum(1 for f in self.errors for e in self.errors[f] if e["severity"] == "warning")
        skip_info = f"  {C.DIM}(skipped {self.deprecated_skipped_count} ignored){C.RESET}" if self.deprecated_skipped_count else ""

        if not self.errors:
            print(f"\n{C.GREEN}✓ No problems found{C.RESET}{skip_info}\n")
            exit(0)

        # 计算列宽，对齐 ESLint 风格
        all_entries = [e for f in self.errors for e in self.errors[f]]
        loc_w = max(len(f"{e['line']}:{e['col']}") for e in all_entries)
        sev_w = max(len(e["severity"]) for e in all_entries)
        msg_w = max(len(e["message"]) for e in all_entries)

        for f in sorted(self.errors.keys()):
            print(f"\n{C.UNDERLINE}{self._fmt_path(f)}{C.RESET}")
            for e in self.errors[f]:
                loc = f"{e['line']}:{e['col']}"
                sev_color = C.RED if e["severity"] == "error" else C.YELLOW
                print(
                    f"  {C.DIM}{loc:<{loc_w}}{C.RESET}  "
                    f"{sev_color}{e['severity']:<{sev_w}}{C.RESET}  "
                    f"{e['message']:<{msg_w}}  "
                    f"{C.DIM}{e['rule']}{C.RESET}"
                )

        total = err_count + warn_count
        mark = f"{C.RED}✖{C.RESET}" if err_count else f"{C.YELLOW}⚠{C.RESET}"
        print(
            f"\n{mark} {C.BOLD}{total} problem{'s' if total != 1 else ''}{C.RESET} "
            f"({err_count} error{'s' if err_count != 1 else ''}, "
            f"{warn_count} warning{'s' if warn_count != 1 else ''}){skip_info}\n"
        )
        exit(1 if err_count else 0)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("-d", "--dir", default="./src", help="Target directory")
    args = parser.parse_args()
    FrontendLinter(args.dir).run()