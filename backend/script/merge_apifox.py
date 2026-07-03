"""
将代码生成的 openapi.json 合并到 Apifox 规范中：
- 代码中已有的接口 → 用代码版本覆盖（以实际代码为准）
- 仅在 Apifox 中存在的接口 → 保留（Lab 3 新功能，尚未实现）

用法:
  python script/merge_apifox.py [--code target/openapi.json] [--output merged_spec.json]

输出 merged_spec.json 后，将其导入 Apifox 即可。
"""
import json
import sys
import os
import urllib.request

# Apifox 导出 URL（与 api_diff.py 一致）
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

CODE_FILE = "target/openapi.json"
OUTPUT_FILE = "merged_spec.json"

# 解析命令行参数
args = sys.argv[1:]
i = 0
while i < len(args):
    if args[i] == "--code" and i + 1 < len(args):
        CODE_FILE = args[i + 1]; i += 2
    elif args[i] == "--output" and i + 1 < len(args):
        OUTPUT_FILE = args[i + 1]; i += 2
    else:
        i += 1

# 1. 读取代码生成的规范
if not os.path.exists(CODE_FILE):
    print(f"Code spec not found: {CODE_FILE}")
    sys.exit(1)

with open(CODE_FILE, "r", encoding="utf-8") as f:
    code_spec = json.load(f)

code_paths = code_spec.get("paths", {})

# 2. 获取 Apifox 实时规范
print("Fetching Apifox spec...")
req = urllib.request.Request(
    APIFOX_EXPORT_URL,
    data=json.dumps(APIFOX_PAYLOAD).encode(),
    headers={"Content-Type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=30) as r:
        apifox_spec = json.loads(r.read().decode())
except Exception as e:
    print(f"Failed to fetch Apifox spec: {e}")
    sys.exit(1)

# Apifox 返回格式可能是 {"data": {...}} 或直接是 OpenAPI 对象
if isinstance(apifox_spec, dict) and "data" in apifox_spec:
    apifox_spec = apifox_spec["data"]

apifox_paths = apifox_spec.get("paths", {})

# 3. 收集统计信息
code_endpoints = set()
for path, methods in code_paths.items():
    for method in methods:
        if method.lower() in ("get", "post", "put", "delete", "patch"):
            code_endpoints.add((method.lower(), path))

apifox_endpoints = set()
for path, methods in apifox_paths.items():
    for method in methods:
        if method.lower() in ("get", "post", "put", "delete", "patch"):
            apifox_endpoints.add((method.lower(), path))

# 4. 合并：代码中有的接口 → 覆盖或新增；仅在 Apifox 的 → 保留
overwritten = []
kept = []
added_to_apifox = []

for method, path in code_endpoints:
    if path in apifox_paths and method in apifox_paths[path]:
        apifox_paths[path][method] = code_paths[path][method]
        overwritten.append(f"[{method.upper()}] {path}")
    else:
        # 代码有但 Apifox 没有 → 直接加到 Apifox 规范中
        apifox_paths.setdefault(path, {})[method] = code_paths[path][method]
        added_to_apifox.append(f"[{method.upper()}] {path}")

for method, path in apifox_endpoints - code_endpoints:
    kept.append(f"[{method.upper()}] {path}")

# 5. 同步 schemas——代码有的 schema 覆盖，Apifox 独有的保留
code_schemas = code_spec.get("components", {}).get("schemas", {})
apifox_schemas = apifox_spec.get("components", {}).get("schemas", {})
for name, schema in code_schemas.items():
    apifox_schemas[name] = schema

# 6. 写输出
with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
    json.dump(apifox_spec, f, ensure_ascii=False, indent=2)

# 7. 报告
print(f"\n=== 合并结果 ===")
print(f"覆盖（代码为准）: {len(overwritten)} 个")
for ep in sorted(overwritten):
    print(f"  ✓ {ep}")
print(f"保留（仅Apifox，Lab3新接口）: {len(kept)} 个")
for ep in sorted(kept):
    print(f"  → {ep}")
if added_to_apifox:
    print(f"补充到Apifox（代码有但Apifox缺失）: {len(added_to_apifox)} 个")
    for ep in sorted(added_to_apifox):
        print(f"  + {ep}")

print(f"\n输出文件: {OUTPUT_FILE}")
print("请将此文件导入 Apifox 以更新接口规范。")
