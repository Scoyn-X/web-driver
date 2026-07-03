import json
import sys
import argparse
import urllib.request
import os
import re

# ==========================================
# 配置区
# ==========================================
APIFOX_EXPORT_URL = "https://s.apifox.cn/api/v1/projects/7938425/shared-docs/ca65c85a-df6d-4c7f-890d-d235763b9675/export-data"
APIFOX_PAYLOAD = {
    "type": "openapi",
    "id": "ca65c85a-df6d-4c7f-890d-d235763b9675",
    "version": "3.0",
    "moduleId": 7550830,
    "excludeExtension": True,
    "excludeTagsWithFolder": False,
    "projectId": 7938425,
}


class OpenAPIParser:
    def __init__(self, spec_data):
        self.spec = (
            spec_data.get("data", spec_data)
            if isinstance(spec_data, dict)
            else spec_data
        )

    def get_paths(self, released_only=False):
        paths = self.spec.get("paths", {})
        if not released_only:
            return paths

        filtered = {}
        for path, methods in paths.items():
            if not isinstance(methods, dict):
                continue
            kept_methods = {}
            for method, detail in methods.items():
                if self._is_released_operation(detail, methods):
                    kept_methods[method] = detail
            if kept_methods:
                filtered[path] = kept_methods
        return filtered

    def _is_released_operation(self, operation, path_item=None):
        if not isinstance(operation, dict):
            return False
        status = operation.get("x-apifox-status")
        if status is None and isinstance(path_item, dict):
            status = path_item.get("x-apifox-status")
        return status == "released"

    def resolve_ref(self, ref_str):
        if not ref_str or not ref_str.startswith("#/"):
            return {}
        parts = ref_str.lstrip("#/").split("/")
        curr = self.spec
        for p in parts:
            if isinstance(curr, dict) and p in curr:
                curr = curr[p]
            else:
                return {}
        return curr

    def normalize_schema(self, schema, visited=None):
        if visited is None:
            visited = set()
        if not isinstance(schema, dict):
            return schema
        if "$ref" in schema:
            ref_path = schema["$ref"]
            if ref_path in visited:
                return {"$ref": ref_path, "circular": True}
            visited.add(ref_path)
            resolved = self.resolve_ref(ref_path)
            if not resolved:
                return {"$ref": ref_path}
            merged = dict(resolved)
            merged.update({k: v for k, v in schema.items() if k != "$ref"})
            return self.normalize_schema(merged, visited)
        if "allOf" in schema and isinstance(schema["allOf"], list):
            merged = {}
            for part in schema["allOf"]:
                normalized_part = self.normalize_schema(part, visited.copy())
                merged = self._merge_schema_dicts(merged, normalized_part)
            for key, value in schema.items():
                if key == "allOf" or key == "example":
                    continue
                if isinstance(value, dict):
                    merged[key] = self.normalize_schema(value, visited.copy())
                elif isinstance(value, list):
                    merged[key] = [
                        self.normalize_schema(item, visited.copy()) for item in value
                    ]
                else:
                    merged[key] = value
            return merged

        cleaned = {}
        for key, value in schema.items():
            if key == "example":
                continue
            if isinstance(value, dict):
                cleaned[key] = self.normalize_schema(value, visited.copy())
            elif isinstance(value, list):
                cleaned[key] = [
                    self.normalize_schema(item, visited.copy()) for item in value
                ]
            else:
                cleaned[key] = value
        return cleaned

    def _merge_schema_dicts(self, left, right):
        if not isinstance(left, dict):
            return right
        if not isinstance(right, dict):
            return left
        merged = dict(left)
        for key, value in right.items():
            if key == "properties" and isinstance(merged.get(key), dict) and isinstance(value, dict):
                props = dict(merged[key])
                props.update(value)
                merged[key] = props
            elif key == "items" and isinstance(merged.get(key), dict) and isinstance(value, dict):
                merged[key] = self._merge_schema_dicts(merged[key], value)
            elif key == "required" and isinstance(merged.get(key), list) and isinstance(value, list):
                merged[key] = sorted(set(merged[key]) | set(value))
            elif key == "enum" and isinstance(merged.get(key), list) and isinstance(value, list):
                merged[key] = value if merged[key] == value else value
            else:
                merged[key] = value
        return merged

    def extract_body_schema(self, node):
        if not node:
            return {}
        if "$ref" in node:
            node = self.resolve_ref(node["$ref"])
        content = node.get("content", {})
        for mime in [
            "application/json",
            "multipart/form-data",
            "*/*",
            "application/x-www-form-urlencoded",
        ]:
            if mime in content:
                return self.normalize_schema(content[mime].get("schema", {}))
        return {}


class CodeSummaryExtractor:
    METHOD_ANNOTATIONS = {
        "@GetMapping": "get",
        "@PostMapping": "post",
        "@PutMapping": "put",
        "@DeleteMapping": "delete",
        "@PatchMapping": "patch",
    }

    def __init__(self, project_root):
        self.java_root = os.path.join(project_root, "src", "main", "java")

    def extract(self):
        summaries = {}
        if not os.path.isdir(self.java_root):
            return summaries
        for root, _, files in os.walk(self.java_root):
            for name in files:
                if not name.endswith(".java"):
                    continue
                file_path = os.path.join(root, name)
                summaries.update(self._extract_from_file(file_path))
        return summaries

    def _extract_from_file(self, file_path):
        summaries = {}
        base_paths = [""]
        pending = []
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                for line in f:
                    stripped = line.strip()
                    if not stripped:
                        continue
                    if stripped.startswith("@"):
                        pending.append(stripped)
                        continue
                    if "class " in stripped:
                        base_paths = self._extract_request_mapping_paths(pending) or [
                            ""
                        ]
                        pending.clear()
                        continue
                    if (
                        stripped.startswith(("public ", "protected ", "private "))
                        and "(" in stripped
                    ):
                        summary = self._extract_operation_summary(pending)
                        mappings = self._extract_method_mappings(pending)
                        if mappings and summary:
                            for method, path in mappings:
                                for base in base_paths:
                                    full_path = self._join_paths(base, path)
                                    summaries[(method, full_path)] = summary
                        pending.clear()
                        continue
                    if pending:
                        pending.clear()
        except OSError:
            return summaries
        return summaries

    def _extract_operation_summary(self, annotations):
        ann_block = " ".join(annotations)
        match = re.search(r"@Operation\([^)]*summary\s*=\s*\"([^\"]*)\"", ann_block)
        return match.group(1).strip() if match else ""

    def _extract_method_mappings(self, annotations):
        mappings = []
        for ann in annotations:
            for prefix, method in self.METHOD_ANNOTATIONS.items():
                if ann.startswith(prefix):
                    paths = self._extract_paths(ann) or [""]
                    for path in paths:
                        mappings.append((method, path))
                    break
            else:
                if ann.startswith("@RequestMapping"):
                    methods = re.findall(r"RequestMethod\.([A-Z]+)", ann)
                    if not methods:
                        continue
                    paths = self._extract_paths(ann) or [""]
                    for method in methods:
                        for path in paths:
                            mappings.append((method.lower(), path))
        return mappings

    def _extract_request_mapping_paths(self, annotations):
        for ann in annotations:
            if ann.startswith("@RequestMapping"):
                return self._extract_paths(ann)
        return []

    def _extract_paths(self, annotation_text):
        return re.findall(r"\"([^\"]*)\"", annotation_text)

    def _join_paths(self, base, path):
        if not base:
            if not path:
                return ""
            return path if path.startswith("/") else f"/{path}"
        if not path:
            return base
        if base.endswith("/") and path.startswith("/"):
            return base[:-1] + path
        if not base.endswith("/") and not path.startswith("/"):
            return f"{base}/{path}"
        return base + path


class APIDiffEngine:
    def __init__(self, code_spec, doc_spec):
        self.code_parser = OpenAPIParser(code_spec)
        self.doc_parser = OpenAPIParser(doc_spec)
        project_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        self.code_summaries = CodeSummaryExtractor(project_root).extract()
        self.errors = {}
        self.warnings = {}
        self.passed = []

    def _add_error(self, api_id, msg):
        if api_id not in self.errors:
            self.errors[api_id] = []
        self.errors[api_id].append(msg)

    def _add_warning(self, api_id, msg):
        if api_id not in self.warnings:
            self.warnings[api_id] = []
        self.warnings[api_id].append(msg)

    def run_diff(self, released_only=True):
        c_paths = self.code_parser.get_paths()
        a_paths = self.doc_parser.get_paths(released_only=released_only)
        for path, a_methods in a_paths.items():
            for method, a_details in a_methods.items():
                api_id = f"[{method.upper()}] {path}"
                if path not in c_paths or method not in c_paths.get(path, {}):
                    # 文档中存在但代码未实现的接口，记录为警告（不参与结构对比）
                    self._add_warning(api_id, " [ 未实现警告 ] -> 文档中存在但代码未实现")
                    continue
                c_details = c_paths[path][method]

                c_oid = c_details.get("operationId", "").strip()
                a_oid = a_details.get("operationId", "").strip()
                if c_oid != a_oid:
                    self._add_error(
                        api_id,
                        f" [ operationId差异 ] -> 文档: {a_oid} | 代码: {c_oid}",
                    )

                # 1. Summary compare (from code annotations)
                a_sum = a_details.get("summary", "").strip()
                c_sum = self.code_summaries.get((method.lower(), path), "").strip()
                if not c_sum:
                    c_sum = c_details.get("summary", "").strip()
                if (c_sum or a_sum) and c_sum != a_sum:
                    self._add_error(
                        api_id, f" [ summary差异 ] -> 文档: {a_sum} | 代码: {c_sum}"
                    )
                # 2. 参数对比
                self._compare_params(api_id, c_details, a_details)
                # 3. Request Body 对比
                c_body = self.code_parser.extract_body_schema(
                    c_details.get("requestBody", {})
                )
                a_body = self.doc_parser.extract_body_schema(
                    a_details.get("requestBody", {})
                )
                self._diff_schemas(api_id, c_body, a_body, "RequestBody")
                # 4. 响应体对比
                for status, c_node in c_details.get("responses", {}).items():
                    a_node = a_details.get("responses", {}).get(status)
                    if not a_node:
                        self._add_error(
                            api_id, f" [ 响应缺失 ] -> 文档中未定义状态码 {status}"
                        )
                        continue
                    c_resp = self.code_parser.extract_body_schema(c_node)
                    a_resp = self.doc_parser.extract_body_schema(a_node)
                    self._diff_schemas(api_id, c_resp, a_resp, f"Response({status})")
                if api_id not in self.errors:
                    self.passed.append(api_id)

    def _compare_params(self, api_id, c_details, a_details):
        c_ps = {
            p.get("name"): p for p in c_details.get("parameters", []) if "name" in p
        }
        a_ps = {
            p.get("name"): p for p in a_details.get("parameters", []) if "name" in p
        }
        # 双向检查字段存在性
        for n in set(c_ps.keys()) - set(a_ps.keys()):
            self._add_error(api_id, f" [ 参数差异 ] -> 字段 '{n}' 仅在代码端定义")
        for n in set(a_ps.keys()) - set(c_ps.keys()):
            self._add_error(api_id, f" [ 参数差异 ] -> 字段 '{n}' 仅在文档端定义")
        # 类型比对
        for n in set(c_ps.keys()) & set(a_ps.keys()):
            c_t = (
                c_ps[n]
                .get("schema", {})
                .get("type", "any")
                .replace("integer", "numeric")
                .replace("number", "numeric")
            )
            a_t = (
                a_ps[n]
                .get("schema", {})
                .get("type", "any")
                .replace("integer", "numeric")
                .replace("number", "numeric")
            )
            if c_t != "any" and a_t != "any" and c_t != a_t:
                self._add_error(
                    api_id, f" [ 类型差异 ] -> 参数 '{n}' | 文档: {a_t} | 代码: {c_t}"
                )

    def _diff_schemas(self, api_id, c, a, path_prefix):
        if path_prefix.startswith("Response("):
            c = self._strip_common_response_envelope(c)
            a = self._strip_common_response_envelope(a)
        c_norm = self.code_parser.normalize_schema(c)
        a_norm = self.doc_parser.normalize_schema(a)
        if c_norm == a_norm:
            return
        self._compare_schema_nodes(api_id, c_norm, a_norm, path_prefix)

    def _strip_common_response_envelope(self, schema):
        if not isinstance(schema, dict):
            return schema

        if self._looks_like_common_response_envelope(schema):
            properties = schema.get("properties", {})
            if isinstance(properties, dict) and "data" in properties:
                return self._unwrap_data_schema(properties["data"])

        all_of = schema.get("allOf")
        if not isinstance(all_of, list) or len(all_of) < 2:
            return schema
        if not self._looks_like_common_response_envelope(all_of[0]):
            return schema
        payload_parts = [part for part in all_of[1:] if part is not None]
        if len(payload_parts) == 1:
            payload = payload_parts[0]
            if isinstance(payload, dict):
                properties = payload.get("properties", {})
                if isinstance(properties, dict) and "data" in properties:
                    return self._unwrap_data_schema(properties["data"])
            return self._unwrap_data_schema(payload)
        if payload_parts:
            return {"allOf": payload_parts}
        return schema

    def _unwrap_data_schema(self, schema):
        if not isinstance(schema, dict):
            return schema
        properties = schema.get("properties")
        if isinstance(properties, dict) and "data" in properties:
            return properties["data"]
        return schema

    def _looks_like_common_response_envelope(self, schema):
        if not isinstance(schema, dict):
            return False
        properties = schema.get("properties")
        if not isinstance(properties, dict):
            return False
        common_keys = {"code", "data", "msg", "requestId", "traceId"}
        return common_keys.issuperset(properties.keys()) and {"code", "msg"}.issubset(properties.keys())

    def _compare_schema_nodes(self, api_id, c_node, a_node, current_path):
        if c_node == a_node:
            return

        if isinstance(c_node, dict) and isinstance(a_node, dict):
            c_type = c_node.get("type", "any")
            a_type = a_node.get("type", "any")
            if c_type != a_type:
                self._add_error(
                    api_id,
                    f" [ schema差异 ] -> {current_path} 的 type 不一致 | 文档: {a_type} | 代码: {c_type}",
                )

            c_keys = set(c_node.keys())
            a_keys = set(a_node.keys())
            if current_path.startswith("Response("):
                c_keys.discard("description")
                a_keys.discard("description")
            for key in sorted(c_keys - a_keys):
                self._add_error(
                    api_id,
                    f" [ schema差异 ] -> {current_path} 缺少文档字段 '{key}' | 代码端定义: {self._schema_preview(c_node[key])}",
                )
            for key in sorted(a_keys - c_keys):
                self._add_error(
                    api_id,
                    f" [ schema差异 ] -> {current_path} 多出文档字段 '{key}' | 文档端定义: {self._schema_preview(a_node[key])}",
                )

            for key in sorted(c_keys & a_keys):
                self._compare_schema_nodes(
                    api_id,
                    c_node[key],
                    a_node[key],
                    f"{current_path} -> {key}",
                )
            return

        if isinstance(c_node, list) and isinstance(a_node, list):
            if self._is_set_like_list(current_path):
                c_list = sorted(c_node, key=lambda x: json.dumps(x, ensure_ascii=False, sort_keys=True))
                a_list = sorted(a_node, key=lambda x: json.dumps(x, ensure_ascii=False, sort_keys=True))
            else:
                c_list = c_node
                a_list = a_node

            if len(c_list) != len(a_list):
                self._add_error(
                    api_id,
                    f" [ schema差异 ] -> {current_path} 的数组长度不一致 | 文档: {len(a_list)} | 代码: {len(c_list)}",
                )

            for idx, (c_item, a_item) in enumerate(zip(c_list, a_list)):
                self._compare_schema_nodes(
                    api_id,
                    c_item,
                    a_item,
                    f"{current_path}[{idx}]",
                )
            if len(c_list) > len(a_list):
                for idx in range(len(a_list), len(c_list)):
                    self._add_error(
                        api_id,
                        f" [ schema差异 ] -> {current_path}[{idx}] 仅在代码端定义 | 值: {self._schema_preview(c_list[idx])}",
                    )
            elif len(a_list) > len(c_list):
                for idx in range(len(c_list), len(a_list)):
                    self._add_error(
                        api_id,
                        f" [ schema差异 ] -> {current_path}[{idx}] 仅在文档端定义 | 值: {self._schema_preview(a_list[idx])}",
                    )
            return

        if c_node != a_node:
            self._add_error(
                api_id,
                f" [ schema差异 ] -> {current_path} 值不一致 | 文档: {self._schema_preview(a_node)} | 代码: {self._schema_preview(c_node)}",
            )

    def _is_set_like_list(self, current_path):
        return current_path.endswith("required") or current_path.endswith("enum")

    def _schema_preview(self, value):
        if isinstance(value, (dict, list)):
            try:
                return json.dumps(value, ensure_ascii=False, sort_keys=True)
            except TypeError:
                return str(value)
        return repr(value)


def main(code_path):
    try:
        with open(code_path, "r", encoding="utf-8") as f:
            code_spec = json.load(f)
    except Exception as e:
        print(f"🚨 本地文件错误: {e}")
        sys.exit(1)

    req = urllib.request.Request(
        APIFOX_EXPORT_URL,
        data=json.dumps(APIFOX_PAYLOAD).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            doc_spec = json.loads(r.read().decode())
    except Exception as e:
        print(f"🚨 远端文档获取失败: {e}")
        sys.exit(1)

    engine = APIDiffEngine(code_spec, doc_spec)
    engine.run_diff(released_only=not args.all)

    if engine.passed:
        print(f"🟢 结构完全对齐的接口 ({len(engine.passed)} 个):")
        for api in sorted(engine.passed):
            print(f"   {api}")
        print("")

    if engine.warnings:
        print(f"⚠️ 文档存在但代码未实现的接口 ({len(engine.warnings)} 个):")
        for api in sorted(engine.warnings.keys()):
            print(f" ⚠ {api}")
            for w in engine.warnings[api]:
                print(f"    {w}")
            print("")
        print("")

    if engine.errors:
        print(f"❌ 存在定义差异的接口 ({len(engine.errors)} 个):")
        for api in sorted(engine.errors.keys()):
            print(f" 🚩 {api}")
            for e in engine.errors[api]:
                print(f"    {e}")
            print("")
        print("═" * 80 + "\n🚨 结论：同步异常。请根据上述差异点更新文档或修正代码。")
        sys.exit(1)
    else:
        print("═" * 80 + "\n🎉 结论：同步完成。代码契约与文档定义完全一致。")
        sys.exit(0)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--code", default="target/openapi.code.json")
    parser.add_argument(
        "--all",
        action="store_true",
        help="对远端所有接口进行全量检查，不仅限于 x-apifox-status=released",
    )
    args = parser.parse_args()
    main(args.code)
