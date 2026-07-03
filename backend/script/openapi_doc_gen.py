# -*- coding: utf-8 -*-
"""
OpenAPI 文档生成器
==================

扫描整个 Spring Boot 项目的 Java 源码（src/main/java），静态解析其中的
`@RestController` 控制器与 Swagger / Spring Web 注解，生成符合
OpenAPI 3.0.3 规范的接口文档，输出到 ``target/openapi.code.json``。

特性：
  * 纯 Python 标准库实现，无需安装第三方依赖、无需启动后端、无需编译；
  * 识别 @RestController / @RequestMapping / @Tag；
  * 识别 @GetMapping / @PostMapping / @PutMapping / @DeleteMapping /
    @PatchMapping / @RequestMapping；
  * 识别 @PathVariable / @RequestParam / @RequestBody / @RequestPart /
    @RequestHeader 以及 MultipartFile 文件上传；
  * 自动展开统一响应体 Result<T>，并把请求 / 响应中引用到的 VO、Entity、
    枚举递归地生成为 components.schemas；
  * 解析 @Schema(description/example/allowableValues) 与字段 Javadoc 注释、
    @NotNull / @NotBlank / @NotEmpty 等校验注解推断必填字段。

用法：
    python3 script/openapi_doc_gen.py [项目根目录]

不传参数时，默认使用当前工作目录（需包含 pom.xml）。
"""

import json
import os
import re
import sys

# ---------------------------------------------------------------------------
# 一、低层文本工具：注释剥离、字符串掩码、括号匹配、顶层切分
# ---------------------------------------------------------------------------


def strip_comments(src):
    """剥离 Java 源码中的注释。

    * 行注释 ``// ...`` 与普通块注释 ``/* ... */`` 直接删除；
    * Javadoc 块注释 ``/** ... */`` 予以保留，便于后续提取字段说明；
    * 字符串 / 字符字面量内部的 ``//`` ``/*`` 不会被误判（例如 URL 中的 ``//``）。
    """
    out = []
    i, n = 0, len(src)
    in_str = None
    while i < n:
        c = src[i]
        if in_str:
            out.append(c)
            if c == '\\' and i + 1 < n:
                out.append(src[i + 1])
                i += 2
                continue
            if c == in_str:
                in_str = None
            i += 1
            continue
        if c in '"\'':
            in_str = c
            out.append(c)
            i += 1
            continue
        if c == '/' and i + 1 < n and src[i + 1] == '/':
            while i < n and src[i] != '\n':
                i += 1
            continue
        if c == '/' and i + 1 < n and src[i + 1] == '*':
            is_doc = (i + 2 < n and src[i + 2] == '*') and not (i + 3 < n and src[i + 3] == '/')
            end = src.find('*/', i + 2)
            end = (end + 2) if end != -1 else n
            if is_doc:
                out.append(src[i:end])
            else:
                # 用等量换行占位，保持行号大致不变
                out.append('\n' * src[i:end].count('\n'))
            i = end
            continue
        out.append(c)
        i += 1
    return ''.join(out)


def mask_strings(s):
    """返回与 ``s`` 等长的字符串，把所有字符串 / 字符字面量的“内容”替换为 ``x``。

    引号本身与换行保留。结构性扫描（找关键字、切分括号）都在掩码文本上进行，
    需要真实文本内容时再用相同下标回原文本切片，从而彻底避免字符串内的
    逗号、括号、关键字造成误判。
    """
    out = list(s)
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if c in '"\'':
            q = c
            i += 1
            while i < n:
                if s[i] == '\\':
                    out[i] = 'x'
                    if i + 1 < n:
                        out[i + 1] = 'x'
                    i += 2
                    continue
                if s[i] == q:
                    i += 1
                    break
                if s[i] != '\n':
                    out[i] = 'x'
                i += 1
        else:
            i += 1
    return ''.join(out)


_OPEN = {'(': ')', '[': ']', '{': '}', '<': '>'}


def find_matching(m, pos):
    """在掩码文本 ``m`` 中，返回 ``m[pos]`` 这个开括号对应的闭括号下标。"""
    open_c = m[pos]
    close_c = _OPEN[open_c]
    depth = 0
    for i in range(pos, len(m)):
        c = m[i]
        if c == open_c:
            depth += 1
        elif c == close_c:
            depth -= 1
            if depth == 0:
                return i
    return -1


def split_top_level(m):
    """在掩码文本 ``m`` 中按顶层逗号切分，返回 [(start, end), ...] 下标区间。"""
    spans = []
    depth = 0
    start = 0
    for i, c in enumerate(m):
        if c in '([{<':
            depth += 1
        elif c in ')]}>':
            depth = max(0, depth - 1)
        elif c == ',' and depth == 0:
            spans.append((start, i))
            start = i + 1
    spans.append((start, len(m)))
    return spans


def find_top_eq(m):
    """在掩码文本 ``m`` 中找到顶层的 ``=`` 下标（注解 key=value 用），找不到返回 -1。"""
    depth = 0
    for i, c in enumerate(m):
        if c in '([{<':
            depth += 1
        elif c in ')]}>':
            depth = max(0, depth - 1)
        elif c == '=' and depth == 0:
            prev = m[i - 1] if i > 0 else ''
            nxt = m[i + 1] if i + 1 < len(m) else ''
            if prev not in '=!<>' and nxt != '=':
                return i
    return -1


# ---------------------------------------------------------------------------
# 二、注解与类型解析
# ---------------------------------------------------------------------------

_ANNO_HEAD = re.compile(r'@\s*([A-Za-z_][\w.]*)')


def _read_anno_at(m, o, i):
    """从下标 ``i`` 处尝试读取一个注解，返回 (anno|None, next_index)。

    ``m`` 为掩码文本，``o`` 为原始文本。anno = {'name', 'raw'}，raw 为括号内原文。
    """
    head = _ANNO_HEAD.match(m, i)
    if not head:
        return None, i
    name = head.group(1).split('.')[-1]
    j = head.end()
    while j < len(m) and m[j] in ' \t\r\n':
        j += 1
    raw = ''
    if j < len(m) and m[j] == '(':
        close = find_matching(m, j)
        if close == -1:
            return {'name': name, 'raw': ''}, len(m)
        raw = o[j + 1:close]
        j = close + 1
    return {'name': name, 'raw': raw}, j


def consume_leading_annotations(m, o):
    """消费一段文本开头的连续注解（跳过空白与残留 Javadoc）。

    返回 (annotations, rest_index)，rest_index 指向注解之后的第一个有效字符。
    """
    annos = []
    i, n = 0, len(m)
    while i < n:
        while i < n and m[i] in ' \t\r\n':
            i += 1
        if m[i:i + 2] == '/*':
            end = m.find('*/', i + 2)
            i = (end + 2) if end != -1 else n
            continue
        if i < n and m[i] == '@':
            anno, j = _read_anno_at(m, o, i)
            if anno is None:
                break
            annos.append(anno)
            i = j
        else:
            break
    return annos, i


def find_annotations(m, o):
    """扫描整段文本中的全部注解（用于类声明前的注解块）。"""
    annos = []
    for head in _ANNO_HEAD.finditer(m):
        anno, _ = _read_anno_at(m, o, head.start())
        if anno is not None:
            annos.append(anno)
    return annos


def get_anno(annos, name):
    """从注解列表里取出指定名字的注解（取第一个），不存在返回 None。"""
    for a in annos:
        if a['name'] == name:
            return a
    return None


def _unescape(c):
    return {'n': '\n', 't': '\t', 'r': '\r', 'b': '\b', 'f': '\f',
            '0': '\0', '"': '"', "'": "'", '\\': '\\'}.get(c, c)


def extract_strings(v):
    """把 ``v`` 中出现的全部字符串字面量内容拼接返回（支持 "a" + "b" 拼接）。"""
    parts = []
    i, n = 0, len(v)
    while i < n:
        if v[i] in '"\'':
            q = v[i]
            i += 1
            buf = []
            while i < n and v[i] != q:
                if v[i] == '\\' and i + 1 < n:
                    if v[i + 1] == 'u' and i + 6 <= n:
                        try:
                            buf.append(chr(int(v[i + 2:i + 6], 16)))
                            i += 6
                            continue
                        except ValueError:
                            pass
                    buf.append(_unescape(v[i + 1]))
                    i += 2
                    continue
                buf.append(v[i])
                i += 1
            i += 1
            parts.append(''.join(buf))
        else:
            i += 1
    return ''.join(parts)


def parse_value(v):
    """把注解的一个取值文本解析成 Python 值（字符串 / 布尔 / 数字 / 列表 / 标识符）。"""
    v = v.strip()
    if not v:
        return None
    if v[0] == '{':
        inner = v[1:v.rfind('}')]
        im = mask_strings(inner)
        return [parse_value(inner[s:e]) for (s, e) in split_top_level(im) if inner[s:e].strip()]
    if v[0] in '"\'':
        return extract_strings(v)
    if v == 'true':
        return True
    if v == 'false':
        return False
    if re.fullmatch(r'-?\d+', v):
        return int(v)
    if re.fullmatch(r'-?\d*\.\d+[fFdD]?', v):
        return float(re.sub(r'[fFdD]$', '', v))
    return v  # 标识符 / 枚举引用，原样返回


def parse_anno_args(raw):
    """解析注解括号内的参数，返回 (kwargs: dict, positional: list)。"""
    raw = raw.strip()
    kwargs, positional = {}, []
    if not raw:
        return kwargs, positional
    rm = mask_strings(raw)
    for (s, e) in split_top_level(rm):
        part = raw[s:e]
        if not part.strip():
            continue
        eq = find_top_eq(rm[s:e])
        if eq != -1:
            kwargs[part[:eq].strip()] = parse_value(part[eq + 1:].strip())
        else:
            positional.append(parse_value(part.strip()))
    return kwargs, positional


def anno_str(annos, name, *keys):
    """便捷取值：取注解 ``name`` 的 kwargs[key]（按 keys 顺序），否则取首个位置参数。"""
    a = get_anno(annos, name)
    if a is None:
        return None
    kw, pos = parse_anno_args(a['raw'])
    for k in keys:
        if k in kw and kw[k] not in (None, ''):
            return kw[k]
    if pos:
        return pos[0]
    return None


def read_type(m, i):
    """从掩码文本 ``m`` 的下标 ``i`` 处读取一个 Java 类型，返回 (type_str, next_index)。

    支持带包名的限定类型、泛型 ``<...>`` 与数组 ``[]``。
    """
    n = len(m)
    while i < n and m[i] in ' \t\r\n':
        i += 1
    head = re.match(r'[A-Za-z_][\w.]*', m[i:])
    if not head:
        return None, i
    t = head.group(0)
    i += head.end()
    j = i
    while j < n and m[j] in ' \t\r\n':
        j += 1
    if j < n and m[j] == '<':
        close = find_matching(m, j)
        if close != -1:
            t += m[j:close + 1]
            i = close + 1
    while True:
        j = i
        while j < n and m[j] in ' \t\r\n':
            j += 1
        if m[j:j + 2] == '[]':
            t += '[]'
            i = j + 2
        else:
            break
    return t, i


def split_generic(t):
    """拆解类型字符串，返回 (simple_base, [generic_args], array_dimension)。"""
    t = re.sub(r'\s+', '', t or '')
    arr = 0
    while t.endswith('[]'):
        arr += 1
        t = t[:-2]
    lt = t.find('<')
    if lt == -1:
        return t.split('.')[-1], [], arr
    base = t[:lt].split('.')[-1]
    inner = t[lt + 1:t.rfind('>')]
    args = [inner[s:e] for (s, e) in split_top_level(inner) if inner[s:e].strip()]
    return base, args, arr


def extract_type_decl(m, o):
    """定位文件中第一个类型声明，返回声明信息字典；失败返回 None。"""
    decl = re.search(r'\b(class|interface|enum|record)\s+([A-Za-z_]\w*)', m)
    if not decl:
        return None
    brace = m.find('{', decl.end())
    if brace == -1:
        return None
    close = find_matching(m, brace)
    if close == -1:
        close = len(m) - 1
    return {
        'kind': decl.group(1),
        'name': decl.group(2),
        'pretext_m': m[:decl.start()], 'pretext_o': o[:decl.start()],
        'header_m': m[decl.end():brace], 'header_o': o[decl.end():brace],
        'body_m': m[brace + 1:close], 'body_o': o[brace + 1:close],
    }


def split_members(body_m):
    """把类体掩码文本按顶层成员（字段以 ';' 结束、方法/内部类以 '}' 结束）切分。

    注意：注解数组字面量（如 ``allowableValues = {"a", "b"}``）也用 ``{}``，
    但它出现在注解括号内部（paren > 0），不能当成成员级代码块的边界。
    """
    spans = []
    start = 0
    paren = 0               # () [] <> 的嵌套深度
    brace = 0               # {} 的嵌套深度
    member_block = False    # 当前最外层 {} 块是否为成员级代码块（方法体 / 内部类体）
    for i, c in enumerate(body_m):
        if c in '([<':
            paren += 1
        elif c in ')]>':
            paren = max(0, paren - 1)
        elif c == '{':
            if brace == 0:
                member_block = (paren == 0)
            brace += 1
        elif c == '}':
            brace = max(0, brace - 1)
            if brace == 0 and member_block:
                spans.append((start, i + 1))
                start = i + 1
        elif c == ';' and paren == 0 and brace == 0:
            spans.append((start, i + 1))
            start = i + 1
    if body_m[start:].strip():
        spans.append((start, len(body_m)))
    return spans


def clean_javadoc(text):
    """从一段文本中提取最后一个 Javadoc 块并清洗为单行说明。"""
    blocks = re.findall(r'/\*\*(.*?)\*/', text, re.S)
    if not blocks:
        return None
    raw = blocks[-1]
    lines = []
    for ln in raw.splitlines():
        ln = ln.strip().lstrip('*').strip()
        if ln.startswith('@'):  # 遇到 @author / @since 等标签即停止
            break
        if ln:
            lines.append(ln)
    desc = ' '.join(lines).strip()
    return desc or None


# ---------------------------------------------------------------------------
# 三、类型 -> OpenAPI Schema
# ---------------------------------------------------------------------------

_INT64 = {'long', 'Long', 'BigInteger'}
_INT32 = {'int', 'Integer', 'short', 'Short', 'byte', 'Byte'}
_NUMBER = {'double', 'Double', 'float', 'Float', 'BigDecimal'}
_STRING = {'String', 'CharSequence', 'char', 'Character', 'UUID'}
_BOOL = {'boolean', 'Boolean'}
_DATE = {'LocalDateTime', 'LocalDate', 'Date', 'LocalTime', 'Instant',
         'ZonedDateTime', 'OffsetDateTime', 'Timestamp'}
_COLLECTION = {'List', 'Set', 'Collection', 'Iterable', 'ArrayList',
               'LinkedList', 'HashSet', 'LinkedHashSet', 'TreeSet', 'SortedSet'}
_MAP = {'Map', 'HashMap', 'LinkedHashMap', 'TreeMap', 'ConcurrentHashMap', 'SortedMap'}
_GENERIC_PARAMS = {'T', 'E', 'K', 'V', 'R', 'U', 'S', 'O', 'N'}


class SchemaBuilder:
    """把 Java 类型转换为 OpenAPI Schema，并按需把引用到的类登记进 components。"""

    def __init__(self, registry):
        self.registry = registry            # simple name -> {'kind','src','masked'}
        self.schemas = {}                   # 已生成的 components.schemas
        self._processing = set()            # 正在解析中的类（防止自引用死循环）

    # -- 公开入口 ---------------------------------------------------------
    def resolve(self, java_type):
        """把任意 Java 类型字符串解析为一个 OpenAPI Schema 字典。"""
        if not java_type:
            return {}
        base, args, arr = split_generic(java_type)

        if arr > 0:
            inner = java_type.strip()
            inner = inner[:inner.rfind('[]')]
            return {'type': 'array', 'items': self.resolve(inner)}

        if base in ('?', 'Object', 'Serializable') or base in _GENERIC_PARAMS:
            return {}
        if base.startswith('?'):
            mm = re.search(r'(?:extends|super)\s*(.+)', base)
            return self.resolve(mm.group(1)) if mm else {}

        if base in _COLLECTION:
            return {'type': 'array', 'items': self.resolve(args[0]) if args else {}}
        if base in _MAP:
            val = self.resolve(args[1]) if len(args) > 1 else {}
            return {'type': 'object', 'additionalProperties': val}
        if base == 'Optional':
            return self.resolve(args[0]) if args else {}
        if base in ('ResponseEntity', 'HttpEntity', 'Mono', 'Flux', 'Callable', 'Future'):
            return self.resolve(args[0]) if args else {}
        if base in ('MultipartFile', 'File', 'Resource'):
            return {'type': 'string', 'format': 'binary'}

        if base in _INT64:
            return {'type': 'integer', 'format': 'int64'}
        if base in _INT32:
            return {'type': 'integer', 'format': 'int32'}
        if base in _NUMBER:
            return {'type': 'number'}
        if base in _STRING:
            return {'type': 'string'}
        if base in _BOOL:
            return {'type': 'boolean'}
        if base in _DATE:
            return {'type': 'string', 'format': 'date-time'}

        if base == 'Result':
            self.ensure_result()
            return {'$ref': '#/components/schemas/Result'}

        if base in self.registry:
            self.register_class(base)
            return {'$ref': '#/components/schemas/' + base}

        # 未知的外部类型，退化为对象
        return {'type': 'object'}

    def ensure_result(self):
        """登记统一响应体 Result 的通用 Schema。"""
        if 'Result' in self.schemas:
            return
        self.schemas['Result'] = {
            'type': 'object',
            'description': '统一响应结构体',
            'properties': {
                'code': {'type': 'string', 'description': '业务状态码，00000 表示成功'},
                'data': {'description': '业务数据'},
                'msg': {'type': 'string', 'description': '提示信息'},
                'requestId': {'type': 'string', 'description': '请求 ID'},
                'traceId': {'type': 'string', 'description': '链路追踪 ID'},
            },
        }

    # -- 类 / 枚举 -> Schema ---------------------------------------------
    def register_class(self, name):
        if name in self.schemas or name in self._processing:
            return
        entry = self.registry.get(name)
        if not entry:
            self.schemas[name] = {'type': 'object'}
            return
        self._processing.add(name)
        try:
            if entry['kind'] == 'enum':
                self.schemas[name] = self._build_enum(entry)
            elif entry['kind'] == 'interface':
                self.schemas[name] = {'type': 'object'}
            else:
                self.schemas[name] = self._build_class(entry)
        finally:
            self._processing.discard(name)

    def _build_enum(self, entry):
        decl = extract_type_decl(entry['masked'], entry['src'])
        body_m, body_o = decl['body_m'], decl['body_o']
        # 枚举常量位于类体起始处，到第一个顶层 ';' 为止
        const_end = len(body_m)
        depth = 0
        for i, c in enumerate(body_m):
            if c in '([{<':
                depth += 1
            elif c in ')]}>':
                depth = max(0, depth - 1)
            elif c == ';' and depth == 0:
                const_end = i
                break
        consts = []
        for (s, e) in split_top_level(body_m[:const_end]):
            piece = body_o[s:e]
            mm = re.match(r'\s*(?:@\w+(?:\([^()]*\))?\s*)*([A-Za-z_]\w*)', piece)
            if mm:
                consts.append(mm.group(1))
        schema = {'type': 'string'}
        if consts:
            schema['enum'] = consts
        desc = anno_str(find_annotations(decl['pretext_m'], decl['pretext_o']),
                        'Schema', 'description')
        jdoc = clean_javadoc(decl['pretext_o'])
        if desc or jdoc:
            schema['description'] = desc or jdoc
        return schema

    def _collect_fields(self, entry, seen):
        """收集类自身及其父类的字段成员 [(member_masked, member_orig), ...]。"""
        decl = extract_type_decl(entry['masked'], entry['src'])
        if not decl:
            return []
        fields = []
        for (s, e) in split_members(decl['body_m']):
            fields.append((decl['body_m'][s:e], decl['body_o'][s:e]))
        # 向上合并父类字段
        ext = re.search(r'\bextends\s+([A-Za-z_][\w.]*)', decl['header_m'])
        if ext:
            parent = ext.group(1).split('.')[-1]
            if parent not in seen and parent in self.registry \
                    and self.registry[parent]['kind'] != 'interface':
                seen.add(parent)
                fields = self._collect_fields(self.registry[parent], seen) + fields
        return fields

    def _build_class(self, entry):
        decl = extract_type_decl(entry['masked'], entry['src'])
        properties = {}
        required = []
        for member_m, member_o in self._collect_fields(entry, set()):
            field = self._parse_field(member_m, member_o)
            if not field:
                continue
            properties[field['name']] = field['schema']
            if field['required']:
                required.append(field['name'])
        schema = {'type': 'object'}
        desc = anno_str(find_annotations(decl['pretext_m'], decl['pretext_o']),
                        'Schema', 'description')
        jdoc = clean_javadoc(decl['pretext_o'])
        if desc or jdoc:
            schema['description'] = desc or jdoc
        if properties:
            schema['properties'] = properties
        if required:
            schema['required'] = sorted(set(required), key=required.index)
        return schema

    def _parse_field(self, member_m, member_o):
        """解析一个类成员，若为有效字段返回 {'name','schema','required'}，否则 None。"""
        vis = re.search(r'\b(private|protected|public)\b', member_m)
        if not vis:
            return None
        i = vis.end()
        is_static = False
        while True:
            mod = re.match(r'\s*(static|final|transient|volatile)\b', member_m[i:])
            if not mod:
                break
            if mod.group(1) == 'static':
                is_static = True
            i += mod.end()
        if is_static:                       # 常量不计入 schema
            return None
        java_type, i = read_type(member_m, i)
        if java_type is None:
            return None
        name_m = re.match(r'\s*([A-Za-z_]\w*)', member_m[i:])
        if not name_m:
            return None
        field_name = name_m.group(1)
        i += name_m.end()
        j = i
        while j < len(member_m) and member_m[j] in ' \t\r\n':
            j += 1
        if j >= len(member_m) or member_m[j] not in '=;':   # 非字段（方法/构造器）
            return None

        annos = find_annotations(member_m[:vis.start()], member_o[:vis.start()])
        javadoc = clean_javadoc(member_o[:vis.start()])

        schema = self.resolve(java_type)

        # @JsonProperty 可重命名 JSON 字段
        json_name = anno_str(annos, 'JsonProperty', 'value')
        if isinstance(json_name, str) and json_name:
            field_name = json_name

        description = anno_str(annos, 'Schema', 'description') or javadoc
        example = anno_str(annos, 'Schema', 'example')
        sc = get_anno(annos, 'Schema')
        allowable = None
        required = False
        if sc is not None:
            kw, _ = parse_anno_args(sc['raw'])
            allowable = kw.get('allowableValues')
            if kw.get('required') is True:
                required = True
            rm = kw.get('requiredMode')
            if isinstance(rm, str) and 'REQUIRED' in rm:
                required = True
        if any(get_anno(annos, a) for a in ('NotNull', 'NotBlank', 'NotEmpty')):
            required = True

        if allowable is None:
            allowable = self._infer_allowable_values(description)

        schema = self._decorate(schema, description, example, allowable)
        return {'name': field_name, 'schema': schema, 'required': required}

    @staticmethod
    def _infer_allowable_values(description):
        if not description:
            return None
        text = str(description)
        if '状态' not in text and '角色' not in text and '枚举' not in text:
            return None

        candidates = re.findall(r'[A-Za-z0-9_]+', text)
        values = []
        for candidate in candidates:
            if candidate.isupper() or candidate in {'Owner', 'Admin', 'Editor', 'Viewer'}:
                if candidate not in values:
                    values.append(candidate)

        if len(values) >= 2:
            return values

        return None

    @staticmethod
    def _decorate(schema, description, example, allowable):
        """为 schema 附加 description / example / enum。$ref 需用 allOf 包裹。"""
        meta = {}
        if description:
            meta['description'] = str(description)
        if example is not None:
            meta['example'] = _coerce_example(example, schema)
        is_ref = '$ref' in schema
        if not is_ref and isinstance(allowable, list) and allowable:
            schema = dict(schema)
            schema['enum'] = allowable
        if not meta:
            return schema
        if is_ref:
            return dict(meta, allOf=[schema])
        out = dict(schema)
        out.update(meta)
        return out


def _coerce_example(value, schema):
    """把注解里写成字符串的示例值，按 schema 类型尽量转成对应的 JSON 类型。"""
    t = schema.get('type')
    if isinstance(value, str):
        if t == 'integer':
            try:
                return int(value)
            except ValueError:
                return value
        if t == 'number':
            try:
                return float(value)
            except ValueError:
                return value
        if t == 'boolean':
            if value.lower() in ('true', 'false'):
                return value.lower() == 'true'
    return value


# ---------------------------------------------------------------------------
# 四、Controller 解析
# ---------------------------------------------------------------------------

_HTTP_MAPPING = {
    'GetMapping': 'get', 'PostMapping': 'post', 'PutMapping': 'put',
    'DeleteMapping': 'delete', 'PatchMapping': 'patch',
}
_SERVLET_TYPES = {'HttpServletResponse', 'ServletResponse', 'OutputStream'}


def _norm_path(*parts):
    """拼接并规范化 URL 路径，处理 Spring 的 {*var}、{var:regex} 写法。"""
    segs = []
    for p in parts:
        if not p:
            continue
        segs.extend(s for s in str(p).split('/') if s)
    path = '/' + '/'.join(segs)
    path = re.sub(r'\{\s*\*\s*([A-Za-z_]\w*)\s*\}', r'{\1}', path)    # {*var} -> {var}
    path = re.sub(r'\{\s*([A-Za-z_]\w*)\s*:[^{}]*\}', r'{\1}', path)  # {var:regex} -> {var}
    return path or '/'


def _mapping_path(anno):
    """从 @XxxMapping 注解里取出路径。"""
    if anno is None:
        return ''
    kw, pos = parse_anno_args(anno['raw'])
    for k in ('value', 'path'):
        v = kw.get(k)
        if isinstance(v, list) and v:
            return v[0]
        if isinstance(v, str):
            return v
    if pos:
        v = pos[0]
        return v[0] if isinstance(v, list) and v else v
    return ''


def parse_controller(raw_src, builder):
    """解析单个控制器源文件，返回该文件贡献的 operation 列表。"""
    src = strip_comments(raw_src)
    masked = mask_strings(src)
    decl = extract_type_decl(masked, src)
    if not decl or decl['kind'] != 'class':
        return []

    class_annos = find_annotations(decl['pretext_m'], decl['pretext_o'])
    if not (get_anno(class_annos, 'RestController') or get_anno(class_annos, 'Controller')):
        return []

    base_path = _mapping_path(get_anno(class_annos, 'RequestMapping'))
    tag = anno_str(class_annos, 'Tag', 'name') or decl['name']

    operations = []
    for (s, e) in split_members(decl['body_m']):
        member_m = decl['body_m'][s:e]
        member_o = decl['body_o'][s:e]
        annos, rest = consume_leading_annotations(member_m, member_o)

        http_method = None
        sub_path = ''
        for a in annos:
            if a['name'] in _HTTP_MAPPING:
                http_method = _HTTP_MAPPING[a['name']]
                sub_path = _mapping_path(a)
                break
        if http_method is None:
            rm = get_anno(annos, 'RequestMapping')
            if rm is not None:
                kw, _ = parse_anno_args(rm['raw'])
                method = kw.get('method')
                method = method[0] if isinstance(method, list) and method else method
                http_method = 'get'
                if isinstance(method, str):
                    mm = re.search(r'(GET|POST|PUT|DELETE|PATCH)', method)
                    if mm:
                        http_method = mm.group(1).lower()
                sub_path = _mapping_path(rm)
        if http_method is None:
            continue                        # 不是接口处理方法

        sig = _parse_signature(member_m[rest:], member_o[rest:])
        if not sig:
            continue

        full_path = _norm_path(base_path, sub_path)
        op = _build_operation(builder, tag, sig, annos, full_path)
        operations.append({
            'path': full_path,
            'http_method': http_method,
            'operation': op,
            'source': '%s#%s' % (decl['name'], sig['name']),
        })
    return operations


def _parse_signature(member_m, member_o):
    """从方法成员（已去掉前导注解）解析签名，返回 {'return','name','params'}。"""
    i = 0
    n = len(member_m)
    while i < n and member_m[i] in ' \t\r\n':
        i += 1
    while True:
        mod = re.match(r'(public|protected|private|static|final|abstract|'
                       r'synchronized|native|default|strictfp)\b\s*', member_m[i:])
        if not mod:
            break
        i += mod.end()
    if member_m[i:i + 1] == '<':            # 跳过泛型方法声明 <T>
        close = find_matching(member_m, i)
        if close != -1:
            i = close + 1
    return_type, i = read_type(member_m, i)
    if return_type is None:
        return None
    name_m = re.match(r'\s*([A-Za-z_]\w*)', member_m[i:])
    if not name_m:
        return None
    method_name = name_m.group(1)
    i += name_m.end()
    while i < n and member_m[i] in ' \t\r\n':
        i += 1
    if i >= n or member_m[i] != '(':
        return None
    close = find_matching(member_m, i)
    if close == -1:
        return None
    params_m = member_m[i + 1:close]
    params_o = member_o[i + 1:close]
    params = []
    for (s, e) in split_top_level(params_m):
        if not params_m[s:e].strip():
            continue
        p = _parse_param(params_m[s:e], params_o[s:e])
        if p:
            params.append(p)
    return {'return': return_type, 'name': method_name, 'params': params}


def _parse_param(pm, po):
    """解析单个方法形参，返回 {'annos','type','name'}。"""
    annos, rest = consume_leading_annotations(pm, po)
    body_m = pm[rest:]
    i = 0
    while True:
        mod = re.match(r'\s*final\b', body_m[i:])
        if not mod:
            break
        i += mod.end()
    java_type, i = read_type(body_m, i)
    if java_type is None:
        return None
    name_m = re.match(r'\s*\.{0,3}\s*([A-Za-z_]\w*)', body_m[i:])  # 兼容可变参数 ...
    pname = name_m.group(1) if name_m else java_type.lower()
    return {'annos': annos, 'type': java_type, 'name': pname}


def _is_file_type(java_type):
    base, args, _arr = split_generic(java_type)
    if base == 'MultipartFile':
        return True
    if base in _COLLECTION and args and split_generic(args[0])[0] == 'MultipartFile':
        return True
    return False


def _build_operation(builder, tag, sig, method_annos, full_path):
    """根据方法签名与注解构造一个 OpenAPI operation 对象。"""
    summary = anno_str(method_annos, 'Operation', 'summary')
    description = anno_str(method_annos, 'Operation', 'description')

    op = {
        'tags': [tag],
        'summary': summary or sig['name'],
        'operationId': sig['name'],
    }
    if description:
        op['description'] = description

    parameters = []
    body_param = None
    form_params = []          # multipart/form-data 的字段
    has_multipart = any(_is_file_type(p['type']) or get_anno(p['annos'], 'RequestPart')
                        for p in sig['params'])

    for p in sig['params']:
        annos = p['annos']
        path_var = get_anno(annos, 'PathVariable')
        req_param = get_anno(annos, 'RequestParam')
        req_body = get_anno(annos, 'RequestBody')
        req_part = get_anno(annos, 'RequestPart')
        req_header = get_anno(annos, 'RequestHeader')
        param_desc = anno_str(annos, 'Parameter', 'description')

        if path_var is not None:
            kw, pos = parse_anno_args(path_var['raw'])
            pname = kw.get('value') or kw.get('name') or (pos[0] if pos else p['name'])
            param = {'name': pname, 'in': 'path', 'required': True,
                     'schema': _schema_for_param(builder.resolve(p['type']))}
            if param_desc:
                param['description'] = param_desc
            parameters.append(param)
        elif req_header is not None:
            kw, pos = parse_anno_args(req_header['raw'])
            pname = kw.get('value') or kw.get('name') or (pos[0] if pos else p['name'])
            required = kw.get('required', True) is not False
            param = {'name': pname, 'in': 'header', 'required': required,
                     'schema': _schema_for_param(builder.resolve(p['type']))}
            if param_desc:
                param['description'] = param_desc
            parameters.append(param)
        elif req_body is not None:
            kw, _ = parse_anno_args(req_body['raw'])
            body_param = {'type': p['type'],
                          'required': kw.get('required', True) is not False,
                          'description': param_desc}
        elif has_multipart and (req_part is not None or req_param is not None
                                or _is_file_type(p['type'])):
            src_anno = req_part or req_param
            kw, pos = parse_anno_args(src_anno['raw']) if src_anno is not None else ({}, [])
            pname = kw.get('value') or kw.get('name') or (pos[0] if pos else p['name'])
            required = kw.get('required', True) is not False
            if 'defaultValue' in kw:
                required = False
            form_params.append({'name': pname, 'type': p['type'],
                                 'required': required, 'description': param_desc,
                                 'default': kw.get('defaultValue')})
        elif req_param is not None:
            kw, pos = parse_anno_args(req_param['raw'])
            pname = kw.get('value') or kw.get('name') or (pos[0] if pos else p['name'])
            required = kw.get('required', True) is not False
            schema = _schema_for_param(builder.resolve(p['type']))
            if 'defaultValue' in kw and kw['defaultValue'] is not None:
                required = False
                schema = dict(schema)
                schema['default'] = _coerce_example(kw['defaultValue'], schema)
            param = {'name': pname, 'in': 'query', 'required': required,
                     'schema': schema}
            if param_desc:
                param['description'] = param_desc
            parameters.append(param)
        # 其余无关注解 / Servlet 类型形参（HttpServletResponse 等）忽略

    # 路径里出现但未显式 @PathVariable 的占位符也补成 path 参数
    declared = {p['name'] for p in parameters if p['in'] == 'path'}
    for var in re.findall(r'\{([A-Za-z_]\w*)\}', full_path):
        if var not in declared:
            parameters.append({'name': var, 'in': 'path', 'required': True,
                               'schema': {'type': 'string'}})
            declared.add(var)

    if parameters:
        op['parameters'] = parameters

    # 请求体
    if has_multipart and form_params:
        props = {}
        req_list = []
        for fp in form_params:
            fs = _schema_for_param(builder.resolve(fp['type']))
            if fp.get('default') is not None:
                fs = dict(fs)
                fs['default'] = _coerce_example(fp['default'], fs)
            if fp.get('description'):
                fs = dict(fs)
                fs['description'] = fp['description']
            props[fp['name']] = fs
            if fp['required']:
                req_list.append(fp['name'])
        form_schema = {'type': 'object', 'properties': props}
        if req_list:
            form_schema['required'] = req_list
        op['requestBody'] = {
            'required': bool(req_list),
            'content': {'multipart/form-data': {'schema': form_schema}},
        }
    elif body_param is not None:
        rb = {
            'required': body_param['required'],
            'content': {'application/json': {
                'schema': builder.resolve(body_param['type'])}},
        }
        if body_param.get('description'):
            rb['description'] = body_param['description']
        op['requestBody'] = rb

    # 响应
    op['responses'] = _build_responses(builder, sig)
    return op


def _schema_for_param(schema):
    """path/query/header 参数 schema：$ref / allOf 退化为字符串以保证工具兼容。"""
    if '$ref' in schema or 'allOf' in schema:
        return {'type': 'string'}
    return schema


def _build_responses(builder, sig):
    """根据方法返回类型构造 responses。"""
    return_type = sig['return']
    base, args, _arr = split_generic(return_type)

    if base == 'void':
        has_servlet = any(split_generic(p['type'])[0] in _SERVLET_TYPES
                          for p in sig['params'])
        if has_servlet:
            return {'200': {
                'description': '二进制文件流',
                'content': {'application/octet-stream': {
                    'schema': {'type': 'string', 'format': 'binary'}}},
            }}
        return {'200': {'description': '操作成功'}}

    if base == 'Result':
        builder.ensure_result()
        inner = args[0] if args else None
        inner_base = split_generic(inner)[0] if inner else None
        if not inner or inner_base in ('Void', 'Object', '?'):
            schema = {'$ref': '#/components/schemas/Result'}
        else:
            schema = {'allOf': [
                {'$ref': '#/components/schemas/Result'},
                {'type': 'object', 'properties': {'data': builder.resolve(inner)}},
            ]}
        return {'200': {
            'description': '成功响应（统一结构 Result）',
            'content': {'application/json': {'schema': schema}},
        }}

    # 其它返回类型（ResponseEntity<T> / 直接返回 VO 等）
    return {'200': {
        'description': '成功响应',
        'content': {'application/json': {'schema': builder.resolve(return_type)}},
    }}


# ---------------------------------------------------------------------------
# 五、项目扫描与文档组装
# ---------------------------------------------------------------------------


def find_project_root(argv):
    """确定项目根目录：命令行参数优先，否则当前目录 / 脚本上级目录。"""
    candidates = []
    if len(argv) > 1:
        candidates.append(os.path.abspath(argv[1]))
    candidates.append(os.getcwd())
    candidates.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    for c in candidates:
        if os.path.isfile(os.path.join(c, 'pom.xml')) or \
                os.path.isdir(os.path.join(c, 'src', 'main', 'java')):
            return c
    return candidates[0]


def collect_java_files(root):
    """收集 src/main/java 下的全部 .java 文件。"""
    base = os.path.join(root, 'src', 'main', 'java')
    files = []
    for dirpath, _dirs, filenames in os.walk(base):
        for fn in filenames:
            if fn.endswith('.java'):
                files.append(os.path.join(dirpath, fn))
    return sorted(files)


def build_registry(files):
    """建立 “类简单名 -> 声明信息” 的索引，供类型解析按需查找。"""
    registry = {}
    for path in files:
        try:
            with open(path, encoding='utf-8') as f:
                raw = f.read()
        except (OSError, UnicodeDecodeError):
            continue
        src = strip_comments(raw)
        masked = mask_strings(src)
        decl = extract_type_decl(masked, src)
        if not decl:
            continue
        registry.setdefault(decl['name'], {
            'kind': decl['kind'], 'src': src, 'masked': masked, 'path': path,
        })
    return registry


def read_pom(root):
    """从 pom.xml 读取项目自身坐标。

    先剔除 ``<parent>`` 子树，再截断到首个结构化区段（properties/dependencies/build
    等）之前，剩下的就是 <project> 的直接元数据子元素，避免取到依赖里的版本号。
    """
    info = {}
    try:
        with open(os.path.join(root, 'pom.xml'), encoding='utf-8') as f:
            txt = f.read()
    except OSError:
        return info
    seg = re.sub(r'<parent>.*?</parent>', '', txt, flags=re.S)
    for marker in ('<properties>', '<dependencyManagement>', '<dependencies>',
                   '<build>', '<profiles>', '<modules>', '<reporting>'):
        idx = seg.find(marker)
        if idx != -1:
            seg = seg[:idx]
    for tag in ('artifactId', 'version', 'description', 'name'):
        mm = re.search(r'<' + tag + r'>([^<]+)</' + tag + r'>', seg)
        if mm:
            info[tag] = mm.group(1).strip()
    return info


def read_server(root):
    """从 application*.yml 推断服务端口，构造 server URL。"""
    res = os.path.join(root, 'src', 'main', 'resources')
    active = 'dev'
    try:
        with open(os.path.join(res, 'application.yml'), encoding='utf-8') as f:
            mm = re.search(r'active:\s*([\w-]+)', f.read())
            if mm:
                active = mm.group(1)
    except OSError:
        pass
    port = '8080'
    for fn in ('application-%s.yml' % active, 'application.yml', 'application.properties'):
        try:
            with open(os.path.join(res, fn), encoding='utf-8') as f:
                mm = re.search(r'(?:^|\n)\s*port:\s*(\d+)', f.read())
                if mm:
                    port = mm.group(1)
                    break
        except OSError:
            continue
    return active, port


def main(argv):
    root = find_project_root(argv)
    print('[openapi-gen] 项目根目录: %s' % root)

    java_files = collect_java_files(root)
    if not java_files:
        print('[openapi-gen] 未在 src/main/java 下找到 Java 源文件，已退出。', file=sys.stderr)
        return 1
    print('[openapi-gen] 扫描到 %d 个 Java 文件' % len(java_files))

    registry = build_registry(java_files)
    builder = SchemaBuilder(registry)

    # 解析所有控制器
    all_ops = []
    controller_count = 0
    for path in java_files:
        try:
            with open(path, encoding='utf-8') as f:
                raw = f.read()
        except (OSError, UnicodeDecodeError):
            continue
        if '@RestController' not in raw and '@Controller' not in raw:
            continue
        ops = parse_controller(raw, builder)
        if ops:
            controller_count += 1
            all_ops.extend(ops)

    # 组装 paths，并保证 operationId 全局唯一
    paths = {}
    seen_ids = {}
    tags = []
    for item in sorted(all_ops, key=lambda x: (x['path'], x['http_method'])):
        op = item['operation']
        for t in op.get('tags', []):
            if t not in tags:
                tags.append(t)

        oid = op['operationId']
        if oid in seen_ids:
            seen_ids[oid] += 1
            op['operationId'] = '%s_%d' % (oid, seen_ids[oid])
        else:
            seen_ids[oid] = 0

        path_item = paths.setdefault(item['path'], {})
        if item['http_method'] in path_item:
            print('[openapi-gen] 警告: 路径冲突 %s [%s]，来源 %s 被忽略'
                  % (item['path'], item['http_method'].upper(), item['source']),
                  file=sys.stderr)
            continue
        path_item[item['http_method']] = op

    # 元信息
    pom = read_pom(root)
    active, port = read_server(root)
    title = (pom.get('name') or pom.get('artifactId') or 'backend') + ' API'

    doc = {
        'openapi': '3.0.3',
        'info': {
            'title': title,
            'description': (pom.get('description') or '')
            + '（由 script/openapi_doc_gen.py 静态扫描源码自动生成）',
            'version': pom.get('version') or '1.0.0',
        },
        'servers': [
            {'url': 'http://localhost:%s' % port,
             'description': '本地环境（profile=%s）' % active},
        ],
        'tags': [{'name': t} for t in tags],
        'paths': paths,
        'components': {
            'schemas': dict(sorted(builder.schemas.items())),
            'securitySchemes': {
                'bearerAuth': {
                    'type': 'http', 'scheme': 'bearer', 'bearerFormat': 'JWT',
                    'description': '在请求头携带 Authorization: Bearer <token>',
                },
            },
        },
        'security': [{'bearerAuth': []}],
    }

    # 输出
    out_dir = os.path.join(root, 'target')
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, 'openapi.code.json')
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write('\n')

    op_total = sum(len(v) for v in paths.values())
    print('[openapi-gen] 控制器 %d 个 | 路径 %d 条 | 接口 %d 个 | Schema %d 个'
          % (controller_count, len(paths), op_total, len(builder.schemas)))
    print('[openapi-gen] 已生成: %s' % out_path)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
