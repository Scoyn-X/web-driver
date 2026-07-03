"""Backend Java code linter.

Run as ``python script/code_check.py [project_root]``. The script auto-discovers
the project root by walking upward from ``project_root`` until it finds a
directory that contains both ``src`` and ``sql``.

Architecture (top to bottom):
    1.  Constants and ANSI colors
    2.  Configuration loader
    3.  .checkignore matcher
    4.  Defect dataclass
    5.  Java AST / comment helpers (stateless module-level functions)
    6.  JavaFileContext - per-file shared state used by every Java rule
    7.  Rule base classes: Rule (per file), ProjectRule (cross file), SqlRule
    8.  Rule implementations grouped by category
    9.  Rule registry
    10. BackendLinter - orchestrates parsing + rule execution
    11. ESLint-style reporter
    12. CLI entry point

Adding a new rule = subclass the appropriate base, implement ``check`` and
``applies_to``, then append the instance to the matching list in section 9.
"""

import argparse
import re
import hashlib
import json
import os
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import PurePosixPath
from typing import Callable, Dict, Iterable, List, Optional, Set, Tuple

import javalang

# ---------------------------------------------------------------------------
# 1. Constants and ANSI colors
# ---------------------------------------------------------------------------

IGNORE_FILE = ".checkignore"

# Lines that do not matter for duplicate detection.
NOISE_LINES = {"{", "}", "});", "})", "};", ");"}

SQL_KEYWORDS = ("SELECT ", "INSERT ", "UPDATE ", "DELETE ")

PACKAGE_VO = "vo"
PACKAGE_ENTITY = "entity"
PACKAGE_MAPPER = "mapper"
PACKAGE_SERVICE = "service"
PACKAGE_CONVERTER = "converter"
PACKAGE_CONTROLLER = "controller"
PACKAGE_ENUMS = "enums"

VO_SUFFIXES = ("RequestVO", "ResponseVO")
MAPPING_TYPE_SUFFIXES = ("VO", "BO", "Entity", "DTO")
SKIPPABLE_FIELD_NAMES = {"serialVersionUID", "log", "logger"}
COMMENT_FREE_PUBLIC_METHODS = {"hashCode", "equals", "toString", "get", "set"}
SQL_ANNOTATIONS = {"Select", "Insert", "Update", "Delete"}
ID_LIKE_ANNOTATIONS = {"PathVariable", "RequestParam"}
WRAPPER_LAMBDA_TYPES = {
    "LambdaQueryWrapper",
    "LambdaUpdateWrapper",
    "LambdaQueryChainWrapper",
}
WRAPPER_UPDATE_TYPES = {"LambdaUpdateWrapper", "UpdateWrapper"}
MAPPER_XML_RULE_TAGS = (
    "if",
    "choose",
    "when",
    "otherwise",
    "foreach",
    "trim",
    "where",
    "set",
    "bind",
    "script",
)


class Colors:
    UNDERLINE = "\033[4m"
    RED = "\033[31m"
    GREEN = "\033[32m"
    YELLOW = "\033[33m"
    DIM = "\033[2m"
    RESET = "\033[0m"
    BOLD = "\033[1m"


# ---------------------------------------------------------------------------
# 2. Configuration loader
# ---------------------------------------------------------------------------


def load_config(_root_dir: str) -> dict:
    """Load ``script/config.json`` next to this script.

    A missing or malformed file degrades to a minimal placeholder so the linter
    can still run (with all rules effectively disabled because no config keys
    will match), but a warning is emitted to stderr.
    """
    config_path = os.path.join(os.path.dirname(__file__), "config.json")
    if not os.path.exists(config_path):
        print(f"Warning: config.json not found at {config_path}", file=sys.stderr)
        return {"output": {}, "rules": {}, "scan": {}}

    try:
        with open(config_path, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception as exc:
        print(f"Warning: failed to read config file: {exc}", file=sys.stderr)
        return {"output": {}, "rules": {}, "scan": {}}


def _parse_cli_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="code_check.py",
        description="Backend Java code linter.",
    )
    parser.add_argument(
        "project_root",
        nargs="?",
        default=".",
        help="Project root or any directory under it (auto-discovers src/ + sql/).",
    )
    parser.add_argument(
        "--mode",
        choices=["full", "incremental"],
        default=None,
        help="full = scan everything (default), "
        "incremental = only scan files that differ from --base.",
    )
    parser.add_argument(
        "--base",
        default=None,
        help="Base git ref for incremental mode (default: 'master', or scan.incrementalBaseRef in config.json).",
    )
    return parser.parse_args(argv)


def discover_changed_files(project_root: str, base_ref: str) -> Optional[Set[str]]:
    """Return absolute paths of files changed vs ``base_ref``.

    Combines three git diffs:
        - committed:  ``<base>...HEAD`` (commits in HEAD not in base)
        - staged:     ``--cached`` against HEAD
        - unstaged:   working tree against HEAD

    All three use ``--diff-filter=ACMR`` to skip deleted files. Returns
    ``None`` if any git invocation fails (treated as a hard error by the CLI).
    """
    diffs = [
        ["git", "-C", project_root, "diff", "--name-only", "--diff-filter=ACMR", f"{base_ref}...HEAD"],
        ["git", "-C", project_root, "diff", "--name-only", "--diff-filter=ACMR", "--cached"],
        ["git", "-C", project_root, "diff", "--name-only", "--diff-filter=ACMR"],
    ]
    rel_paths: Set[str] = set()
    for cmd in diffs:
        try:
            out = subprocess.run(cmd, capture_output=True, text=True, check=True)
        except (subprocess.CalledProcessError, FileNotFoundError) as exc:
            print(f"Warning: {' '.join(cmd)} failed: {exc}", file=sys.stderr)
            return None
        for line in out.stdout.splitlines():
            line = line.strip()
            if line:
                rel_paths.add(line)
    return {os.path.abspath(os.path.join(project_root, p)) for p in rel_paths}


# ---------------------------------------------------------------------------
# 3. .checkignore matcher
# ---------------------------------------------------------------------------


class IgnoreMatcher:
    """Simple ``.checkignore`` matcher with path globs and optional rule filters.

    Each non-empty / non-comment line is ``<glob>`` or ``<glob>:<rule1>,<rule2>``.
    Without a colon, the glob ignores defects of every rule for matching paths.
    With a colon, only listed rule IDs are ignored.
    """

    def __init__(self, root_dir: str):
        self.entries: List[Tuple[str, Optional[Set[str]]]] = []
        path = os.path.join(root_dir, IGNORE_FILE)
        if not os.path.isfile(path):
            return

        with open(path, "r", encoding="utf-8") as f:
            for raw in f:
                line = raw.strip()
                if not line or line.startswith("#"):
                    continue
                rules: Optional[Set[str]] = None
                if ":" in line:
                    pattern, rule_part = line.rsplit(":", 1)
                    rules = {
                        rule.strip() for rule in rule_part.split(",") if rule.strip()
                    }
                else:
                    pattern = line
                self.entries.append((pattern.strip(), rules))

    def matches(self, rel_path: str, rule_id: str) -> bool:
        norm = rel_path.replace(os.sep, "/")
        for pattern, rules in self.entries:
            if rules is not None and rule_id not in rules:
                continue
            if self._matches_pattern(norm, pattern):
                return True
        return False

    @staticmethod
    def _matches_pattern(path: str, pattern: str) -> bool:
        try:
            return PurePosixPath(path).match(pattern)
        except Exception:
            return path == pattern


# ---------------------------------------------------------------------------
# 4. Defect dataclass
# ---------------------------------------------------------------------------


@dataclass
class Defect:
    file: str
    line: int
    column: int
    level: str
    message: str
    rule: str


# ---------------------------------------------------------------------------
# 5. Java AST / comment helpers (stateless)
# ---------------------------------------------------------------------------


def first_type_declaration(tree):
    for type_decl in getattr(tree, "types", []) or []:
        if isinstance(
            type_decl,
            (javalang.tree.ClassDeclaration, javalang.tree.InterfaceDeclaration),
        ):
            return type_decl
    return None


def node_line(node) -> Optional[int]:
    position = getattr(node, "position", None) or getattr(node, "_position", None)
    return position.line if position else None


def annotation_name(annotation) -> str:
    return (getattr(annotation, "name", "") or "").split(".")[-1]


def annotation_pairs(annotation) -> Dict[str, str]:
    element = getattr(annotation, "element", None)
    if element is None:
        return {}
    if isinstance(element, list):
        result: Dict[str, str] = {}
        for pair in element:
            pair_name = getattr(pair, "name", None)
            if pair_name is None:
                continue
            result[pair_name] = expression_text(getattr(pair, "value", None))
        return result
    return {"value": expression_text(element)}


def annotation_value(annotation, key: str) -> Optional[str]:
    return annotation_pairs(annotation).get(key)


def type_name(type_node) -> str:
    if type_node is None:
        return ""
    name = getattr(type_node, "name", "") or ""
    sub_type = getattr(type_node, "sub_type", None)
    if sub_type is not None:
        sub_name = type_name(sub_type)
        if sub_name:
            return f"{name}.{sub_name}"
    return name


def is_vo_type(name: str) -> bool:
    return name.endswith("VO")


def is_mapping_type(name: str) -> bool:
    return name.endswith(MAPPING_TYPE_SUFFIXES)


def expression_text(node) -> str:
    """Best-effort flat string serialization of a javalang expression node."""
    if node is None:
        return ""
    if isinstance(node, javalang.tree.Literal):
        value = node.value
        if (
            isinstance(value, str)
            and len(value) >= 2
            and value[0] == value[-1]
            and value[0] in {'"', "'"}
        ):
            return value[1:-1]
        return str(value)
    if isinstance(node, javalang.tree.BinaryOperation):
        left = expression_text(node.operandl)
        right = expression_text(node.operandr)
        if node.operator == "+":
            return left + right
        return f"{left}{node.operator}{right}"
    if isinstance(node, javalang.tree.MemberReference):
        qualifier = f"{node.qualifier}." if node.qualifier else ""
        return f"{qualifier}{node.member}"
    if isinstance(node, javalang.tree.MethodInvocation):
        qualifier = f"{node.qualifier}." if node.qualifier else ""
        arguments = ", ".join(
            expression_text(argument) for argument in node.arguments or []
        )
        return f"{qualifier}{node.member}({arguments})"
    if isinstance(node, javalang.tree.ClassCreator):
        return type_name(getattr(node, "type", None))
    if isinstance(node, javalang.tree.TernaryExpression):
        return expression_text(node.if_true) + expression_text(node.if_false)
    if isinstance(node, list):
        return "".join(expression_text(item) for item in node)
    return str(node)


def has_leading_comment(lines: List[str], anchor_line: int) -> bool:
    """True if the line just above ``anchor_line`` is a comment.

    Walks upward, skipping blank lines and ``@`` annotations, and reports
    whether the first non-blank non-annotation line above is a comment.
    """
    index = anchor_line - 2
    while index >= 0:
        text = lines[index].strip()
        if not text:
            index -= 1
            continue
        if text.startswith("@"):
            index -= 1
            continue
        if (
            text.startswith("//")
            or text.startswith("/**")
            or text.startswith("/*")
            or text.startswith("*")
            or text.endswith("*/")
        ):
            return True
        return False
    return False


def extract_leading_comment(lines: List[str], anchor_line: int) -> Optional[str]:
    """Return the full text of the comment block above ``anchor_line`` or None."""
    index = anchor_line - 2
    while index >= 0:
        text = lines[index].rstrip()
        if not text:
            index -= 1
            continue
        if text.startswith("@"):
            index -= 1
            continue
        # block comment
        if text.endswith("*/"):
            block = [text]
            index -= 1
            while index >= 0:
                t = lines[index].rstrip()
                block.insert(0, t)
                if t.strip().startswith("/**") or t.strip().startswith("/*"):
                    break
                index -= 1
            return "\n".join(block)
        # single-line comments
        if text.startswith("//"):
            block = [text]
            index -= 1
            while index >= 0 and lines[index].strip().startswith("//"):
                block.insert(0, lines[index].rstrip())
                index -= 1
            return "\n".join(block)
        return None
    return None


def find_block_end_line(content_lines: List[str], start_line: int) -> int:
    """Find the source line where the brace-block opened at/after ``start_line`` closes."""
    n = len(content_lines)
    idx = start_line - 1
    start_i = -1
    for i in range(idx, n):
        if "{" in content_lines[i]:
            start_i = i
            break
    if start_i < 0:
        return start_line

    depth = 0
    for i in range(start_i, n):
        for ch in content_lines[i]:
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return i + 1
    return n


def normalize_for_duplication(content: str) -> List[Tuple[int, str]]:
    """Strip comments, blanks, package/import lines and brace-only noise.

    Returns ``[(original_line_number, normalized_line_text), ...]``.
    """
    normalized: List[Tuple[int, str]] = []
    in_block_comment = False

    for line_number, raw_line in enumerate(content.splitlines(), 1):
        cleaned: List[str] = []
        index = 0
        while index < len(raw_line):
            if in_block_comment:
                end = raw_line.find("*/", index)
                if end == -1:
                    index = len(raw_line)
                    break
                in_block_comment = False
                index = end + 2
                continue

            line_comment = raw_line.find("//", index)
            block_comment = raw_line.find("/*", index)

            if line_comment != -1 and (
                block_comment == -1 or line_comment < block_comment
            ):
                cleaned.append(raw_line[index:line_comment])
                index = len(raw_line)
                break

            if block_comment == -1:
                cleaned.append(raw_line[index:])
                index = len(raw_line)
                break

            cleaned.append(raw_line[index:block_comment])
            index = block_comment + 2
            in_block_comment = True

        text = "".join(cleaned).strip()
        if not text:
            continue
        if text.startswith("package ") or text.startswith("import "):
            continue
        if text in NOISE_LINES:
            continue
        normalized.append((line_number, " ".join(text.split())))
    return normalized


# ---------------------------------------------------------------------------
# 6. JavaFileContext
# ---------------------------------------------------------------------------


@dataclass
class JavaFileContext:
    path: str
    rel_path: str
    content: str
    lines: List[str]
    tree: object
    package_name: str
    package_segments: Set[str]
    type_decl: object
    type_name: str
    type_line: int
    is_interface: bool
    is_test_file: bool

    @property
    def is_vo_package(self) -> bool:
        return PACKAGE_VO in self.package_segments

    @property
    def is_entity_package(self) -> bool:
        return PACKAGE_ENTITY in self.package_segments

    @property
    def is_mapper_package(self) -> bool:
        return PACKAGE_MAPPER in self.package_segments

    @property
    def is_service_package(self) -> bool:
        return PACKAGE_SERVICE in self.package_segments

    @property
    def is_converter_package(self) -> bool:
        return PACKAGE_CONVERTER in self.package_segments

    @property
    def is_controller_package(self) -> bool:
        return PACKAGE_CONTROLLER in self.package_segments

    @property
    def is_enums_package(self) -> bool:
        return PACKAGE_ENUMS in self.package_segments


# ---------------------------------------------------------------------------
# 7. Rule base classes
# ---------------------------------------------------------------------------


# emit signatures
#   per-file rule    : emit(line: int, message: str)
#   project rule     : emit(file_path: str, line: int, message: str)
#   sql rule         : emit(file_path: str, line: int, message: str)
FileEmit = Callable[[int, str], None]
LocationEmit = Callable[[str, int, str], None]


class Rule:
    """Base class for rules that process one Java file at a time.

    Set ``requires_type_decl = False`` for rules that work without a primary
    class/interface declaration (e.g. enum-only rules or package-structure
    rules that only look at the package path).
    """

    id: str = ""
    default_config: Dict[str, object] = {"enabled": True}
    requires_type_decl: bool = True

    def applies_to(self, ctx: JavaFileContext) -> bool:
        return True

    def check(self, ctx: JavaFileContext, cfg: dict, emit: FileEmit) -> None:
        raise NotImplementedError


class ProjectRule:
    """Base class for rules that need to look at every Java file together."""

    id: str = ""
    default_config: Dict[str, object] = {"enabled": True}

    def check(
        self,
        files: Dict[str, List[Tuple[int, str]]],
        cfg: dict,
        emit: LocationEmit,
    ) -> None:
        raise NotImplementedError


class SqlRule:
    """Base class for rules that process the project's SQL files."""

    id: str = ""
    default_config: Dict[str, object] = {"enabled": True}

    def check(
        self, sql_files: List[str], sql_dir: str, cfg: dict, emit: LocationEmit
    ) -> None:
        raise NotImplementedError


class MapperXmlRule:
    """Base class for rules that process MyBatis mapper XML files."""

    id: str = ""
    default_config: Dict[str, object] = {"enabled": True}

    def check(self, xml_files: List[str], cfg: dict, emit: LocationEmit) -> None:
        raise NotImplementedError


# ---------------------------------------------------------------------------
# 8. Rule implementations
# ---------------------------------------------------------------------------

# --- naming -----------------------------------------------------------------


class VoSuffixRule(Rule):
    id = "naming/vo-suffix"

    def applies_to(self, ctx):
        return ctx.is_vo_package

    def check(self, ctx, cfg, emit):
        name = ctx.type_name
        if not (name.endswith(VO_SUFFIXES[0]) or name.endswith(VO_SUFFIXES[1])):
            emit(
                ctx.type_line,
                f"Classes under the vo package must end with RequestVO or ResponseVO ({name})",
            )


class NoDtoRule(Rule):
    id = "naming/no-dto"

    def check(self, ctx, cfg, emit):
        if "dto" in ctx.type_name.lower():
            emit(ctx.type_line, f"DTO naming is not allowed ({ctx.type_name})")


class PackageNoMultiWordRule(Rule):
    id = "naming/package-no-multi-word"
    requires_type_decl = False

    def check(self, ctx, cfg, emit):
        if not ctx.package_name:
            return

        package_node = getattr(ctx.tree, "package", None)
        line = node_line(package_node) or 1
        for segment in ctx.package_name.split("."):
            if "_" not in segment:
                continue
            emit(
                line,
                f"Package segments must not contain underscores; found '{segment}' in '{ctx.package_name}'",
            )
            return


# --- api --------------------------------------------------------------------


class _OperationSummaryWalker:
    """Iterates @Operation summaries once per file for both summary rules."""

    @staticmethod
    def iter_summaries(tree):
        for _, method in tree.filter(javalang.tree.MethodDeclaration):
            for annotation in method.annotations or []:
                if annotation_name(annotation) != "Operation":
                    continue
                summary = annotation_value(annotation, "summary")
                if summary is None:
                    continue
                line = node_line(annotation) or node_line(method) or 1
                yield line, summary


class SummaryLengthRule(Rule):
    id = "api/summary-length"
    default_config = {"enabled": True, "maxLength": 20}

    def check(self, ctx, cfg, emit):
        max_len = cfg.get("maxLength", 20)
        for line, summary in _OperationSummaryWalker.iter_summaries(ctx.tree):
            if len(summary) > max_len:
                emit(
                    line,
                    f"@Operation summary exceeds {max_len} characters (actual {len(summary)})",
                )


class SummaryBannedWordRule(Rule):
    id = "api/summary-banned-word"
    default_config = {
        "enabled": True,
        "bannedWords": ["新增", "添加", "更新", "编辑", "更改", "彻底删除"],
    }

    def check(self, ctx, cfg, emit):
        banned = cfg.get(
            "bannedWords", ["新增", "添加", "更新", "编辑", "更改", "彻底删除"]
        )
        for line, summary in _OperationSummaryWalker.iter_summaries(ctx.tree):
            if any(word in summary for word in banned):
                emit(line, "@Operation summary contains a banned term")


class InterfaceIdNamingRule(Rule):
    id = "interface/id-naming"
    default_config = {"enabled": True, "allowedNames": ["id", "parentId"]}

    def applies_to(self, ctx):
        return ctx.is_controller_package

    def check(self, ctx, cfg, emit):
        allowed = set(cfg.get("allowedNames", ["id", "parentId"]))
        for _, method in ctx.tree.filter(javalang.tree.MethodDeclaration):
            for parameter in method.parameters or []:
                names = {
                    annotation_name(annotation)
                    for annotation in (parameter.annotations or [])
                }
                if not names.intersection(ID_LIKE_ANNOTATIONS):
                    continue
                pname = parameter.name or ""
                if pname in allowed:
                    continue
                if "id" in pname.lower():
                    line = node_line(parameter) or node_line(method) or 1
                    emit(
                        line,
                        f"ID-like parameters in controller methods must use id or parentId, not {pname}",
                    )


# --- model ------------------------------------------------------------------


class ModelFieldCommentRule(Rule):
    id = "model/field-comment"

    def applies_to(self, ctx):
        return ctx.is_vo_package or ctx.is_entity_package

    def check(self, ctx, cfg, emit):
        for _, field_decl in ctx.tree.filter(javalang.tree.FieldDeclaration):
            if not field_decl.declarators:
                continue
            if self._has_documentation(field_decl, ctx.lines):
                continue
            line = node_line(field_decl) or 1
            for declarator in field_decl.declarators:
                if declarator.name in SKIPPABLE_FIELD_NAMES:
                    continue
                emit(line, f"Field {declarator.name} is missing a comment")

    @staticmethod
    def _has_documentation(field_decl, lines):
        for annotation in field_decl.annotations or []:
            if annotation_name(annotation) == "Schema":
                return True
        anchor = node_line(field_decl)
        if anchor is None:
            return False
        return has_leading_comment(lines, anchor)


class EntityNoSchemaRule(Rule):
    id = "model/entity-no-schema"

    def applies_to(self, ctx):
        return ctx.is_entity_package

    def check(self, ctx, cfg, emit):
        type_decl = ctx.type_decl
        if type_decl is not None:
            for annotation in getattr(type_decl, "annotations", []) or []:
                if annotation_name(annotation) != "Schema":
                    continue
                line = node_line(annotation) or ctx.type_line or 1
                emit(line, "Entity classes must not use @Schema annotations")

        for _, field_decl in ctx.tree.filter(javalang.tree.FieldDeclaration):
            for annotation in field_decl.annotations or []:
                if annotation_name(annotation) != "Schema":
                    continue
                line = node_line(annotation) or node_line(field_decl) or 1
                emit(line, "Entity fields must not use @Schema annotations")


class ModelPackageStructureRule(Rule):
    id = "model/package-structure"
    requires_type_decl = False
    default_config = {
        "enabled": True,
        "allowedSubPackages": ["vo", "bo", "enums", "entity"],
    }

    def applies_to(self, ctx):
        if not ctx.package_name:
            return False
        return "model" in ctx.package_name.split(".")

    def check(self, ctx, cfg, emit):
        allowed = list(
            cfg.get("allowedSubPackages", ["vo", "bo", "enums", "entity"])
        )
        segments = ctx.package_name.split(".")
        try:
            idx = segments.index("model")
        except ValueError:
            return
        line = ctx.type_line or 1
        if idx == len(segments) - 1:
            emit(
                line,
                f"Files directly under model package are not allowed; "
                f"place them in one of {sorted(allowed)}",
            )
            return
        sub = segments[idx + 1]
        if sub not in allowed:
            emit(
                line,
                f"Sub-package '{sub}' under model is not allowed; "
                f"only {sorted(allowed)} are permitted",
            )


# --- vo ---------------------------------------------------------------------


class VoFieldSchemaRule(Rule):
    id = "vo/field-schema"

    def applies_to(self, ctx):
        return ctx.is_vo_package

    def check(self, ctx, cfg, emit):
        for _, field_decl in ctx.tree.filter(javalang.tree.FieldDeclaration):
            if not field_decl.declarators:
                continue
            line = node_line(field_decl) or 1
            for declarator in field_decl.declarators:
                if declarator.name in SKIPPABLE_FIELD_NAMES:
                    continue

                if has_leading_comment(ctx.lines, line):
                    emit(
                        line,
                        f"VO field {declarator.name} must not use plain comments; use @Schema",
                    )
                    continue

                anns = field_decl.annotations or []
                if not anns:
                    emit(
                        line,
                        f"VO field {declarator.name} must have @Schema annotation",
                    )
                    continue

                if annotation_name(anns[0]) != "Schema":
                    emit(
                        line,
                        f"@Schema must be the first annotation for VO field {declarator.name}",
                    )


# --- enum -------------------------------------------------------------------


class EnumBaseEnumRule(Rule):
    id = "enum/base-enum"
    requires_type_decl = False

    def applies_to(self, ctx):
        return ctx.is_enums_package

    def check(self, ctx, cfg, emit):
        for _, enum_decl in ctx.tree.filter(javalang.tree.EnumDeclaration):
            if any(
                type_name(impl) == "BaseEnum" for impl in enum_decl.implements or []
            ):
                continue
            line = node_line(enum_decl) or 1
            emit(line, f"Enum {enum_decl.name} must implement BaseEnum")


# --- converter --------------------------------------------------------------


class ConverterSingleParamRule(Rule):
    id = "converter/single-param"
    default_config = {"enabled": True, "maxParameters": 1}

    def applies_to(self, ctx):
        return ctx.is_converter_package

    def check(self, ctx, cfg, emit):
        max_params = cfg.get("maxParameters", 1)
        for _, method in ctx.tree.filter(javalang.tree.MethodDeclaration):
            actual = len(method.parameters or [])
            if actual > max_params:
                line = node_line(method) or 1
                emit(
                    line,
                    f"Converter methods may have at most {max_params} parameters (found {actual})",
                )


class ConverterNameFormatRule(Rule):
    id = "converter/name-format"
    default_config = {"enabled": True, "prefix": "to"}

    def applies_to(self, ctx):
        return ctx.is_converter_package

    def check(self, ctx, cfg, emit):
        prefix = cfg.get("prefix", "to")
        type_decl = ctx.type_decl
        for member in getattr(type_decl, "body", []) or []:
            if not isinstance(member, javalang.tree.MethodDeclaration):
                continue
            modifiers = member.modifiers or set()
            is_public = ctx.is_interface or ("public" in modifiers)
            if not is_public:
                continue
            if getattr(member, "constructor", False):
                continue
            name = getattr(member, "name", "") or ""
            if not name.startswith(prefix):
                line = node_line(member) or 1
                emit(line, f"Converter method {name} should start with '{prefix}'")


# --- service ----------------------------------------------------------------


class UseConverterRule(Rule):
    id = "vo/use-converter"

    def applies_to(self, ctx):
        return ctx.is_service_package and not ctx.is_test_file

    def check(self, ctx, cfg, emit):
        for _, method in ctx.tree.filter(javalang.tree.MethodDeclaration):
            if method.body is None:
                continue
            if self._uses_converter(method) or self._uses_wrapper_update(method):
                continue
            if self._uses_direct_construction(method):
                line = node_line(method) or 1
                emit(
                    line,
                    "Direct VO/entity construction detected; use a converter instead",
                )
                continue
            if not self._looks_like_manual_mapping(method):
                continue
            line = node_line(method) or 1
            emit(line, "Manual get/set mapping detected; use a converter instead")

    @staticmethod
    def _uses_converter(method):
        for _, invocation in method.filter(javalang.tree.MethodInvocation):
            qualifier = (
                invocation.qualifier if isinstance(invocation.qualifier, str) else ""
            )
            member = invocation.member or ""
            if "converter" in qualifier.lower() or "converter" in member.lower():
                return True
        for _, creator in method.filter(javalang.tree.ClassCreator):
            if "converter" in type_name(getattr(creator, "type", None)).lower():
                return True
        return False

    @staticmethod
    def _uses_wrapper_update(method):
        has_wrapper = any(
            type_name(getattr(creator, "type", None)) in WRAPPER_UPDATE_TYPES
            for _, creator in method.filter(javalang.tree.ClassCreator)
        )
        if not has_wrapper:
            return False
        for _, invocation in method.filter(javalang.tree.MethodInvocation):
            if (invocation.member or "").startswith("set"):
                return True
        return False

    @staticmethod
    def _uses_direct_construction(method):
        for _, creator in method.filter(javalang.tree.ClassCreator):
            tname = type_name(getattr(creator, "type", None))
            if not is_mapping_type(tname):
                continue
            if creator.arguments:
                continue
            return True
        return False

    @staticmethod
    def _looks_like_manual_mapping(method):
        setter_counts: Dict[str, int] = defaultdict(int)
        getter_sources: Set[str] = set()
        model_targets: Set[str] = set()

        for _, local_var in method.filter(javalang.tree.LocalVariableDeclaration):
            for declarator in local_var.declarators or []:
                init = getattr(declarator, "initializer", None)
                if not isinstance(init, javalang.tree.ClassCreator):
                    continue
                if is_mapping_type(type_name(getattr(init, "type", None))):
                    model_targets.add(declarator.name)

        for _, invocation in method.filter(javalang.tree.MethodInvocation):
            member = invocation.member or ""
            qualifier = (
                invocation.qualifier if isinstance(invocation.qualifier, str) else ""
            )
            if not qualifier or qualifier == "this":
                continue
            lower = member.lower()
            if lower.startswith("set") and (
                not model_targets or qualifier in model_targets
            ):
                setter_counts[qualifier] += 1
            elif (
                lower.startswith("get")
                or lower.startswith("is")
                or lower.startswith("has")
            ):
                getter_sources.add(qualifier)

        if not model_targets:
            return False
        total_setters = sum(setter_counts.values())
        if total_setters < 2:
            return False
        if not getter_sources and total_setters < 3:
            return False
        target_names = set(setter_counts)
        if not any(source not in target_names for source in getter_sources):
            return False
        return True


class ComplexLambdaRule(Rule):
    id = "service/complex-lambda"
    default_config = {"enabled": True, "maxWrapperChainLength": 5}

    def applies_to(self, ctx):
        return ctx.is_service_package

    def check(self, ctx, cfg, emit):
        max_chain = cfg.get("maxWrapperChainLength", 5)
        for _, method in ctx.tree.filter(javalang.tree.MethodDeclaration):
            if method.body is None:
                continue
            chain_lengths = [
                len(creator.selectors or [])
                for _, creator in method.filter(javalang.tree.ClassCreator)
                if type_name(getattr(creator, "type", None)) in WRAPPER_LAMBDA_TYPES
            ]
            if not chain_lengths:
                continue
            if max(chain_lengths) > max_chain:
                line = node_line(method) or 1
                emit(
                    line, "Long LambdaWrapper chains should be replaced with mapper XML"
                )


class ServiceInterfaceDocsRule(Rule):
    id = "service/interface-docs"

    def applies_to(self, ctx):
        return ctx.is_service_package and ctx.is_interface

    def check(self, ctx, cfg, emit):
        for _, method in ctx.tree.filter(javalang.tree.MethodDeclaration):
            line = node_line(method) or 1
            comment = extract_leading_comment(ctx.lines, line)
            if not comment:
                emit(line, f"Service interface method {method.name} is missing Javadoc")
                continue

            if not comment.strip().startswith("/**"):
                emit(
                    line,
                    f"Service interface method {method.name} must use Javadoc /** */ style",
                )

            for pname in (p.name for p in (method.parameters or [])):
                if f"@param {pname}" not in comment:
                    emit(line, f"Javadoc for {method.name} missing @param {pname}")

            return_type = getattr(method, "return_type", None)
            has_return = return_type is not None and type_name(return_type) != "void"
            if has_return and "@return" not in comment:
                emit(line, f"Javadoc for {method.name} missing @return")

            throws = getattr(method, "throws", None) or []
            if throws and "@throws" not in comment and "@exception" not in comment:
                emit(
                    line,
                    f"Javadoc for {method.name} missing @throws/@exception for declared exceptions",
                )


class ServicePublicMethodCommentRule(Rule):
    id = "service/public-method-comment"

    def applies_to(self, ctx):
        return ctx.is_service_package

    def check(self, ctx, cfg, emit):
        for _, method in ctx.tree.filter(javalang.tree.MethodDeclaration):
            if "public" not in (method.modifiers or set()):
                continue
            if method.name in COMMENT_FREE_PUBLIC_METHODS:
                continue
            anchor = node_line(method)
            if anchor is None:
                continue
            if has_leading_comment(ctx.lines, anchor):
                continue
            emit(anchor, f"Public method {method.name} is missing a comment")


# --- mapper -----------------------------------------------------------------


class MapperNoInlineSqlRule(Rule):
    id = "mapper/no-inline-sql"

    def applies_to(self, ctx):
        return ctx.is_mapper_package

    def check(self, ctx, cfg, emit):
        for _, method in ctx.tree.filter(javalang.tree.MethodDeclaration):
            for annotation in method.annotations or []:
                if annotation_name(annotation) not in SQL_ANNOTATIONS:
                    continue
                text = expression_text(annotation.element).upper()
                if not any(keyword in text for keyword in SQL_KEYWORDS):
                    continue
                line = node_line(annotation) or node_line(method) or 1
                emit(
                    line,
                    "Mapper classes must not contain inline SQL; use mapper XML instead",
                )
                break


# --- size -------------------------------------------------------------------


class FileLengthRule(Rule):
    id = "size/file-length"
    default_config = {"enabled": True, "maxLines": 1000}

    def check(self, ctx, cfg, emit):
        max_lines = cfg.get("maxLines", 0)
        actual = len(ctx.lines)
        if max_lines and actual > max_lines:
            emit(1, f"File length {actual} exceeds max {max_lines}")


class ClassLengthRule(Rule):
    id = "size/class-length"
    default_config = {"enabled": True, "maxLines": 300}

    def check(self, ctx, cfg, emit):
        max_lines = cfg.get("maxLines", 0)
        if not max_lines:
            return
        for _, type_decl in ctx.tree.filter(
            (
                javalang.tree.ClassDeclaration,
                javalang.tree.InterfaceDeclaration,
                javalang.tree.EnumDeclaration,
            )
        ):
            start = node_line(type_decl) or 1
            end = find_block_end_line(ctx.lines, start)
            length = end - start + 1
            if length > max_lines:
                emit(
                    start,
                    f"Type {type_decl.name} length {length} exceeds max {max_lines}",
                )


class MethodLengthRule(Rule):
    id = "size/method-length"
    default_config = {"enabled": True, "maxLines": 80}

    def check(self, ctx, cfg, emit):
        max_lines = cfg.get("maxLines", 0)
        if not max_lines:
            return
        for _, method in ctx.tree.filter(javalang.tree.MethodDeclaration):
            start = node_line(method) or 1
            end = start
            for _p, node in method.filter(javalang.tree.Node):
                pos = getattr(node, "position", None) or getattr(
                    node, "_position", None
                )
                if pos and getattr(pos, "line", None):
                    end = max(end, pos.line)
            end = max(end, find_block_end_line(ctx.lines, start))
            length = end - start + 1
            if length > max_lines:
                emit(
                    start,
                    f"Method {method.name} length {length} exceeds max {max_lines}",
                )


# --- duplication (project-wide) ---------------------------------------------


class DuplicationBlockRule(ProjectRule):
    id = "duplication/block"
    default_config = {"enabled": True, "minLines": 10}

    def check(self, files, cfg, emit):
        min_lines = cfg.get("minLines", 10)
        by_hash: Dict[str, List[Tuple[str, int, int, int]]] = defaultdict(list)

        for file_path, lines in files.items():
            if len(lines) < min_lines:
                continue
            for index in range(len(lines) - min_lines + 1):
                block = "\n".join(text for _, text in lines[index : index + min_lines])
                digest = hashlib.md5(block.encode("utf-8")).hexdigest()
                by_hash[digest].append(
                    (file_path, index, lines[index][0], lines[index + min_lines - 1][0])
                )

        covered: Dict[str, List[Tuple[int, int]]] = defaultdict(list)
        groups = [g for g in by_hash.values() if len(g) >= 2]
        groups.sort(key=lambda g: (-len(g), min(item[1] for item in g)))

        for occurrences in groups:
            if all(
                any(s <= idx <= e for s, e in covered[fp])
                for fp, idx, _, _ in occurrences
            ):
                continue

            siblings = [
                f"{os.path.relpath(fp, _project_root_for_emit(emit))}:{sl}-{el}"
                for fp, _, sl, el in occurrences
            ]

            for fp, idx, sl, el in occurrences:
                rel = os.path.relpath(fp, _project_root_for_emit(emit))
                others = [s for s in siblings if not s.startswith(f"{rel}:{sl}-")]
                msg = f"Duplicate code block ({min_lines} lines), see {', '.join(others) or 'same file'}"
                emit(fp, sl, msg)
                covered[fp].append((idx, idx + min_lines - 1))


def _project_root_for_emit(emit) -> str:
    """Project rules need to compute relative paths for sibling rendering.

    The emit closure carries the linter via attribute access set by the
    orchestrator; this helper hides that detail.
    """
    return getattr(emit, "project_root", "")


# --- sql --------------------------------------------------------------------


class SqlFileNamingRule(SqlRule):
    id = "sql/file-naming"
    default_config = {
        "enabled": True,
        "allowedFiles": ["jiayuan_boot.sql"],
        "allowedPatterns": ["migration_\\d{8}\\.sql"],
    }

    def check(self, sql_files, sql_dir, cfg, emit):
        allowed_files = cfg.get("allowedFiles", ["jiayuan_boot.sql"])
        allowed_patterns = cfg.get("allowedPatterns", ["migration_\\d{8}\\.sql"])

        for filename in sql_files:
            if filename in allowed_files:
                continue
            if any(self._matches_pattern(filename, p) for p in allowed_patterns):
                continue
            emit(
                os.path.join(sql_dir, filename),
                1,
                "The sql directory may only contain jiayuan_boot.sql and migration_{timestamp}.sql files.",
            )

    @staticmethod
    def _matches_pattern(filename, pattern):
        if pattern in {"migration_\\d{8}\\.sql", r"migration_\d{8}\.sql"}:
            prefix = "migration_"
            suffix = ".sql"
            if not filename.startswith(prefix) or not filename.endswith(suffix):
                return False
            digits = filename[len(prefix) : len(filename) - len(suffix)]
            return len(digits) == 8 and digits.isdigit()
        try:
            return PurePosixPath(filename).match(pattern)
        except Exception:
            return filename == pattern


class ShortMapperQueryRule(MapperXmlRule):
    id = "mapper/short-query"
    default_config = {"enabled": True, "maxWhereConditions": 2}

    def check(self, xml_files, cfg, emit):
        max_conditions = cfg.get("maxWhereConditions", 2)
        for file_path in xml_files:
            try:
                with open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
            except OSError:
                continue

            for line, sql in self._iter_select_sql_blocks(content):
                if not self._is_short_query(sql, max_conditions):
                    continue
                emit(
                    file_path,
                    line,
                    "Short mapper queries should be replaced with a LambdaWrapper or a built-in MyBatis-Plus mapper method",
                )

    @staticmethod
    def _iter_select_sql_blocks(content: str):
        pattern = re.compile(
            r"<select\b[^>]*>(.*?)</select>",
            re.IGNORECASE | re.DOTALL,
        )
        for match in pattern.finditer(content):
            block = match.group(1)
            line = content.count("\n", 0, match.start()) + 1
            yield line, block

    @staticmethod
    def _is_short_query(sql: str, max_conditions: int) -> bool:
        cleaned = re.sub(r"<!--.*?-->", " ", sql, flags=re.DOTALL)
        cleaned = re.sub(r"<[^>]+>", " ", cleaned)
        cleaned = re.sub(r"\s+", " ", cleaned).strip()
        upper = cleaned.upper()

        if not upper.startswith("SELECT "):
            return False
        if any(token in upper for token in (" JOIN ", " UNION ", " GROUP BY ", " HAVING ", " WITH ")):
            return False
        if any(re.search(rf"<\s*{tag}\b", sql, re.IGNORECASE) for tag in MAPPER_XML_RULE_TAGS):
            return False
        if " FROM " not in upper:
            return False

        where_match = re.search(r"\bWHERE\b(.*?)(\bORDER\s+BY\b|\bLIMIT\b|$)", upper, re.DOTALL)
        if not where_match:
            return False

        where_clause = where_match.group(1).strip()
        if not where_clause:
            return False
        if " OR " in where_clause:
            return False
        if re.search(r"\b(IN|EXISTS|LIKE|BETWEEN|CASE)\b", where_clause):
            return False

        conditions = [part.strip() for part in re.split(r"\bAND\b", where_clause) if part.strip()]
        if not conditions or len(conditions) > max_conditions:
            return False

        simple_condition = re.compile(
            r"^[A-Z0-9_$.]+\s*(=|<>|!=|<=|>=|<|>)\s*(#\{[^}]+\}|\?|NULL|TRUE|FALSE|'.*?'|\d+)$",
            re.DOTALL,
        )
        return all(simple_condition.match(condition) for condition in conditions)


# ---------------------------------------------------------------------------
# 9. Rule registry
# ---------------------------------------------------------------------------


JAVA_RULES: List[Rule] = [
    # naming
    VoSuffixRule(),
    NoDtoRule(),
    PackageNoMultiWordRule(),
    # api
    SummaryLengthRule(),
    SummaryBannedWordRule(),
    InterfaceIdNamingRule(),
    # model / vo / enum
    ModelFieldCommentRule(),
    EntityNoSchemaRule(),
    ModelPackageStructureRule(),
    VoFieldSchemaRule(),
    EnumBaseEnumRule(),
    # converter
    ConverterSingleParamRule(),
    ConverterNameFormatRule(),
    # service
    UseConverterRule(),
    ComplexLambdaRule(),
    ServiceInterfaceDocsRule(),
    ServicePublicMethodCommentRule(),
    # mapper
    MapperNoInlineSqlRule(),
    # size
    FileLengthRule(),
    ClassLengthRule(),
    MethodLengthRule(),
]

PROJECT_RULES: List[ProjectRule] = [DuplicationBlockRule()]

SQL_RULES: List[SqlRule] = [SqlFileNamingRule()]

MAPPER_XML_RULES: List[MapperXmlRule] = [ShortMapperQueryRule()]


# ---------------------------------------------------------------------------
# 10. BackendLinter (orchestrator)
# ---------------------------------------------------------------------------


class BackendLinter:
    def __init__(
        self,
        root_dir: str,
        config: dict,
        file_filter: Optional[Set[str]] = None,
    ):
        self.root_dir = os.path.abspath(root_dir)
        self.project_root = self._discover_project_root(self.root_dir)
        self.source_root = os.path.join(self.project_root, "src")
        self.sql_root = os.path.join(self.project_root, "sql", "mysql")
        self.mapper_xml_root = os.path.join(
            self.project_root, "src", "main", "resources", "mapper"
        )
        self.config = config
        self.ignore = IgnoreMatcher(self.project_root)
        self.defects: List[Defect] = []
        # Absolute paths of files to check; ``None`` means scan everything.
        self.file_filter = file_filter

    @staticmethod
    def _discover_project_root(start_dir: str) -> str:
        current = os.path.abspath(start_dir)
        if os.path.isfile(current):
            current = os.path.dirname(current)
        while True:
            if os.path.isdir(os.path.join(current, "src")) and os.path.isdir(
                os.path.join(current, "sql")
            ):
                return current
            parent = os.path.dirname(current)
            if parent == current:
                return os.path.abspath(start_dir)
            current = parent

    def scan(self) -> List[Defect]:
        files_norm: Dict[str, List[Tuple[int, str]]] = {}
        source_root = (
            self.source_root if os.path.isdir(self.source_root) else self.root_dir
        )

        if os.path.isdir(source_root):
            for root, _, files in os.walk(source_root):
                for filename in files:
                    if not filename.endswith(".java"):
                        continue
                    path = os.path.join(root, filename)
                    if not self._included(path):
                        continue
                    with open(path, "r", encoding="utf-8") as f:
                        content = f.read()
                    files_norm[path] = normalize_for_duplication(content)
                    self._check_java_file(path, content)

        self._run_project_rules(files_norm)

        if os.path.isdir(self.sql_root):
            self._run_sql_rules()

        if os.path.isdir(self.mapper_xml_root):
            self._run_mapper_xml_rules()

        return self.defects

    def _included(self, abs_path: str) -> bool:
        if self.file_filter is None:
            return True
        return os.path.abspath(abs_path) in self.file_filter

    # --- per-file Java rule execution -------------------------------------

    def _check_java_file(self, path: str, content: str) -> None:
        try:
            tree = javalang.parse.parse(content)
        except (javalang.parser.JavaSyntaxError, IndexError, TypeError) as exc:
            print(
                f"Warning: failed to parse {os.path.relpath(path, self.project_root)}: {exc}",
                file=sys.stderr,
            )
            return

        package_name = tree.package.name if tree.package else ""
        package_segments = {seg for seg in package_name.split(".") if seg}
        type_decl = first_type_declaration(tree)

        # Rules that only need the tree and not a primary type still run, so
        # we build a lightweight context even when ``type_decl`` is None.
        ctx_type_name = type_decl.name if type_decl is not None else ""
        ctx_type_line = (node_line(type_decl) if type_decl is not None else None) or 1
        is_interface = isinstance(type_decl, javalang.tree.InterfaceDeclaration)
        normalized_path = path.replace(os.sep, "/")
        is_test_file = "/test/" in normalized_path

        ctx = JavaFileContext(
            path=path,
            rel_path=os.path.relpath(path, self.project_root),
            content=content,
            lines=content.splitlines(),
            tree=tree,
            package_name=package_name,
            package_segments=package_segments,
            type_decl=type_decl,
            type_name=ctx_type_name,
            type_line=ctx_type_line,
            is_interface=is_interface,
            is_test_file=is_test_file,
        )

        for rule in JAVA_RULES:
            rule_cfg = self._rule_config(rule)
            if not rule_cfg.get("enabled", True):
                continue
            # Rules that need a primary type declaration are skipped when the
            # file has no class/interface (e.g. package-info.java).
            if type_decl is None and rule.requires_type_decl:
                continue
            if not rule.applies_to(ctx):
                continue
            emit = self._make_file_emit(rule.id, path)
            rule.check(ctx, rule_cfg, emit)

    # --- project-level rule execution -------------------------------------

    def _run_project_rules(self, files_norm: Dict[str, List[Tuple[int, str]]]) -> None:
        for rule in PROJECT_RULES:
            rule_cfg = self._rule_config(rule)
            if not rule_cfg.get("enabled", True):
                continue
            emit = self._make_location_emit(rule.id)
            rule.check(files_norm, rule_cfg, emit)

    # --- SQL rule execution -----------------------------------------------

    def _run_sql_rules(self) -> None:
        sql_files = [
            f
            for f in os.listdir(self.sql_root)
            if f.endswith(".sql")
            and self._included(os.path.join(self.sql_root, f))
        ]
        if not sql_files:
            return
        for rule in SQL_RULES:
            rule_cfg = self._rule_config(rule)
            if not rule_cfg.get("enabled", True):
                continue
            emit = self._make_location_emit(rule.id)
            rule.check(sql_files, self.sql_root, rule_cfg, emit)

    # --- mapper XML rule execution --------------------------------------

    def _run_mapper_xml_rules(self) -> None:
        xml_files: List[str] = []
        for root, _, files in os.walk(self.mapper_xml_root):
            for filename in files:
                if not filename.endswith(".xml"):
                    continue
                path = os.path.join(root, filename)
                if not self._included(path):
                    continue
                xml_files.append(path)

        if not xml_files:
            return

        for rule in MAPPER_XML_RULES:
            rule_cfg = self._rule_config(rule)
            if not rule_cfg.get("enabled", True):
                continue
            emit = self._make_location_emit(rule.id)
            rule.check(xml_files, rule_cfg, emit)

    # --- shared helpers ---------------------------------------------------

    def _rule_config(self, rule) -> dict:
        configured = self.config.get("rules", {}).get(rule.id, {})
        merged = dict(rule.default_config)
        merged.update(configured)
        return merged

    def _make_file_emit(self, rule_id: str, file_path: str) -> FileEmit:
        def emit(line: int, message: str) -> None:
            self._record(rule_id, file_path, line, message)

        return emit

    def _make_location_emit(self, rule_id: str) -> LocationEmit:
        def emit(file_path: str, line: int, message: str) -> None:
            self._record(rule_id, file_path, line, message)

        emit.project_root = self.project_root  # used by DuplicationBlockRule
        return emit

    def _record(self, rule_id: str, file_path: str, line: int, message: str) -> None:
        rel = os.path.relpath(file_path, self.project_root)
        if self.ignore.matches(rel, rule_id):
            return
        self.defects.append(
            Defect(
                file=rel,
                line=line,
                column=1,
                level="error",
                message=message,
                rule=rule_id,
            )
        )


# ---------------------------------------------------------------------------
# 11. Reporters (eslint-style + angry summary)
# ---------------------------------------------------------------------------


def print_report_eslint(defects: List[Defect], colors_enabled: bool) -> None:
    if not defects:
        if colors_enabled:
            print(f"\n{Colors.GREEN}{Colors.BOLD}No problems found.{Colors.RESET}\n")
        else:
            print("\nNo problems found.\n")
        return

    grouped: Dict[str, List[Defect]] = defaultdict(list)
    for defect in defects:
        grouped[defect.file].append(defect)

    for index, file in enumerate(sorted(grouped)):
        if index > 0:
            print()
        print(file)

        file_defects = sorted(
            grouped[file], key=lambda item: (item.line, item.column, item.rule)
        )
        location_width = max(len(f"{item.line}:{item.column}") for item in file_defects)

        for defect in file_defects:
            location = f"{defect.line}:{defect.column}".rjust(location_width)
            level = defect.level
            message = defect.message
            rule = defect.rule

            if colors_enabled:
                level_text = (
                    f"{Colors.RED}{level}{Colors.RESET}"
                    if level == "error"
                    else f"{Colors.YELLOW}{level}{Colors.RESET}"
                )
                rule_text = f"{Colors.DIM}{rule}{Colors.RESET}"
            else:
                level_text = level
                rule_text = rule

            print(f"  {location}  {level_text}  {message}  {rule_text}")

    total = len(defects)
    error_count = sum(1 for d in defects if d.level == "error")
    warning_count = total - error_count
    if colors_enabled:
        print(
            f"\n{Colors.RED}{Colors.BOLD}{total} problems{Colors.RESET} "
            f"({Colors.RED}{error_count} errors{Colors.RESET}, "
            f"{Colors.YELLOW}{warning_count} warnings{Colors.RESET})\n"
        )
    else:
        print(f"\n{total} problems ({error_count} errors, {warning_count} warnings)\n")


def print_report_angry(defects: List[Defect], colors_enabled: bool) -> None:
    """Angry mode: only the total count, never per-defect details."""
    total = len(defects)
    if total == 0:
        text = "No problems found."
        if colors_enabled:
            text = f"{Colors.GREEN}{Colors.BOLD}{text}{Colors.RESET}"
        print(f"\n{text}\n")
        return

    error_count = sum(1 for d in defects if d.level == "error")
    warning_count = total - error_count
    text = f"{total} problems ({error_count} errors, {warning_count} warnings)"
    if colors_enabled:
        text = f"{Colors.RED}{Colors.BOLD}{text}{Colors.RESET}"
    print(f"\n{text}\n")


REPORTERS: Dict[str, Callable[[List[Defect], bool], None]] = {
    "eslint": print_report_eslint,
    "angry": print_report_angry,
}


# ---------------------------------------------------------------------------
# 12. CLI entry point
# ---------------------------------------------------------------------------


if __name__ == "__main__":
    if sys.platform == "win32":
        os.system("")
        try:
            sys.stdout.reconfigure(encoding="utf-8")
            sys.stderr.reconfigure(encoding="utf-8")
        except AttributeError:
            pass

    args = _parse_cli_args(sys.argv[1:])
    config = load_config(args.project_root)

    output_cfg = config.get("output", {})
    scan_cfg = config.get("scan", {})

    mode = args.mode or scan_cfg.get("mode", "full")
    base_ref = args.base or scan_cfg.get("incrementalBaseRef", "master")

    file_filter: Optional[Set[str]] = None
    if mode == "incremental":
        project_root_abs = BackendLinter._discover_project_root(args.project_root)
        file_filter = discover_changed_files(project_root_abs, base_ref)
        if file_filter is None:
            print(
                "Error: incremental mode requested but git diff failed; aborting.",
                file=sys.stderr,
            )
            sys.exit(2)
        if not file_filter:
            # Nothing changed: succeed silently with 0 defects.
            print(
                f"\nIncremental mode vs '{base_ref}': no changed files; nothing to check.\n"
            )
            sys.exit(0)
    elif mode != "full":
        print(
            f"Error: unknown scan mode '{mode}' (expected 'full' or 'incremental').",
            file=sys.stderr,
        )
        sys.exit(2)

    linter = BackendLinter(args.project_root, config, file_filter=file_filter)
    defects = linter.scan()

    colors_enabled = output_cfg.get("colors", True)
    fmt = output_cfg.get("format", "eslint")
    reporter = REPORTERS.get(fmt, print_report_eslint)
    reporter(defects, colors_enabled)
    sys.exit(1 if defects else 0)
