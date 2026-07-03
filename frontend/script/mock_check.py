#!/usr/bin/env python3
"""
mock_check.py —— Mock 接口覆盖检查

比对 `src/api/*.api.ts` 中声明的后端接口与 `mock/*.mock.ts` 中注册的 mock 路由，
找出「有接口、无 mock」以及「有 mock、无接口」的偏差，并以 ESLint 风格输出报告。

用法：
    python script/mock_check.py [-a src/api] [-m mock] [--strict]

退出码：
    0  全部接口均已被 mock 覆盖（无 error）
    1  存在未被 mock 的接口（error）；--strict 下孤立 mock 也算 error
"""
import os
import re
import sys
import argparse
from pathlib import Path

# Windows 终端启用 ANSI 颜色 & UTF-8 输出
if sys.platform == "win32":
    os.system("")
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass


# ANSI 颜色（ESLint 风格输出用，与 code_check.py 保持一致）
class C:
    RESET = "\033[0m"; BOLD = "\033[1m"; DIM = "\033[2m"
    RED = "\033[31m"; YELLOW = "\033[33m"; GREEN = "\033[32m"
    CYAN = "\033[36m"; UNDERLINE = "\033[4m"


# 默认代理前缀，读取不到 .env.development 时回退使用
DEFAULT_PROXY_PREFIX = "/dev-api"
# 默认 API 根路径，读取不到 src/api/config.ts 时回退使用
DEFAULT_API_BASE = "/api/v1"

# 同时捕获 url 与紧随其后的 method —— api 与 mock 文件中两者均成对相邻出现
API_URL_METHOD_RE = re.compile(
    r"""url:\s*(`[^`]*`|"[^"]*"|'[^']*'|[A-Za-z_$][\w$]*)"""
    r"""\s*,\s*method:\s*['"](\w+)['"]"""
)
MOCK_URL_METHOD_RE = re.compile(
    r"""url:\s*['"]([^'"]+)['"]\s*,\s*method:\s*['"](\w+)['"]"""
)
# 形如 `const FOO_BASE_URL = `${API_BASE}/foo`;` 的基础路径常量
CONST_RE = re.compile(
    r"""const\s+([A-Za-z_$][\w$]*)\s*=\s*(`[^`]*`|"[^"]*"|'[^']*')"""
)
TEMPLATE_SLOT_RE = re.compile(r"\$\{([^}]*)\}")


def strip_comments(text):
    """移除 JS 块注释与行注释，避免注释中的示例代码干扰解析"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"(?m)(^|[^:])//[^\n]*", r"\1", text)
    return text


def line_col(text, offset):
    """根据字符偏移量计算 1 基的行号与列号"""
    line = text.count("\n", 0, offset) + 1
    col = offset - text.rfind("\n", 0, offset)
    return line, col


def normalize_path(path):
    """把路径归一化：去掉查询串、路径参数统一为 :p、去掉末尾斜杠"""
    path = path.split("?", 1)[0].split("#", 1)[0]
    segments = []
    for seg in path.split("/"):
        if seg.startswith(":") or (seg.startswith("{") and seg.endswith("}")):
            segments.append(":p")
        else:
            segments.append(seg)
    norm = "/".join(segments)
    if len(norm) > 1 and norm.endswith("/"):
        norm = norm[:-1]
    return norm


def resolve_url_token(token, consts):
    """把 api 文件中的 url 取值解析为具体路径

    token 可能是：模板字符串 `${BASE}/x`、普通字符串字面量、或基础路径常量名。
    无法解析的 ${...} 槽位（如 ${id}）视为路径参数 :p。
    """
    if token[0] in "`\"'":
        quote, inner = token[0], token[1:-1]
        if quote != "`":
            return inner  # 普通字符串字面量

        def repl(match):
            expr = match.group(1).strip()
            return consts.get(expr, ":p")

        return TEMPLATE_SLOT_RE.sub(repl, inner)
    # 裸常量名
    return consts.get(token)


class Endpoint:
    """一条接口 / mock 路由记录"""

    __slots__ = ("method", "path", "file", "line", "col", "raw")

    def __init__(self, method, path, file, line, col, raw):
        self.method = method.upper()
        self.path = normalize_path(path)
        self.file = file
        self.line = line
        self.col = col
        self.raw = raw

    @property
    def key(self):
        return (self.method, self.path)

    @property
    def label(self):
        return f"{self.method} {self.path}"


class MockChecker:
    def __init__(self, api_dir, mock_dir, strict=False):
        script_dir = Path(__file__).resolve().parent
        self.project_root = script_dir.parent
        self.api_dir = (self.project_root / api_dir).resolve()
        self.mock_dir = (self.project_root / mock_dir).resolve()
        self.strict = strict
        self.api_base = self._load_api_base()
        self.proxy_prefix = self._load_proxy_prefix()
        # problems: { file_str: [ {line,col,severity,message,rule}, ... ] }
        self.problems = {}

    # ---------------------------------------------------------------- 环境读取

    def _load_api_base(self):
        """从 src/api/config.ts 读取 API_BASE"""
        config = self.api_dir / "config.ts"
        if config.exists():
            match = re.search(r"""API_BASE\s*=\s*['"]([^'"]+)['"]""", config.read_text(encoding="utf-8"))
            if match:
                return match.group(1)
        return DEFAULT_API_BASE

    def _load_proxy_prefix(self):
        """从 .env.development 读取 VITE_APP_BASE_API（mock url 携带的代理前缀）"""
        env = self.project_root / ".env.development"
        if env.exists():
            match = re.search(r"(?m)^\s*VITE_APP_BASE_API\s*=\s*(\S+)", env.read_text(encoding="utf-8"))
            if match:
                return match.group(1).strip()
        return DEFAULT_PROXY_PREFIX

    # ---------------------------------------------------------------- 解析

    def collect_api_endpoints(self):
        """解析 src/api 下所有 *.api.ts，返回接口记录列表"""
        endpoints = []
        for file in sorted(self.api_dir.glob("*.api.ts")):
            text = strip_comments(file.read_text(encoding="utf-8"))
            # 先解析基础路径常量，API_BASE 作为初始已知量
            consts = {"API_BASE": self.api_base}
            for m in CONST_RE.finditer(text):
                resolved = resolve_url_token(m.group(2), consts)
                if resolved is not None:
                    consts[m.group(1)] = resolved
            # 再解析每个 request 调用的 url / method
            for m in API_URL_METHOD_RE.finditer(text):
                path = resolve_url_token(m.group(1), consts)
                if not path:
                    continue
                line, col = line_col(text, m.start())
                endpoints.append(Endpoint(m.group(2), path, file, line, col, m.group(1)))
        return endpoints

    def collect_mock_routes(self):
        """解析 mock 下所有 *.mock.ts，返回 mock 路由记录列表"""
        routes = []
        for file in sorted(self.mock_dir.glob("*.mock.ts")):
            text = strip_comments(file.read_text(encoding="utf-8"))
            for m in MOCK_URL_METHOD_RE.finditer(text):
                url = m.group(1)
                # 去掉代理前缀，与 api 侧路径对齐
                if url == self.proxy_prefix:
                    url = "/"
                elif url.startswith(self.proxy_prefix + "/"):
                    url = url[len(self.proxy_prefix):]
                line, col = line_col(text, m.start())
                routes.append(Endpoint(m.group(2), url, file, line, col, m.group(1)))
        return routes

    # ---------------------------------------------------------------- 检查

    def _add(self, file, line, col, message, rule, severity):
        self.problems.setdefault(str(file), []).append({
            "line": line, "col": col, "severity": severity,
            "message": message, "rule": rule,
        })

    def run(self):
        if not self.api_dir.is_dir():
            print(f"{C.RED}✗ 未找到 API 目录：{self.api_dir}{C.RESET}")
            sys.exit(1)
        if not self.mock_dir.is_dir():
            print(f"{C.RED}✗ 未找到 mock 目录：{self.mock_dir}{C.RESET}")
            sys.exit(1)

        api_endpoints = self.collect_api_endpoints()
        mock_routes = self.collect_mock_routes()
        api_keys = {e.key for e in api_endpoints}
        mock_keys = {r.key for r in mock_routes}

        # 接口缺少 mock —— error
        seen = set()
        for ep in api_endpoints:
            if ep.key in mock_keys or ep.key in seen:
                continue
            seen.add(ep.key)
            self._add(
                ep.file, ep.line, ep.col,
                f"接口 {ep.label} 未配置 mock 处理",
                "missing-mock", "error",
            )

        # mock 路由无对应接口 —— warning（--strict 下升级为 error）
        severity = "error" if self.strict else "warning"
        seen = set()
        for route in mock_routes:
            if route.key in api_keys or route.key in seen:
                continue
            seen.add(route.key)
            self._add(
                route.file, route.line, route.col,
                f"mock 路由 {route.label} 在 src/api 中无对应接口",
                "orphan-mock", severity,
            )

        self.report(len(api_keys), len(mock_keys))

    # ---------------------------------------------------------------- 报告

    def _fmt_path(self, file_path):
        """相对项目根目录的路径，便于阅读"""
        try:
            return Path(file_path).resolve().relative_to(self.project_root).as_posix()
        except Exception:
            return str(file_path)

    def report(self, api_count, mock_count):
        # 去重 & 按文件 -> 行号 -> 列号排序
        for f in self.problems:
            seen, uniq = set(), []
            for e in self.problems[f]:
                k = (e["line"], e["col"], e["rule"], e["message"])
                if k in seen:
                    continue
                seen.add(k)
                uniq.append(e)
            uniq.sort(key=lambda e: (e["line"], e["col"]))
            self.problems[f] = uniq

        err_count = sum(1 for f in self.problems for e in self.problems[f] if e["severity"] == "error")
        warn_count = sum(1 for f in self.problems for e in self.problems[f] if e["severity"] == "warning")

        print(
            f"\n{C.DIM}扫描：{C.RESET}"
            f"{C.CYAN}{api_count}{C.RESET} 个 API 接口  ·  "
            f"{C.CYAN}{mock_count}{C.RESET} 条 mock 路由\n"
        )

        if not self.problems:
            print(f"{C.GREEN}✓ 全部 {api_count} 个 API 接口均已被 mock 覆盖{C.RESET}\n")
            sys.exit(0)

        # 计算列宽，对齐 ESLint 风格
        all_entries = [e for f in self.problems for e in self.problems[f]]
        loc_w = max(len(f"{e['line']}:{e['col']}") for e in all_entries)
        sev_w = max(len(e["severity"]) for e in all_entries)
        msg_w = max(len(e["message"]) for e in all_entries)

        for f in sorted(self.problems.keys()):
            print(f"{C.UNDERLINE}{self._fmt_path(f)}{C.RESET}")
            for e in self.problems[f]:
                loc = f"{e['line']}:{e['col']}"
                sev_color = C.RED if e["severity"] == "error" else C.YELLOW
                print(
                    f"  {C.DIM}{loc:<{loc_w}}{C.RESET}  "
                    f"{sev_color}{e['severity']:<{sev_w}}{C.RESET}  "
                    f"{e['message']:<{msg_w}}  "
                    f"{C.DIM}{e['rule']}{C.RESET}"
                )
            print()

        total = err_count + warn_count
        mark = f"{C.RED}✖{C.RESET}" if err_count else f"{C.YELLOW}⚠{C.RESET}"
        print(
            f"{mark} {C.BOLD}{total} problem{'s' if total != 1 else ''}{C.RESET} "
            f"({err_count} error{'s' if err_count != 1 else ''}, "
            f"{warn_count} warning{'s' if warn_count != 1 else ''})\n"
        )
        sys.exit(1 if err_count else 0)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="检查 src/api 接口是否都有对应的 mock")
    parser.add_argument("-a", "--api-dir", default="src/api", help="API 目录（相对项目根）")
    parser.add_argument("-m", "--mock-dir", default="mock", help="mock 目录（相对项目根）")
    parser.add_argument("--strict", action="store_true", help="把孤立 mock 也视为 error")
    args = parser.parse_args()
    MockChecker(args.api_dir, args.mock_dir, args.strict).run()
