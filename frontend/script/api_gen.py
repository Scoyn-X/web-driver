#!/usr/bin/env python3
"""
api_gen.py — 依据 OpenAPI 文档自动生成前端 TypeScript 接口层。

用法:
    python script/api_gen.py            # 默认：生成到 src/api/generated/
    python script/api_gen.py --merge    # merge：按 URL 路径匹配并合并进 src/api/ 手写接口
                                        #   （执行前会在终端二次确认；加 --yes 可跳过确认）

merge 模式:
    - 按 (HTTP 方法 + 路径) 自动匹配 src/api/ 下手写接口里的同路径函数，整体替换为
      依据 spec 生成的版本（函数名 / 参数 / 类型均采用生成版），并同步改写整个 src/
      代码库里对该函数的引用；
    - spec 中全新的接口直接追加（已有模块追加进对应文件，全新模块新建文件）；
    - 不再生成 src/api/generated/ 目录。

流程:
    1. get_open_api()  通过 Apifox 分享文档导出接口拉取 OpenAPI 3.0 文档；
    2. 解析 paths  ->  按 URL 路径前缀分模块；
    3. 解析 components.schemas  ->  把每个模块用到的请求对象 / 响应对象 / 枚举
       内联进各自的 src/api/generated/<module>.api.ts（跨模块共用的模型会复制到
       每个用到它的模块文件中），并导出带详细 JSDoc 的 XxxAPI 对象；
    4. 生成 src/api/generated/index.ts 统一出口。

约定:
    - 生成的文件每次运行会被【完全覆盖重写】，请勿手动修改；
    - 请求底层复用项目现有的 @/utils/request（自带 token 注入、Result 解包与错误提示），
      因此响应类型为后端 Result<T> 解包后的 data 类型。

依赖: 仅标准库。
"""

import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

# ============================================================================
# 配置区
# ============================================================================

APIFOX_EXPORT_URL = "https://s.apifox.cn/api/v1/projects/7938425/shared-docs/ca65c85a-df6d-4c7f-890d-d235763b9675/export-data"
APIFOX_PAYLOAD = {
    "type": "openapi",
    "id": "ca65c85a-df6d-4c7f-890d-d235763b9675",
    "version": "3.0",
    "moduleId":7811973,
    "excludeExtension": True,
    "excludeTagsWithFolder": False,
    "projectId": 7938425,
}

# OpenAPI 路径公共前缀；生成 URL 时会被替换为 `${API_BASE}`
API_PREFIX = "/api/v1"

# 输出目录：<repo>/src/api/generated
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "src" / "api" / "generated"

# 生成文件中的导入路径
REQUEST_IMPORT = "@/utils/request"   # 请求底层
CONFIG_IMPORT = "../config"          # API_BASE 来源（src/api/config.ts）

# 路径首段 -> 模块名归一化（复数转单数、同义模块合并）
MODULE_ALIASES = {
    "directories": "directory",
    "files": "file",
    "permissions": "permission",
    "roles": "role",
    "users": "user",
    "visits": "visit",
    "s": "share",       # /api/v1/s/{shareToken}  分享链接访问
    "shares": "share",  # /api/v1/shares          个人分享管理
}

HTTP_METHODS = ("get", "post", "put", "delete", "patch", "head", "options")


def get_open_api():
    """通过 Apifox 分享文档导出接口获取 OpenAPI 3.0 文档（返回 dict）。"""
    req = urllib.request.Request(
        APIFOX_EXPORT_URL,
        data=json.dumps(APIFOX_PAYLOAD).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


# ============================================================================
# 命名 / 文本工具
# ============================================================================

_ID_RE = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$]*$")


def ts_ident(name):
    """把任意字符串转换为合法的 TS 标识符。"""
    n = re.sub(r"[^0-9A-Za-z_$]", "_", str(name))
    if not n:
        n = "_"
    if n[0].isdigit():
        n = "_" + n
    return n


def split_words(s):
    return [w for w in re.split(r"[^0-9A-Za-z]+", str(s)) if w]


def to_camel(s):
    """转 camelCase（保留单词内部已有的大小写，仅处理首字母与分隔符）。"""
    words = split_words(s)
    if not words:
        return "op"
    head = words[0]
    head = head[0].lower() + head[1:]
    name = head + "".join(w[0].upper() + w[1:] for w in words[1:])
    if name[0].isdigit():
        name = "op" + name
    return name


def to_pascal(s):
    words = split_words(s)
    return "".join(w[0].upper() + w[1:] for w in words) or "X"


def prop_key(k):
    """对象属性键：合法标识符直接用，否则加引号。"""
    return k if _ID_RE.match(k) else json.dumps(k, ensure_ascii=False)


def clean_comment(text):
    """清洗注释文本，避免出现提前结束注释块的 `*/`。"""
    return str(text).replace("*/", "* /").strip()


def collect_refs(node, acc):
    """递归收集一个 schema 节点中出现的所有 $ref 名称。"""
    if isinstance(node, dict):
        ref = node.get("$ref")
        if isinstance(ref, str):
            acc.add(ref.split("/")[-1])
        for v in node.values():
            collect_refs(v, acc)
    elif isinstance(node, list):
        for v in node:
            collect_refs(v, acc)


# ============================================================================
# 生成器
# ============================================================================

class ApiGenerator:
    def __init__(self, spec):
        self.spec = spec
        self.schemas = spec.get("components", {}).get("schemas", {})
        self.used = set()        # 被引用到的 schema 原始名集合（闭包后为完整依赖）
        self.local_refs = set()  # 处理单个操作时临时收集其引用到的 schema 名
        self.modules = {}        # module -> [func, ...]
        self.func_names = {}     # module -> 已占用的函数名集合
        self.model_entries = {}  # 原始名 -> {kind, ...}
        self.model_alias = {}    # 同结构重复定义：别名 -> 规范名
        self.model_rename = {}   # 唯一编号副本 -> 还原后的干净命名
        self.base_map = {}       # 当前文件的根路径常量：URL 前缀 -> 常量名

    # ------------------------------------------------------------------
    # schema -> TypeScript 类型
    # ------------------------------------------------------------------
    def deref(self, schema):
        """顺着 $ref 解引用，返回最终的 schema dict。"""
        seen = 0
        while isinstance(schema, dict) and "$ref" in schema and seen < 20:
            name = schema["$ref"].split("/")[-1]
            schema = self.schemas.get(name, {})
            seen += 1
        return schema if isinstance(schema, dict) else {}

    def ts_type(self, schema):
        """把 OpenAPI schema 节点转换为 TS 类型表达式字符串。"""
        if not isinstance(schema, dict) or not schema:
            return "unknown"

        if "$ref" in schema:
            raw = schema["$ref"].split("/")[-1]
            self.used.add(raw)
            self.local_refs.add(raw)
            return ts_ident(raw)

        if "allOf" in schema:
            parts = [self.ts_type(s) for s in schema["allOf"]]
            parts = [p for p in parts if p and p != "unknown"]
            return " & ".join(dict.fromkeys(parts)) if parts else "unknown"

        for key in ("oneOf", "anyOf"):
            if key in schema:
                parts = [self.ts_type(s) for s in schema[key] if s]
                uniq = list(dict.fromkeys(p for p in parts if p))
                return " | ".join(uniq) if uniq else "unknown"

        if "enum" in schema:
            return " | ".join(self._literal(v) for v in schema["enum"]) or "unknown"

        t = schema.get("type")
        if isinstance(t, list):
            non_null = [x for x in t if x != "null"]
            base = self.ts_type({**schema, "type": non_null[0]}) if non_null else "unknown"
            return base + " | null" if "null" in t else base

        if t == "array":
            inner = self.ts_type(schema.get("items", {}))
            if re.search(r"[ |&]", inner):
                inner = "(" + inner + ")"
            return inner + "[]"

        if t == "object" or "properties" in schema:
            props = schema.get("properties") or {}
            if not props:
                ap = schema.get("additionalProperties")
                if isinstance(ap, dict):
                    return "Record<string, " + self.ts_type(ap) + ">"
                return "Record<string, unknown>"
            required = set(schema.get("required", []))
            fields = []
            for k, v in props.items():
                opt = "" if k in required else "?"
                fields.append(prop_key(k) + opt + ": " + self.ts_type(v))
            return "{ " + "; ".join(fields) + " }"

        if t in ("integer", "number"):
            return "number"
        if t == "boolean":
            return "boolean"
        if t == "string":
            return "Blob" if schema.get("format") == "binary" else "string"
        return "unknown"

    @staticmethod
    def _literal(v):
        if isinstance(v, bool):
            return "true" if v else "false"
        if isinstance(v, str):
            return json.dumps(v, ensure_ascii=False)
        return json.dumps(v)

    def close_used(self):
        """对 self.used 做依赖闭包：补全所有被嵌套引用的 schema。"""
        frontier = set(self.used)
        while frontier:
            nxt = set()
            for name in frontier:
                sch = self.schemas.get(name)
                if not isinstance(sch, dict):
                    continue
                refs = set()
                collect_refs(sch, refs)
                for r in refs:
                    if r not in self.used:
                        self.used.add(r)
                        nxt.add(r)
            frontier = nxt

    # ------------------------------------------------------------------
    # 解析操作（paths）
    # ------------------------------------------------------------------
    def process(self):
        for path, item in self.spec.get("paths", {}).items():
            if not isinstance(item, dict):
                continue
            shared = item.get("parameters", []) or []
            for method, op in item.items():
                if method.lower() in HTTP_METHODS and isinstance(op, dict):
                    self._process_op(path, method.lower(), op, shared)

    def _process_op(self, path, method, op, shared_params):
        self.local_refs = set()
        module = self._module_of(path)
        params = list(shared_params) + list(op.get("parameters", []) or [])
        declared_path = {p["name"]: p for p in params if p.get("in") == "path"}
        query_params = [p for p in params if p.get("in") == "query"]

        # 路径参数：以 URL 中的占位符为准（支持 {*filePath} 通配），按出现顺序排列
        path_params = []
        for raw_name in re.findall(r"\{\*?([^}]+)\}", path):
            decl = declared_path.get(raw_name)
            if decl and isinstance(decl.get("schema"), dict):
                ptype = self.ts_type(decl["schema"])
            else:
                ptype = "string"
            path_params.append({
                "name": ts_ident(raw_name),
                "type": ptype,
                "desc": (decl or {}).get("description", ""),
            })

        body = self._parse_body(op.get("requestBody"))

        data_schema = self._extract_data(op)
        is_download = self._is_download(path, op, data_schema)
        ret_type = "Blob" if is_download else self._return_type(data_schema)

        queries = []
        for p in query_params:
            sch = p.get("schema", {}) or {}
            queries.append({
                "name": ts_ident(p["name"]),
                "raw": p["name"],
                "type": self.ts_type(sch) or "unknown",
                "required": bool(p.get("required")),
                "default": sch.get("default") if isinstance(sch, dict) else None,
                "desc": p.get("description", ""),
            })

        func = {
            "name": self._unique_func_name(module, op.get("operationId") or (method + path)),
            "summary": op.get("summary") or op.get("operationId") or "",
            "description": op.get("description") or "",
            "deprecated": bool(op.get("deprecated")),
            "method": method,
            "path": path,
            "path_params": path_params,
            "query_params": queries,
            "body": body,
            "ret_type": ret_type,
            "ret_desc": self._data_desc(data_schema),
            "is_download": is_download,
            "types": set(self.local_refs),
        }
        self.modules.setdefault(module, []).append(func)

    def _module_of(self, path):
        rest = path[len(API_PREFIX):] if path.startswith(API_PREFIX) else path
        seg = [s for s in rest.split("/") if s]
        first = seg[0] if seg else "misc"
        return MODULE_ALIASES.get(first, first)

    def _build_url(self, path):
        """把 OpenAPI 路径转换为 TS 表达式。

        优先复用 self.base_map 中已抽取的根路径常量（最长前缀匹配）；
        命中且路径恰为该前缀时直接返回常量名，否则拼成模板字符串；
        未命中则降级为 ${API_BASE} 前缀。占位符 {x}/{*x} 统一替换为 ${x}。
        """
        def fill(s):
            return re.sub(r"\{\*?([^}]+)\}",
                          lambda m: "${" + ts_ident(m.group(1)) + "}", s)

        best = None
        for prefix, const in self.base_map.items():
            if (path == prefix or path.startswith(prefix + "/")) \
                    and (best is None or len(prefix) > len(best[0])):
                best = (prefix, const)
        if best:
            prefix, const = best
            remainder = path[len(prefix):]
            return const if not remainder else "`${" + const + "}" + fill(remainder) + "`"

        rest = path[len(API_PREFIX):] if path.startswith(API_PREFIX) else path
        return "`${API_BASE}" + fill(rest) + "`"

    def _base_consts(self, funcs):
        """按路径首段抽取根路径常量声明，返回 (声明文本, {URL 前缀: 常量名})。"""
        segs = {}
        for f in funcs:
            path = f["path"]
            rest = path[len(API_PREFIX):] if path.startswith(API_PREFIX) else path
            parts = [p for p in rest.split("/") if p]
            if parts and not parts[0].startswith("{"):
                segs.setdefault(parts[0], API_PREFIX + "/" + parts[0])
        decls, base_map = [], {}
        for seg in sorted(segs):
            const = re.sub(r"[^0-9A-Za-z]+", "_", seg).strip("_").upper() + "_BASE_URL"
            decls.append("const " + const + " = `${API_BASE}/" + seg + "`;")
            base_map[segs[seg]] = const
        return "\n".join(decls), base_map

    def _unique_func_name(self, module, raw):
        base = to_camel(raw)
        used = self.func_names.setdefault(module, set())
        name, i = base, 2
        while name in used:
            name = base + str(i)
            i += 1
        used.add(name)
        return name

    def _parse_body(self, request_body):
        """解析 requestBody：返回 None / {mode:'json'} / {mode:'form'}。"""
        if not isinstance(request_body, dict):
            return None
        content = request_body.get("content", {})
        if not content:
            return None

        ctype = None
        for cand in ("multipart/form-data", "application/json"):
            if cand in content:
                ctype = cand
                break
        if ctype is None:
            ctype = next(iter(content))

        schema = content[ctype].get("schema", {}) or {}
        resolved = self.deref(schema)
        props = resolved.get("properties") or {}
        req_set = set(resolved.get("required", []))
        has_binary = any(self.deref(v).get("format") == "binary" for v in props.values())

        # 含二进制字段 或 multipart  ->  作为 FormData 上传处理
        if (ctype.startswith("multipart/") or has_binary) and props:
            fields = []
            for k, v in props.items():
                dv = self.deref(v)
                is_file = dv.get("format") == "binary"
                fields.append({
                    "name": ts_ident(k),
                    "raw": k,
                    "type": "File" if is_file else self.ts_type(v),
                    "is_file": is_file,
                    "required": k in req_set,
                    "desc": v.get("description", "") if isinstance(v, dict) else "",
                })
            return {"mode": "form", "fields": fields}

        # 普通 JSON 请求体
        return {
            "mode": "json",
            "type": self.ts_type(schema),
            "required": bool(request_body.get("required")),
            "desc": resolved.get("description", ""),
        }

    def _extract_data(self, op):
        """从 2xx 响应中取出 Result<T> 信封里的 data 子 schema（未包裹则返回响应体本身）。"""
        responses = op.get("responses", {}) or {}
        resp = None
        for code in ("200", "201"):
            if code in responses:
                resp = responses[code]
                break
        if resp is None:
            for code, r in responses.items():
                if str(code).startswith("2"):
                    resp = r
                    break
        if not isinstance(resp, dict):
            return None

        content = resp.get("content", {})
        if not content:
            return None
        schema = next(iter(content.values()), {}).get("schema")
        if not schema:
            return None

        # allOf: [Result, { data: 实际类型 }]  —— 后出现的 data 覆盖前者
        if "allOf" in schema:
            found = None
            for sub in schema["allOf"]:
                d = (self.deref(sub).get("properties") or {}).get("data")
                if d is not None and (found is None or not self._is_empty(d)):
                    found = d
            return found

        resolved = self.deref(schema)
        props = resolved.get("properties") or {}
        if "data" in props:
            return props["data"]
        return schema

    @staticmethod
    def _is_empty(schema):
        """判断一个 schema 是否“空”（无实际结构，对应返回 void）。"""
        if not isinstance(schema, dict) or not schema:
            return True
        if any(k in schema for k in ("$ref", "allOf", "oneOf", "anyOf", "enum", "items")):
            return False
        if schema.get("type") in ("object", None) \
                and not schema.get("properties") \
                and not schema.get("additionalProperties"):
            return True
        return False

    def _return_type(self, data_schema):
        if data_schema is None or self._is_empty(data_schema):
            return "void"
        return self.ts_type(data_schema) or "void"

    def _is_download(self, path, op, data_schema):
        """判断是否为文件下载接口（响应应作为 Blob 处理）。"""
        if isinstance(data_schema, dict) and data_schema.get("format") == "binary":
            return True
        if isinstance(data_schema, dict) and self.deref(data_schema).get("format") == "binary":
            return True
        if "{*" in path:  # 通配路径只可能是文件流
            return True
        empty = data_schema is None or self._is_empty(data_schema)
        summary = op.get("summary") or ""
        if empty and (path.rstrip("/").endswith("/download") or "下载" in summary):
            return True
        return False

    def _data_desc(self, data_schema):
        if isinstance(data_schema, dict):
            if "$ref" in data_schema:
                ref = self.schemas.get(data_schema["$ref"].split("/")[-1], {})
                return ref.get("description", "") if isinstance(ref, dict) else ""
            return data_schema.get("description", "")
        return ""

    # ------------------------------------------------------------------
    # 构建数据模型（含同结构去重）
    # ------------------------------------------------------------------
    def build_models(self):
        self.close_used()
        entries = {}
        for raw in sorted(self.used):
            sch = self.schemas.get(raw)
            if not isinstance(sch, dict):
                continue
            if "enum" in sch:
                values = [self._literal(v) for v in sch["enum"]]
                entries[raw] = {
                    "kind": "enum", "values": values,
                    "desc": sch.get("description", ""),
                    "sig": ("enum", tuple(values)),
                }
            elif sch.get("properties"):
                req = set(sch.get("required", []))
                fields = []
                for k, v in sch["properties"].items():
                    fields.append({
                        "key": k,
                        "optional": k not in req,
                        "type": self.ts_type(v),
                        "desc": v.get("description", "") if isinstance(v, dict) else "",
                    })
                sig = ("iface", frozenset((f["key"], f["type"], f["optional"]) for f in fields))
                entries[raw] = {
                    "kind": "iface", "fields": fields,
                    "desc": sch.get("description", ""), "sig": sig,
                }
            else:
                ts = self.ts_type(sch)
                entries[raw] = {
                    "kind": "alias", "ts": ts,
                    "desc": sch.get("description", ""),
                    "sig": ("alias", ts),
                }

        # 同结构去重：仅对“字母 + 末尾可选数字”形式的名字按基名分组
        groups = {}
        for raw in entries:
            m = re.match(r"^([A-Za-z]+)(\d*)$", raw)
            base = m.group(1) if m else raw
            groups.setdefault(base, []).append(raw)

        alias_of = {}
        for members in groups.values():
            if len(members) < 2:
                continue
            if len({entries[x]["sig"] for x in members}) != 1:
                continue  # 结构不一致，全部保留
            # 规范名：无末尾数字优先，其次更短，其次字典序
            canon = sorted(members,
                           key=lambda n: (bool(re.search(r"\d$", n)), len(n), n))[0]
            for x in members:
                if x != canon:
                    alias_of[x] = canon

        # 仅被引用到“带编号副本”的 schema（OpenAPI 文档历史遗留）：
        # 若某基名在依赖闭包中只出现唯一一个带编号实例，则去掉编号还原干净命名。
        rename = {}
        for base, members in groups.items():
            if len(members) == 1:
                only = members[0]
                if only != base and re.match(r"^[A-Za-z]+\d+$", only):
                    rename[only] = base

        self.model_entries = entries
        self.model_alias = alias_of
        self.model_rename = rename

    # ------------------------------------------------------------------
    # 渲染
    # ------------------------------------------------------------------
    def _file_header(self, title):
        return "/** " + title + " */"

    @staticmethod
    def _doc_block(desc, indent):
        """生成 JSDoc 注释块（无尾随换行）；desc 为空时返回空串。"""
        desc = (desc or "").strip()
        if not desc:
            return ""
        pad = " " * indent
        lines = [clean_comment(l) for l in desc.split("\n")]
        if len(lines) == 1:
            return pad + "/** " + lines[0] + " */"
        body = "\n".join(pad + " * " + l for l in lines)
        return pad + "/**\n" + body + "\n" + pad + " */"

    def _module_model_names(self, funcs):
        """计算某模块全部接口传递引用到的数据模型名集合（含结构别名的规范名）。"""
        need = set()
        frontier = set()
        for f in funcs:
            frontier |= f["types"]
        while frontier:
            nxt = set()
            for name in frontier:
                if name in need:
                    continue
                need.add(name)
                sch = self.schemas.get(name)
                if isinstance(sch, dict):
                    refs = set()
                    collect_refs(sch, refs)
                    nxt |= refs
                if name in self.model_alias:      # 结构别名 -> 补上其规范定义
                    nxt.add(self.model_alias[name])
            frontier = nxt
        return {n for n in need if n in self.model_entries}

    def _render_module_models(self, names):
        """渲染某模块内联的数据模型声明块（枚举 / 别名 / 接口 / 同结构别名）。"""
        entries, alias = self.model_entries, self.model_alias
        enums = sorted(n for n in names if entries[n]["kind"] == "enum" and n not in alias)
        aliases = sorted(n for n in names if entries[n]["kind"] == "alias" and n not in alias)
        ifaces = sorted(n for n in names if entries[n]["kind"] == "iface" and n not in alias)
        structs = sorted(n for n in names if n in alias)

        blocks = []
        for n in enums:
            blocks.append(self._render_enum(n, entries[n]))
        for n in aliases:
            doc = self._doc_block(entries[n]["desc"], 0)
            line = "export type " + ts_ident(n) + " = " + entries[n]["ts"] + ";"
            blocks.append((doc + "\n" if doc else "") + line)
        for n in ifaces:
            blocks.append(self._render_iface(n, entries[n]))
        for n in structs:
            blocks.append("export type " + ts_ident(n) + " = " + ts_ident(alias[n]) + ";")
        return blocks

    def _render_enum(self, name, e):
        doc = self._doc_block(e["desc"], 0)
        decl = "export type " + ts_ident(name) + " =\n  | " + "\n  | ".join(e["values"]) + ";"
        return (doc + "\n" if doc else "") + decl

    def _render_iface(self, name, e):
        doc = self._doc_block(e["desc"], 0)
        lines = ["export interface " + ts_ident(name) + " {"]
        for f in e["fields"]:
            if f["desc"]:
                lines.append("  /** " + clean_comment(f["desc"]).replace("\n", " ") + " */")
            opt = "?" if f["optional"] else ""
            lines.append("  " + prop_key(f["key"]) + opt + ": " + f["type"] + ";")
        lines.append("}")
        return (doc + "\n" if doc else "") + "\n".join(lines)

    def render_module(self, module, funcs):
        obj_name = to_pascal(module) + "API"

        imports = [
            'import request from "' + REQUEST_IMPORT + '";',
            'import { API_BASE } from "' + CONFIG_IMPORT + '";',
        ]

        # 抽取根路径常量（如 const FILE_BASE_URL = `${API_BASE}/files`）
        base_decls, self.base_map = self._base_consts(funcs)

        # 本模块用到的数据模型直接内联（跨模块共用的模型会在各文件中各存一份）
        model_blocks = self._render_module_models(self._module_model_names(funcs))

        obj_doc = self._doc_block(module + " 模块接口集合", 0)
        body = (obj_doc + "\n" if obj_doc else "") + "export const " + obj_name + " = {\n"
        body += "\n\n".join(self._render_func(f) for f in funcs)
        body += "\n};"

        blocks = [
            self._file_header(to_pascal(module) + " 模块接口"),
            "\n".join(imports),
        ]
        if base_decls:
            blocks.append(base_decls)
        if model_blocks:
            blocks.append("// ==================== 数据模型 ====================")
            blocks.extend(model_blocks)
        blocks.append(body)
        blocks.append("export default " + obj_name + ";")
        return "\n\n".join(blocks) + "\n"

    def _render_func(self, f):
        ind = "  "
        doc = [ind + "/**", ind + " * " + (f["summary"] or f["name"])]
        if f["description"].strip():
            doc.append(ind + " *")
            for line in f["description"].strip().split("\n"):
                doc.append(ind + " * " + clean_comment(line))
        doc.append(ind + " *")
        doc.append(ind + " * `" + f["method"].upper() + " " + f["path"] + "`")

        def param_doc(name, label, desc):
            txt = ind + " * @param " + name + " " + label
            if desc:
                txt += " —— " + clean_comment(desc).replace("\n", " ")
            return txt

        for p in f["path_params"]:
            doc.append(param_doc(p["name"], "路径参数", p["desc"]))
        body = f["body"]
        if body and body["mode"] == "json":
            doc.append(param_doc("data", "请求体", body.get("desc", "")))
        if body and body["mode"] == "form":
            for fld in body["fields"]:
                label = "上传文件" if fld["is_file"] else "表单字段"
                doc.append(param_doc(fld["name"], label, fld["desc"]))
        for q in f["query_params"]:
            doc.append(param_doc(q["name"], "查询参数", q["desc"]))
        if f["ret_desc"]:
            doc.append(ind + " * @returns " + clean_comment(f["ret_desc"]).replace("\n", " "))
        if f["deprecated"]:
            doc.append(ind + " * @deprecated 该接口已标记为废弃")
        doc.append(ind + " */")

        # 参数列表：必填在前、可选在后
        req_args, opt_args = [], []
        for p in f["path_params"]:
            req_args.append(p["name"] + ": " + p["type"])
        if body and body["mode"] == "json":
            (req_args if body["required"] else opt_args).append(
                "data" + ("" if body["required"] else "?") + ": " + body["type"])
        if body and body["mode"] == "form":
            for fld in body["fields"]:
                if fld["required"]:
                    req_args.append(fld["name"] + ": " + fld["type"])
                else:
                    opt_args.append(fld["name"] + "?: " + fld["type"])
        for q in f["query_params"]:
            if q["required"]:
                req_args.append(q["name"] + ": " + q["type"])
            elif q["default"] is not None:
                opt_args.append(q["name"] + ": " + q["type"]
                                + " = " + self._literal(q["default"]))
            else:
                opt_args.append(q["name"] + "?: " + q["type"])
        args = ", ".join(req_args + opt_args)

        # 方法体
        lines = list(doc)
        lines.append(ind + f["name"] + "(" + args + ") {")

        if body and body["mode"] == "form":
            lines.append("    const formData = new FormData();")
            for fld in body["fields"]:
                value = fld["name"] if fld["is_file"] else "String(" + fld["name"] + ")"
                append = 'formData.append("' + fld["raw"] + '", ' + value + ");"
                if fld["required"]:
                    lines.append("    " + append)
                else:
                    lines.append("    if (" + fld["name"] + " !== undefined) {")
                    lines.append("      " + append)
                    lines.append("    }")

        cfg = ["      url: " + self._build_url(f["path"]) + ",",
               '      method: "' + f["method"] + '",']
        if f["query_params"]:
            items = []
            for q in f["query_params"]:
                if q["raw"] == q["name"]:
                    items.append(q["name"])
                else:
                    items.append(json.dumps(q["raw"], ensure_ascii=False) + ": " + q["name"])
            cfg.append("      params: { " + ", ".join(items) + " },")
        if body and body["mode"] == "json":
            cfg.append("      data,")
        if body and body["mode"] == "form":
            cfg.append("      data: formData,")
        if f["is_download"]:
            cfg.append('      responseType: "blob",')

        lines.append("    return request<" + f["ret_type"] + ">({")
        lines.extend(cfg)
        lines.append("    });")
        lines.append(ind + "},")
        return "\n".join(lines)

    def render_index(self):
        blocks = [
            self._file_header("接口层统一出口"),
            "// 数据模型已内联进各模块文件，如需类型请从对应的 ./<module>.api 导入。",
        ]
        exports = []
        for module in sorted(self.modules):
            obj = to_pascal(module) + "API"
            exports.append("export { " + obj + ' } from "./' + module + '.api";')
        blocks.append("\n".join(exports))
        return "\n\n".join(blocks) + "\n"

    # ------------------------------------------------------------------
    # 落盘
    # ------------------------------------------------------------------
    def write_all(self):
        OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
        for old in OUTPUT_DIR.glob("*.ts"):  # 整目录重写，先清理旧产物
            old.unlink()

        files = {}
        for module, funcs in self.modules.items():
            files[module + ".api.ts"] = self.render_module(module, funcs)
        files["index.ts"] = self.render_index()

        for name in list(files):
            files[name] = self._apply_rename(files[name])

        for name, content in files.items():
            (OUTPUT_DIR / name).write_text(content, encoding="utf-8")
        return files

    def _apply_rename(self, text):
        """把仅存在唯一编号副本的类型名还原为无编号的干净命名（整词替换）。"""
        for old in sorted(self.model_rename, key=len, reverse=True):
            text = re.sub(r"\b" + re.escape(old) + r"\b",
                          self.model_rename[old], text)
        return text


# ============================================================================
# merge 模式：把生成结果合并回 src/api 下的手写接口文件
#
# 思路：手写文件结构非常规整（const XxxAPI = { method() { request<T>({...}) } }），
# 用「括号配对扫描 + 正则」即可解析出每个成员函数及其 (method, url)，再与生成接口
# 按归一化路径匹配。匹配上的整体替换，未匹配的追加，函数改名同步全库引用。
# ============================================================================

_BRACKETS = {"{": "}", "(": ")", "[": "]"}


def _skip_string(text, j, quote):
    """j 指向起始引号，返回闭合引号之后的下标。"""
    n = len(text)
    j += 1
    while j < n:
        if text[j] == "\\":
            j += 2
            continue
        if text[j] == quote:
            return j + 1
        j += 1
    return n


def _skip_template(text, j):
    """j 指向起始反引号，返回闭合反引号之后的下标（处理 ${...} 内嵌）。"""
    n = len(text)
    j += 1
    while j < n:
        c = text[j]
        if c == "\\":
            j += 2
            continue
        if c == "`":
            return j + 1
        if c == "$" and j + 1 < n and text[j + 1] == "{":
            j = _find_match(text, j + 1) + 1
            continue
        j += 1
    return n


def _find_match(text, i):
    """text[i] 为 ([{ 之一，返回配对闭合符下标（跳过字符串 / 注释 / 模板字面量）。"""
    n = len(text)
    stack = [_BRACKETS[text[i]]]
    j = i + 1
    while j < n and stack:
        c = text[j]
        if c in "({[":
            stack.append(_BRACKETS[c])
        elif c in ")}]":
            if stack and stack[-1] == c:
                stack.pop()
        elif c in "'\"":
            j = _skip_string(text, j, c)
            continue
        elif c == "`":
            j = _skip_template(text, j)
            continue
        elif c == "/" and j + 1 < n and text[j + 1] == "/":
            nl = text.find("\n", j)
            j = n if nl < 0 else nl
            continue
        elif c == "/" and j + 1 < n and text[j + 1] == "*":
            e = text.find("*/", j + 2)
            j = n if e < 0 else e + 2
            continue
        j += 1
    return j - 1


def _scan_to(text, start, limit, stops):
    """从 start 起在深度 0 扫描，返回首个落在 stops 中的字符下标；跳过嵌套结构。"""
    j = start
    while j < limit:
        c = text[j]
        if c in "({[":
            j = _find_match(text, j) + 1
            continue
        if c in "'\"":
            j = _skip_string(text, j, c)
            continue
        if c == "`":
            j = _skip_template(text, j)
            continue
        if c == "/" and j + 1 < limit and text[j + 1] == "/":
            nl = text.find("\n", j)
            j = limit if nl < 0 else nl
            continue
        if c == "/" and j + 1 < limit and text[j + 1] == "*":
            e = text.find("*/", j + 2)
            j = limit if e < 0 else e + 2
            continue
        if c in stops:
            return j
        j += 1
    return limit


def _norm_path(p):
    """路径归一化：所有 {xxx} / {*xxx} 占位符统一成 {}，去尾部斜杠（用于匹配）。"""
    return re.sub(r"\{[^}]*\}", "{}", p).rstrip("/") or "/"


def _resolve_consts(text):
    """解析文件内 const X = `...`; 声明，返回 {名称: 解析后的值}（递归代入 ${...}）。"""
    raw = {}
    for m in re.finditer(r"\bconst\s+([A-Za-z_$][\w$]*)\s*=\s*`([^`]*)`\s*;", text):
        raw[m.group(1)] = m.group(2)

    def resolve(name, seen):
        if name == "API_BASE":
            return API_PREFIX
        if name in seen or name not in raw:
            return "${" + name + "}"
        return re.sub(r"\$\{([A-Za-z_$][\w$]*)\}",
                      lambda mm: resolve(mm.group(1), seen | {name}), raw[name])

    return {n: resolve(n, set()) for n in raw}


def _resolve_url(expr, consts):
    """把手写接口里的 url 表达式解析为归一化路径。"""
    expr = expr.strip()
    if expr.startswith("`") and expr.endswith("`"):
        body = expr[1:-1]
    elif len(expr) >= 2 and expr[0] in "\"'" and expr[-1] == expr[0]:
        body = expr[1:-1]
    else:
        body = "${" + expr + "}"            # 裸标识符（根路径常量）

    def sub(mm):
        ident = mm.group(1).strip()
        if ident == "API_BASE":
            return API_PREFIX
        if ident in consts:
            return consts[ident]
        return "{}"                          # 其余均视为路径参数

    return _norm_path(re.sub(r"\$\{([^}]+)\}", sub, body))


def _parse_request(body, consts):
    """从一个成员函数体里解析 request 调用，返回 {method, path, has_headers} 或 None。"""
    rm = re.search(r"\brequest\s*(?:<[^;{]*?>)?\s*\(", body)
    if not rm:
        return None
    paren = rm.end() - 1
    pclose = _find_match(body, paren)
    bo = body.find("{", paren + 1)
    if bo < 0 or bo > pclose:
        return None
    obj = body[bo:_find_match(body, bo) + 1]
    mm = re.search(r"\bmethod\s*:\s*[\"']([A-Za-z]+)[\"']", obj)
    um = re.search(r"\burl\s*:", obj)
    if not mm or not um:
        return None
    ucut = _scan_to(obj, um.end(), len(obj), ",}")
    return {
        "method": mm.group(1).lower(),
        "path": _resolve_url(obj[um.end():ucut], consts),
        "has_headers": bool(re.search(r"\bheaders\s*:", obj)),
    }


def parse_handwritten(path):
    """解析一个手写 api 文件，返回结构化信息；无法识别则返回 None。"""
    text = path.read_text(encoding="utf-8")
    consts = _resolve_consts(text)

    om = re.search(r"\bconst\s+([A-Za-z_$][\w$]*)\s*=\s*\{", text)
    if not om:
        return None
    obj_name = om.group(1)
    brace = text.index("{", om.end() - 1)
    obj_close = _find_match(text, brace)

    members = []
    i = brace + 1
    while i < obj_close:
        while i < obj_close and text[i] in " \t\r\n":
            i += 1
        if i >= obj_close:
            break
        doc_start = i
        while i < obj_close:                 # 跳过成员前的注释
            if text[i:i + 2] == "//":
                nl = text.find("\n", i)
                i = obj_close if nl < 0 else nl + 1
            elif text[i:i + 2] == "/*":
                e = text.find("*/", i + 2)
                i = obj_close if e < 0 else e + 2
            else:
                break
            while i < obj_close and text[i] in " \t\r\n":
                i += 1
        nm = re.match(r"[A-Za-z_$][\w$]*", text[i:obj_close])
        if not nm:
            break
        comma = _scan_to(text, i, obj_close, ",")
        members.append({
            "name": nm.group(0),
            "seg_start": text.rfind("\n", 0, doc_start) + 1,
            "seg_end": min(comma + 1, obj_close),
            "body": text[i:comma],
            "match": None,
        })
        i = comma + 1

    for mem in members:
        mem["request"] = _parse_request(mem["body"], consts)

    types = []
    for tm in re.finditer(r"\bexport\s+(interface|type)\s+([A-Za-z_$][\w$]*)", text):
        if tm.group(1) == "interface":
            ob = text.find("{", tm.end())
            if ob < 0:
                continue
            end = _find_match(text, ob) + 1
        else:
            end = min(_scan_to(text, tm.end(), len(text), ";") + 1, len(text))
        ds = tm.start()
        lead = re.search(r"(/\*\*?[\s\S]*?\*/)\s*$", text[:ds])
        if lead:
            ds = lead.start(1)
        types.append({"name": tm.group(2),
                      "start": text.rfind("\n", 0, ds) + 1, "end": end})

    return {
        "path": path, "text": text, "obj_name": obj_name, "obj_close": obj_close,
        "base_map": {p: n for n, p in consts.items() if p.startswith(API_PREFIX)},
        "members": members, "types": types,
    }


class Merger:
    """把生成结果合并回 src/api 下的手写接口文件。"""

    SKIP_FILES = {"config.ts", "index.ts"}

    def __init__(self, gen):
        self.gen = gen
        self.api_dir = OUTPUT_DIR.parent          # src/api
        self.src_dir = OUTPUT_DIR.parents[1]      # src
        self.renames = []                         # [(obj_name, old, new), ...]
        self.touched = set()                      # 改动过的文件
        self.fresh_modules = set()                # 整体新建文件的模块
        self.report = {"replaced": [], "added": [], "new_files": [], "overwritten": [],
                        "type_replaced": [], "type_kept": [], "impact": [], "warn": []}

    def run(self):
        gen = self.gen

        # 1. 生成接口建索引（method, 归一化路径），并给每个接口标注所属模块
        gen_index = {}
        for module, funcs in gen.modules.items():
            for f in funcs:
                f["_module"] = module
                gen_index.setdefault((f["method"], _norm_path(f["path"])), []).append(f)

        # 2. 解析所有手写 api 文件
        files = []
        for p in sorted(self.api_dir.glob("*.ts")):
            if p.name in self.SKIP_FILES:
                continue
            info = parse_handwritten(p)
            if info:
                files.append(info)
            else:
                self.report["warn"].append("无法解析，已跳过：" + p.name)

        # 3. 逐函数匹配
        consumed = set()
        for info in files:
            for mem in info["members"]:
                req = mem["request"]
                if not req:
                    continue
                cands = gen_index.get((req["method"], req["path"]))
                if not cands:
                    continue
                f = next((x for x in cands if id(x) not in consumed), None)
                if f is None:
                    continue
                consumed.add(id(f))
                mem["match"] = f
                if len(cands) > 1:
                    self.report["warn"].append(
                        "%s %s 命中多个生成接口，采用 %s"
                        % (req["method"].upper(), req["path"], f["name"]))
                if req["has_headers"]:
                    self.report["warn"].append(
                        "%s.%s 原函数含自定义 headers，替换后丢失，请人工确认"
                        % (info["obj_name"], mem["name"]))

        # 4. 模块归属：完全没匹配上的模块整体新建文件；若与同名手写文件冲突则整体替换之
        matched_modules = {mem["match"]["_module"]
                           for info in files for mem in info["members"]
                           if mem["match"]}
        overwrite = set()                   # 将被整体替换的手写文件路径
        for module in gen.modules:
            if module in matched_modules:
                continue
            tgt = self.api_dir / (module + ".api.ts")
            if any(i["path"] == tgt for i in files):
                overwrite.add(tgt)

        # 每个匹配上的模块归属到一个「存活的」手写文件（作为就地合并目标）
        home = {}
        for info in files:
            if info["path"] in overwrite:
                continue
            for mem in info["members"]:
                if mem["match"]:
                    home.setdefault(mem["match"]["_module"], info)
        self.fresh_modules = {m for m in gen.modules if m not in home}

        # 5. 就地改写存活的手写文件（替换匹配 + 追加新接口 + 合并类型）
        for info in files:
            if info["path"] in overwrite:
                continue
            homed = [m for m in home if home[m] is info]
            if not homed and not any(mem["match"] for mem in info["members"]):
                continue                    # 无匹配、无新增 —— 原样不动
            news = []
            for m in homed:
                news += [f for f in gen.modules[m] if id(f) not in consumed]
            self._rewrite_file(info, news)

        # 6. fresh 模块整体写文件（与同名手写文件冲突时整体替换）
        for module in sorted(self.fresh_modules):
            victim = next((i for i in files
                           if i["path"] == self.api_dir / (module + ".api.ts")), None)
            self._write_fresh(module, victim)

        # 7. 同步全库引用改名
        self._apply_renames()

        # 8. 格式化 + 报告
        self._format()
        self._print_report()

    def _rewrite_file(self, info, news):
        gen = self.gen
        gen.base_map = dict(info["base_map"])
        text = info["text"]
        edits = []            # [(start, end, 新文本), ...]
        local_renames = []

        # 5a. 替换匹配到的成员
        for mem in info["members"]:
            f = mem["match"]
            if not f:
                continue
            if f["_module"] in self.fresh_modules:
                # 该接口归属的模块将整体落到独立新文件，这里直接删掉旧成员
                edits.append((mem["seg_start"], mem["seg_end"], ""))
                self.report["warn"].append(
                    "%s.%s 已移至 %s.api.ts"
                    % (info["obj_name"], mem["name"], f["_module"]))
                continue
            edits.append((mem["seg_start"], mem["seg_end"],
                          gen._apply_rename(gen._render_func(f))))
            tag = "" if f["name"] == mem["name"] else " → " + f["name"]
            self.report["replaced"].append(
                "%s.%s%s  (%s)" % (info["obj_name"], mem["name"], tag, info["path"].name))
            if f["name"] != mem["name"]:
                local_renames.append((mem["name"], f["name"]))
                self.renames.append((info["obj_name"], mem["name"], f["name"]))

        # 5b. 追加全新接口
        if news:
            rendered = "\n\n".join(gen._apply_rename(gen._render_func(f)) for f in news)
            edits.append((info["obj_close"], info["obj_close"], "\n\n" + rendered + "\n"))
            for f in news:
                self.report["added"].append(
                    "%s.%s  (%s)" % (info["obj_name"], f["name"], info["path"].name))

        # 5c. 合并数据模型：删同名手写类型，文件尾追加生成模型
        file_funcs = [m["match"] for m in info["members"]
                      if m["match"] and m["match"]["_module"] not in self.fresh_modules]
        file_funcs += list(news)
        model_names = gen._module_model_names(file_funcs)
        gen_type_names = {ts_ident(n) for n in model_names}
        for t in info["types"]:
            if t["name"] in gen_type_names:
                edits.append((t["start"], t["end"], ""))
                self.report["type_replaced"].append(t["name"])
            else:
                self.report["type_kept"].append(t["name"])
        model_blocks = gen._render_module_models(model_names)
        if model_blocks:
            block = gen._apply_rename("\n\n".join(model_blocks))
            edits.append((len(text), len(text),
                          "\n\n// ===== 数据模型（由 OpenAPI 生成）=====\n\n"
                          + block + "\n"))

        # 倒序应用编辑（互不重叠）
        for start, end, repl in sorted(edits, key=lambda e: e[0], reverse=True):
            text = text[:start] + repl + text[end:]

        # 文件内部 this.<old> 同步改名（占位符两段式，避免链式串扰）
        for idx, (old, _) in enumerate(local_renames):
            text = re.sub(r"\bthis\." + re.escape(old) + r"\b",
                          "\x00T%d\x00" % idx, text)
        for idx, (_, new) in enumerate(local_renames):
            text = text.replace("\x00T%d\x00" % idx, "this." + new)

        info["path"].write_text(text, encoding="utf-8")
        self.touched.add(info["path"])

    def _write_fresh(self, module, victim):
        """把一个模块整体写成独立文件；victim 非空表示替换掉同名手写文件。"""
        gen = self.gen
        funcs = gen.modules[module]
        content = gen._apply_rename(gen.render_module(module, funcs))
        content = content.replace('from "' + CONFIG_IMPORT + '"', 'from "./config"')
        out = self.api_dir / (module + ".api.ts")
        out.write_text(content, encoding="utf-8")
        self.touched.add(out)
        for f in funcs:
            self.report["added"].append(
                "%sAPI.%s  (%s)" % (to_pascal(module), f["name"], out.name))
        if victim:
            self.report["overwritten"].append(out.name)
            self.report["impact"].append((out.name, self._overwrite_impact(victim)))
        else:
            self.report["new_files"].append(out.name)
        self._register_in_index(module)

    def _overwrite_impact(self, info):
        """整体替换某手写文件后，列出可能引用了其旧导出的其它文件。"""
        obj = info["obj_name"]
        stem = info["path"].stem            # 如 team.api
        callre = re.compile(r"\b" + re.escape(obj) + r"\.")
        hits = []
        for p in sorted(self.src_dir.rglob("*")):
            if not p.is_file() or p.suffix not in (".ts", ".vue"):
                continue
            if "generated" in p.parts or p == info["path"]:
                continue
            txt = p.read_text(encoding="utf-8")
            if (stem + '"') in txt or (stem + "'") in txt or callre.search(txt):
                hits.append(str(p.relative_to(self.src_dir.parent)))
        return hits

    def _register_in_index(self, module):
        idx = self.api_dir / "index.ts"
        if not idx.exists():
            return
        txt = idx.read_text(encoding="utf-8")
        obj = to_pascal(module) + "API"
        if re.search(r"\b" + obj + r"\b", txt):
            return
        txt = txt.rstrip() + "\n" + \
            'export { default as %s } from "./%s.api";\n' % (obj, module)
        idx.write_text(txt, encoding="utf-8")
        self.touched.add(idx)

    def _apply_renames(self):
        """把函数改名同步到整个 src/ 代码库（XxxAPI.old -> XxxAPI.new）。

        采用「占位符两段式」替换：先把所有 旧引用 换成唯一占位符，再把占位符换成
        新引用，避免 a→b、c→a 这类链式改名相互串扰。
        """
        if not self.renames:
            return
        subs = []
        for idx, (obj, old, new) in enumerate(self.renames):
            pat = re.compile(r"\b" + re.escape(obj) + r"\." + re.escape(old) + r"\b")
            subs.append((pat, "\x00R%d\x00" % idx, obj + "." + new))
        for p in sorted(self.src_dir.rglob("*")):
            if not p.is_file() or p.suffix not in (".ts", ".vue"):
                continue
            if "generated" in p.parts:
                continue
            txt = orig = p.read_text(encoding="utf-8")
            for pat, ph, _ in subs:
                txt = pat.sub(ph, txt)
            for _, ph, final in subs:
                txt = txt.replace(ph, final)
            if txt != orig:
                p.write_text(txt, encoding="utf-8")
                self.touched.add(p)

    def _format(self):
        repo = OUTPUT_DIR.parents[2]
        prettier = repo / "node_modules" / ".bin" / "prettier"
        if not prettier.exists() or not self.touched:
            return
        try:
            subprocess.run(
                [str(prettier), "--write", "--log-level", "warn"]
                + [str(p) for p in sorted(self.touched)],
                cwd=str(repo), check=True)
        except:
            self.report["warn"].append("prettier 格式化失败")

    def _print_report(self):
        r = self.report
        print()
        print("✓ merge 模式完成")
        if r["replaced"]:
            print("  替换已有接口 %d 个：" % len(r["replaced"]))
            for s in r["replaced"]:
                print("      " + s)
        print("  新增接口 %d 个" % len(r["added"]))
        if r["new_files"]:
            print("  新建文件：" + "、".join(sorted(set(r["new_files"]))))
        if r["overwritten"]:
            print("  整体替换文件：" + "、".join(sorted(set(r["overwritten"]))))
        if self.renames:
            print("  函数改名 %d 处，已同步全库引用：" % len(self.renames))
            for obj, old, new in self.renames:
                print("      %s.%s → %s.%s" % (obj, old, obj, new))
        print("  数据模型：替换同名 %d 个，保留手写 %d 个"
              % (len(r["type_replaced"]), len(set(r["type_kept"]))))
        print("  共改动文件 %d 个" % len(self.touched))
        for fname, hits in r["impact"]:
            print()
            print("  ⚠ %s 已整体替换，旧接口 / 类型被删除，以下文件可能需手工修复："
                  % fname)
            for h in hits:
                print("      " + h)
        if r["warn"]:
            print()
            print("  ⚠ 其它注意事项：")
            for w in r["warn"]:
                print("      " + w)
        print()
        print("  请执行 `git diff` 审查改动，并用 vue-tsc 校验类型"
              "（参数 / 类型变化导致的不一致需人工处理）。")


# ============================================================================
# 入口
# ============================================================================

def format_output():
    """若项目本地存在 prettier，则格式化生成文件，保持与仓库代码风格一致。"""
    repo = OUTPUT_DIR.parents[2]
    prettier = repo / "node_modules" / ".bin" / "prettier"
    if not prettier.exists():
        print("  (跳过格式化：未找到 node_modules/.bin/prettier)")
        return
    try:
        subprocess.run(
            [str(prettier), "--write", "--log-level", "warn", str(OUTPUT_DIR)],
            cwd=str(repo), check=True,
        )
        print("  已用 prettier 格式化生成文件")
    except :
        print("  (prettier 格式化失败，可手动运行 pnpm lint:prettier；%s)" )


def confirm_merge():
    """merge 模式执行前的终端二次确认；通过 -y/--yes 可跳过。"""
    argv = sys.argv[1:]
    if "-y" in argv or "--yes" in argv:
        return True
    print()
    print("⚠  merge 模式会直接改写工作区文件：")
    print("   · src/api/ 下手写接口里同 URL 路径的函数将被整体替换为生成版本")
    print("     （函数名 / 参数 / 类型均采用生成版）")
    print("   · 函数改名会同步到整个 src/ 代码库（components / views / stores 等）")
    print("   · 个别手写文件可能被整体替换，并新建若干文件")
    print("   建议先提交或暂存当前改动，以便用 git diff 审查、git checkout 回滚。")
    print()
    try:
        ans = input("   确认继续？输入 y 回车继续，其它任意输入取消：").strip().lower()
    except EOFError:
        print("   非交互终端，已取消。如需在自动化场景跳过确认，请加 --yes 参数。")
        return False
    if ans in ("y", "yes"):
        return True
    print("   已取消，未改动任何文件。")
    return False


def main():
    merge = "--merge" in sys.argv[1:]

    if merge and not confirm_merge():
        return

    print("→ 拉取 OpenAPI 文档 ...")
    spec = get_open_api()

    gen = ApiGenerator(spec)
    print("→ 解析接口与数据模型 ...")
    gen.process()
    gen.build_models()

    if merge:
        print("→ merge 模式：合并进 src/api/ 手写接口文件 ...")
        Merger(gen).run()
        return

    print("→ 写出 TypeScript 文件 ...")
    gen.write_all()
    format_output()

    total_ops = sum(len(v) for v in gen.modules.values())
    print()
    print("✓ 生成完成 -> " + str(OUTPUT_DIR))
    print("  模块数 %d   接口数 %d   数据模型 %d（其中同结构别名 %d）"
          % (len(gen.modules), total_ops, len(gen.model_entries), len(gen.model_alias)))
    for module in sorted(gen.modules):
        print("    %-20s %2d 个接口" % (module + ".api.ts", len(gen.modules[module])))


if __name__ == "__main__":
    main()
