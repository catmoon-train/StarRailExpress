#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""StarRailExpress language key manager.

The file-processing functions are intentionally independent from Tkinter so
they can be tested and reused from command-line tooling.
"""

from __future__ import annotations

import argparse
import difflib
import json
import os
import re
import shutil
import tempfile
import threading
from collections import Counter
from collections.abc import Iterable, Mapping
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any


DEFAULT_EXCLUDED_DIRECTORIES = frozenset(
    {
        ".git",
        ".gradle",
        ".idea",
        ".vscode",
        "__pycache__",
        "bin",
        "build",
        "dist",
        "out",
        "run",
    }
)
SECTION_MARKER_PATTERN = re.compile(r"^\s*={3,}.*={3,}\s*$")


class LanguageFileError(ValueError):
    """Raised when a language file cannot be safely processed."""


class DuplicateKeyError(LanguageFileError):
    """Raised when a JSON object contains duplicate keys."""


@dataclass(frozen=True)
class TextFormat:
    has_bom: bool = False
    newline: str = "\n"
    ends_with_newline: bool = True


@dataclass(frozen=True)
class LanguageFileRef:
    path: Path
    assets_root: Path
    namespace: str
    locale: str

    @property
    def display_name(self) -> str:
        return f"{self.namespace} / {self.locale}  [{self.path}]"


@dataclass
class LanguageDocument:
    ref: LanguageFileRef
    entries: dict[str, str]
    text_format: TextFormat
    original_text: str = ""
    duplicate_keys: tuple[str, ...] = ()
    conflicting_duplicate_keys: tuple[str, ...] = ()


@dataclass(frozen=True)
class ComparisonResult:
    missing: tuple[str, ...] = ()
    empty: tuple[str, ...] = ()
    same_as_primary: tuple[str, ...] = ()
    extra: tuple[str, ...] = ()

    def category_for(self, key: str) -> str | None:
        if key in self.missing:
            return "missing"
        if key in self.empty:
            return "empty"
        if key in self.same_as_primary:
            return "same"
        if key in self.extra:
            return "extra"
        return None


@dataclass(frozen=True)
class ExportOptions:
    include_primary_values: bool = True
    include_missing: bool = True
    include_empty: bool = False
    include_same_as_primary: bool = False


@dataclass(frozen=True)
class AggregateChoice:
    source: LanguageFileRef
    value: str

    @property
    def label(self) -> str:
        preview = self.value.replace("\n", r"\n")
        if len(preview) > 100:
            preview = preview[:97] + "..."
        return f"{self.source.namespace} | {preview}"


@dataclass(frozen=True)
class AggregateConflict:
    key: str
    choices: tuple[AggregateChoice, ...]


@dataclass
class AggregateResult:
    entries: dict[str, str]
    conflicts: dict[str, AggregateConflict] = field(default_factory=dict)


@dataclass(frozen=True)
class ImportMergeResult:
    entries: dict[str, str]
    applied: tuple[str, ...] = ()
    unchanged: tuple[str, ...] = ()
    unknown: tuple[str, ...] = ()
    skipped_empty: tuple[str, ...] = ()
    skipped_markers: tuple[str, ...] = ()


@dataclass(frozen=True)
class AITranslationConfig:
    api_key: str
    base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    model: str = "qwen-plus"
    batch_size: int = 30


def is_section_marker(key: str, value: str) -> bool:
    """Return True only for empty-valued, equals-delimited heading keys."""

    return value == "" and SECTION_MARKER_PATTERN.fullmatch(key) is not None


def language_file_ref(path: Path | str) -> LanguageFileRef:
    path = Path(path).resolve()
    if path.suffix.lower() != ".json" or path.parent.name.lower() != "lang":
        raise LanguageFileError(f"不是 lang 目录中的 JSON 文件: {path}")

    namespace_dir = path.parent.parent
    assets_root = namespace_dir.parent
    if assets_root.name.lower() != "assets":
        raise LanguageFileError(f"路径不符合 assets/<namespace>/lang/*.json: {path}")

    return LanguageFileRef(
        path=path,
        assets_root=assets_root,
        namespace=namespace_dir.name,
        locale=path.stem,
    )


def discover_language_files(
    root: Path | str,
    excluded_directories: Iterable[str] = DEFAULT_EXCLUDED_DIRECTORIES,
) -> list[LanguageFileRef]:
    """Discover source language files below root while pruning build outputs."""

    root = Path(root).resolve()
    excluded = {name.lower() for name in excluded_directories}
    discovered: list[LanguageFileRef] = []

    if root.is_file():
        return [language_file_ref(root)]
    if not root.is_dir():
        raise LanguageFileError(f"扫描目录不存在: {root}")

    for current_root, directory_names, file_names in os.walk(root):
        directory_names[:] = [
            name for name in directory_names if name.lower() not in excluded
        ]
        current = Path(current_root)
        if current.name.lower() != "lang":
            continue
        if current.parent.parent.name.lower() != "assets":
            continue

        for file_name in file_names:
            if not file_name.lower().endswith(".json"):
                continue
            discovered.append(language_file_ref(current / file_name))

    return sorted(
        discovered,
        key=lambda ref: (
            str(ref.assets_root).lower(),
            ref.namespace.lower(),
            ref.locale.lower(),
            str(ref.path).lower(),
        ),
    )


def _detect_text_format(raw: bytes) -> TextFormat:
    has_bom = raw.startswith(b"\xef\xbb\xbf")
    newline = "\r\n" if b"\r\n" in raw else "\n"
    return TextFormat(
        has_bom=has_bom,
        newline=newline,
        ends_with_newline=raw.endswith(b"\n"),
    )


def load_language_document(ref_or_path: LanguageFileRef | Path | str) -> LanguageDocument:
    ref = (
        ref_or_path
        if isinstance(ref_or_path, LanguageFileRef)
        else language_file_ref(ref_or_path)
    )
    try:
        raw = ref.path.read_bytes()
    except OSError as exc:
        raise LanguageFileError(f"无法读取 {ref.path}: {exc}") from exc

    text_format = _detect_text_format(raw)
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise LanguageFileError(f"文件不是有效 UTF-8: {ref.path}: {exc}") from exc

    duplicate_keys: list[str] = []
    conflicting_duplicate_keys: list[str] = []

    def collect_pairs(pairs: list[tuple[Any, Any]]) -> dict[Any, Any]:
        result: dict[Any, Any] = {}
        for key, value in pairs:
            if key in result:
                if isinstance(key, str) and key not in duplicate_keys:
                    duplicate_keys.append(key)
                if (
                    result[key] != value
                    and isinstance(key, str)
                    and key not in conflicting_duplicate_keys
                ):
                    conflicting_duplicate_keys.append(key)
                # Match normal JSON parsing behavior: the last value wins.
                result[key] = value
                continue
            result[key] = value
        return result

    try:
        data = json.loads(text, object_pairs_hook=collect_pairs)
    except DuplicateKeyError:
        raise
    except json.JSONDecodeError as exc:
        raise LanguageFileError(
            f"JSON 格式错误 {ref.path}（第 {exc.lineno} 行，第 {exc.colno} 列）: "
            f"{exc.msg}"
        ) from exc

    if not isinstance(data, dict):
        raise LanguageFileError(f"语言文件根节点必须是 JSON 对象: {ref.path}")
    for key, value in data.items():
        if not isinstance(key, str) or not isinstance(value, str):
            raise LanguageFileError(
                f"语言文件只允许字符串键和值: {ref.path}，问题键: {key!r}"
            )

    return LanguageDocument(
        ref=ref,
        entries=data,
        text_format=text_format,
        original_text=text,
        duplicate_keys=tuple(duplicate_keys),
        conflicting_duplicate_keys=tuple(conflicting_duplicate_keys),
    )


def compare_documents(
    primary: LanguageDocument, target: LanguageDocument
) -> ComparisonResult:
    primary_keys = [
        key
        for key, value in primary.entries.items()
        if not is_section_marker(key, value)
    ]
    target_keys = {
        key
        for key, value in target.entries.items()
        if not is_section_marker(key, value)
    }

    missing: list[str] = []
    empty: list[str] = []
    same: list[str] = []
    for key in primary_keys:
        if key not in target_keys:
            missing.append(key)
            continue
        target_value = target.entries[key]
        if target_value == "":
            empty.append(key)
        elif target_value == primary.entries[key]:
            same.append(key)

    primary_key_set = set(primary_keys)
    extra = [
        key
        for key, value in target.entries.items()
        if not is_section_marker(key, value) and key not in primary_key_set
    ]
    return ComparisonResult(
        missing=tuple(missing),
        empty=tuple(empty),
        same_as_primary=tuple(same),
        extra=tuple(extra),
    )


def _selected_export_keys(
    comparison: ComparisonResult, options: ExportOptions
) -> set[str]:
    selected: set[str] = set()
    if options.include_missing:
        selected.update(comparison.missing)
    if options.include_empty:
        selected.update(comparison.empty)
    if options.include_same_as_primary:
        selected.update(comparison.same_as_primary)
    return selected


def _marker_has_selected_key(
    items: list[tuple[str, str]], marker_index: int, selected: set[str]
) -> bool:
    for key, value in items[marker_index + 1 :]:
        if is_section_marker(key, value):
            break
        if key in selected:
            return True
    return False


def _keys_in_section(
    items: list[tuple[str, str]], marker_index: int
) -> set[str]:
    keys: set[str] = set()
    for key, value in items[marker_index + 1 :]:
        if is_section_marker(key, value):
            break
        keys.add(key)
    return keys


def build_translation_export(
    primary: LanguageDocument,
    comparison: ComparisonResult,
    options: ExportOptions = ExportOptions(),
) -> dict[str, str]:
    """Build an ordered translation export using the primary file's layout."""

    selected = _selected_export_keys(comparison, options)
    items = list(primary.entries.items())
    exported: dict[str, str] = {}
    for index, (key, value) in enumerate(items):
        if is_section_marker(key, value):
            if _marker_has_selected_key(items, index, selected):
                exported[key] = ""
            continue
        if key in selected:
            exported[key] = value if options.include_primary_values else ""
    return exported


def build_ai_translation_input(
    primary: LanguageDocument,
    comparison: ComparisonResult,
    options: ExportOptions,
) -> dict[str, str]:
    """Select primary values using the same category filters as export."""

    selected = _selected_export_keys(comparison, options)
    return {
        key: value
        for key, value in primary.entries.items()
        if key in selected and not is_section_marker(key, value)
    }


def organize_target_entries(
    primary: LanguageDocument, target: LanguageDocument
) -> dict[str, str]:
    """Order existing target keys by primary layout without adding missing keys."""

    target_non_markers = {
        key: value
        for key, value in target.entries.items()
        if not is_section_marker(key, value)
    }
    return _order_target_values(primary, target_non_markers)


def _order_target_values(
    primary: LanguageDocument, target_non_markers: Mapping[str, str]
) -> dict[str, str]:
    organized: dict[str, str] = {}
    primary_keys: set[str] = set()

    items = list(primary.entries.items())
    for index, (key, value) in enumerate(items):
        if is_section_marker(key, value):
            # The marker is useful only when this specific group has target keys.
            if _keys_in_section(items, index).intersection(target_non_markers):
                organized[key] = ""
            continue

        primary_keys.add(key)
        if key in target_non_markers:
            organized[key] = target_non_markers[key]

    for key, value in target_non_markers.items():
        if key not in primary_keys:
            organized[key] = value
    return organized


def synchronize_target_entries(
    primary: LanguageDocument, target: LanguageDocument
) -> dict[str, str]:
    """Add missing primary keys while preserving all existing target values."""

    synchronized: dict[str, str] = {}
    target_non_markers = {
        key: value
        for key, value in target.entries.items()
        if not is_section_marker(key, value)
    }
    primary_keys: set[str] = set()

    for key, value in primary.entries.items():
        if is_section_marker(key, value):
            synchronized[key] = ""
            continue
        primary_keys.add(key)
        synchronized[key] = target_non_markers.get(key, value)

    for key, value in target_non_markers.items():
        if key not in primary_keys:
            synchronized[key] = value
    return synchronized


def _extract_json_mapping(value: Any) -> dict[str, str] | None:
    if not isinstance(value, dict):
        return None
    if "translations" in value and isinstance(value["translations"], dict):
        value = value["translations"]
    if not all(isinstance(key, str) and isinstance(item, str) for key, item in value.items()):
        return None
    return dict(value)


def _json_mappings_from_text(text: str) -> Iterable[dict[str, str]]:
    stripped = text.strip()
    if not stripped:
        return

    candidates = [stripped]
    candidates.extend(
        match.group(1).strip()
        for match in re.finditer(
            r"```(?:json|JSON|text|txt)?\s*(.*?)```",
            stripped,
            flags=re.DOTALL,
        )
    )
    for candidate in candidates:
        try:
            parsed = json.loads(candidate)
        except json.JSONDecodeError:
            continue
        mapping = _extract_json_mapping(parsed)
        if mapping is not None:
            yield mapping

    decoder = json.JSONDecoder()
    for index, character in enumerate(stripped):
        if character != "{":
            continue
        try:
            parsed, _end = decoder.raw_decode(stripped[index:])
        except json.JSONDecodeError:
            continue
        mapping = _extract_json_mapping(parsed)
        if mapping is not None:
            yield mapping


def parse_translation_text(text: str) -> dict[str, str]:
    """Parse JSON, fenced JSON, or simple key/value translation text."""

    for mapping in _json_mappings_from_text(text):
        return mapping

    entries: dict[str, str] = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "//", ";")):
            continue
        line = line.removesuffix(",").strip()

        # This accepts JSON-style lines copied from an exported object.
        try:
            parsed_line = json.loads("{" + line + "}")
        except json.JSONDecodeError:
            parsed_line = None
        mapping = _extract_json_mapping(parsed_line)
        if mapping:
            entries.update(mapping)
            continue

        separator = "\t" if "\t" in line else "=" if "=" in line else None
        if separator is None and ":" in line:
            separator = ":"
        if separator is None:
            continue
        key_text, value_text = line.split(separator, 1)
        key_text = key_text.strip()
        value_text = value_text.strip()
        try:
            key = json.loads(key_text) if key_text.startswith('"') else key_text
            value = json.loads(value_text) if value_text.startswith('"') else value_text
        except json.JSONDecodeError:
            continue
        if isinstance(key, str) and isinstance(value, str) and key:
            entries[key] = value

    if not entries:
        raise LanguageFileError(
            "未识别到语言键。支持 JSON 对象、Markdown JSON 代码块，"
            "以及 key=value、key<Tab>value 文本。"
        )
    return entries


def merge_imported_translations(
    primary: LanguageDocument,
    target: LanguageDocument,
    imported: Mapping[str, str],
) -> ImportMergeResult:
    """Merge imported values for known primary keys and keep primary ordering."""

    primary_values = {
        key: value
        for key, value in primary.entries.items()
        if not is_section_marker(key, value)
    }
    target_values = {
        key: value
        for key, value in target.entries.items()
        if not is_section_marker(key, value)
    }
    applied: list[str] = []
    unchanged: list[str] = []
    unknown: list[str] = []
    skipped_empty: list[str] = []
    skipped_markers: list[str] = []

    for key, value in imported.items():
        if is_section_marker(key, value):
            skipped_markers.append(key)
            continue
        if key not in primary_values:
            unknown.append(key)
            continue
        if value == "":
            skipped_empty.append(key)
            continue
        if target_values.get(key) == value:
            unchanged.append(key)
            continue
        target_values[key] = value
        applied.append(key)

    ordered = _order_target_values(primary, target_values)
    return ImportMergeResult(
        entries=ordered,
        applied=tuple(applied),
        unchanged=tuple(unchanged),
        unknown=tuple(unknown),
        skipped_empty=tuple(skipped_empty),
        skipped_markers=tuple(skipped_markers),
    )


def _translation_batches(
    entries: Mapping[str, str], batch_size: int
) -> Iterable[dict[str, str]]:
    items = list(entries.items())
    for index in range(0, len(items), batch_size):
        yield dict(items[index : index + batch_size])


def _format_token_counts(value: str) -> Counter[str]:
    tokens: list[str] = []
    tokens.extend(
        re.findall(
            r"%(?:\d+\$)?[-+#0 ,(]*\d*(?:\.\d+)?(?:[a-zA-Z]|%)",
            value,
        )
    )
    tokens.extend(re.findall(r"\{[^{}\r\n]*\}", value))
    tokens.extend(re.findall(r"§.", value))
    tokens.extend(["<NEWLINE>"] * value.count("\n"))
    tokens.extend(["<ESCAPED_NEWLINE>"] * value.count(r"\n"))
    return Counter(tokens)


def _validate_ai_translations(
    source: Mapping[str, str],
    response: Mapping[str, str],
) -> tuple[dict[str, str], dict[str, str]]:
    valid: dict[str, str] = {}
    invalid: dict[str, str] = {}
    for key, source_value in source.items():
        if key not in response:
            invalid[key] = "返回中缺少此键"
            continue
        translated_value = response[key]
        if not translated_value.strip():
            invalid[key] = "返回值为空"
            continue
        source_tokens = _format_token_counts(source_value)
        translated_tokens = _format_token_counts(translated_value)
        if source_tokens != translated_tokens:
            invalid[key] = (
                f"占位符或格式码变化：原文 {dict(source_tokens)}，"
                f"译文 {dict(translated_tokens)}"
            )
            continue
        valid[key] = translated_value
    return valid, invalid


def translate_entries_with_openai(
    entries: Mapping[str, str],
    target_locale: str,
    config: AITranslationConfig,
    *,
    progress: Any = None,
    client_factory: Any = None,
) -> dict[str, str]:
    """Translate entries with an OpenAI-compatible chat completions API."""

    if not entries:
        return {}
    if not config.api_key.strip():
        raise LanguageFileError(
            "未配置 API Key。请填写 API Key 或设置 DASHSCOPE_API_KEY 环境变量。"
        )
    if not config.base_url.strip() or not config.model.strip():
        raise LanguageFileError("Base URL 和模型名称不能为空。")
    if config.batch_size < 1 or config.batch_size > 200:
        raise LanguageFileError("每批翻译数量必须在 1 到 200 之间。")

    if client_factory is None:
        try:
            from openai import OpenAI
        except ImportError as exc:
            raise LanguageFileError(
                "未安装 OpenAI Python SDK。请运行：python -m pip install openai"
            ) from exc
        client_factory = OpenAI

    client = client_factory(
        api_key=config.api_key.strip(),
        base_url=config.base_url.strip().rstrip("/"),
    )
    batches = list(_translation_batches(entries, config.batch_size))
    translated: dict[str, str] = {}

    system_message = (
        "你是 Minecraft 模组语言文件翻译器。将用户 JSON 对象中的值翻译为"
        f"目标语言代码 {target_locale} 对应的自然语言。键必须原样保留。"
        "严格保留所有格式占位符和控制内容，包括 %s、%d、%1$s、{0}、{}、"
        "\\n、转义字符、Minecraft § 格式码、命令、ID 和专有名词。"
        "不要翻译 JSON 键，不要增加或删除键，不要返回解释。"
        '仅返回 JSON：{"translations":{"原键":"翻译值"}}。'
    )

    def request_translation(
        requested_entries: Mapping[str, str],
        *,
        retry: bool = False,
    ) -> dict[str, str]:
        user_message = (
            (
                "上一次返回未通过校验。请重新翻译，任何值都不得为空，"
                "并严格保留占位符和格式码。\n"
                if retry
                else ""
            )
            + f"请将以下 JSON 的值翻译为 {target_locale}，并按要求返回 JSON：\n"
            + json.dumps(requested_entries, ensure_ascii=False)
        )
        try:
            completion = client.chat.completions.create(
                model=config.model.strip(),
                messages=[
                    {"role": "system", "content": system_message},
                    {"role": "user", "content": user_message},
                ],
                response_format={"type": "json_object"},
                temperature=0.2,
            )
            content = completion.choices[0].message.content
        except Exception as exc:
            raise LanguageFileError(f"AI 翻译请求失败: {exc}") from exc
        if not isinstance(content, str) or not content.strip():
            raise LanguageFileError("AI 返回了空内容。")

        try:
            response = json.loads(content)
        except json.JSONDecodeError as exc:
            raise LanguageFileError(f"AI 返回的内容不是有效 JSON: {exc}") from exc
        response_mapping = _extract_json_mapping(response)
        if response_mapping is None:
            raise LanguageFileError("AI 返回的 JSON 不包含有效的 translations 对象。")
        return response_mapping

    failed: dict[str, str] = {}
    for batch_index, batch in enumerate(batches, start=1):
        response_mapping = request_translation(batch)
        valid, invalid = _validate_ai_translations(batch, response_mapping)
        translated.update(valid)

        # Retry failed entries one by one so one bad value cannot discard a batch.
        for key, first_reason in invalid.items():
            retry_response = request_translation({key: batch[key]}, retry=True)
            retry_valid, retry_invalid = _validate_ai_translations(
                {key: batch[key]}, retry_response
            )
            if key in retry_valid:
                translated[key] = retry_valid[key]
            else:
                failed[key] = retry_invalid.get(key, first_reason)
        if progress is not None:
            progress(batch_index, len(batches), len(translated), len(entries))

    if failed and not translated:
        details = "\n".join(
            f"- {key}: {reason}" for key, reason in list(failed.items())[:20]
        )
        raise LanguageFileError(
            f"AI 翻译经过单项重试后仍有 {len(failed)} 个键失败：\n{details}"
        )
    return translated


def aggregate_documents(documents: Iterable[LanguageDocument]) -> AggregateResult:
    """Aggregate documents and report keys whose values disagree."""

    entries: dict[str, str] = {}
    choices_by_key: dict[str, list[AggregateChoice]] = {}

    for document in documents:
        for key, value in document.entries.items():
            choice = AggregateChoice(document.ref, value)
            if key not in entries:
                entries[key] = value
                choices_by_key[key] = [choice]
                continue
            if all(existing.value != value for existing in choices_by_key[key]):
                choices_by_key[key].append(choice)

    conflicts = {
        key: AggregateConflict(key=key, choices=tuple(choices))
        for key, choices in choices_by_key.items()
        if len(choices) > 1
    }
    return AggregateResult(entries=entries, conflicts=conflicts)


def resolve_aggregate(
    result: AggregateResult, resolutions: Mapping[str, AggregateChoice]
) -> dict[str, str]:
    unresolved = set(result.conflicts).difference(resolutions)
    if unresolved:
        sample = ", ".join(sorted(unresolved)[:5])
        raise LanguageFileError(f"仍有 {len(unresolved)} 个冲突未解决: {sample}")

    resolved = dict(result.entries)
    for key, conflict in result.conflicts.items():
        selected = resolutions[key]
        if selected not in conflict.choices:
            raise LanguageFileError(f"冲突 {key!r} 的选择不属于候选来源")
        resolved[key] = selected.value
    return resolved


def serialize_entries(entries: Mapping[str, str], text_format: TextFormat) -> bytes:
    text = json.dumps(dict(entries), ensure_ascii=False, indent=2)
    if text_format.newline != "\n":
        text = text.replace("\n", text_format.newline)
    if text_format.ends_with_newline:
        text += text_format.newline
    prefix = b"\xef\xbb\xbf" if text_format.has_bom else b""
    return prefix + text.encode("utf-8")


def _unique_backup_path(path: Path) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    candidate = path.with_name(f"{path.name}.bak.{timestamp}")
    counter = 1
    while candidate.exists():
        candidate = path.with_name(f"{path.name}.bak.{timestamp}.{counter}")
        counter += 1
    return candidate


def atomic_write_entries(
    path: Path | str,
    entries: Mapping[str, str],
    text_format: TextFormat,
    *,
    create_backup: bool = True,
) -> Path | None:
    """Atomically write entries and optionally return an adjacent backup path."""

    path = Path(path).resolve()
    path.parent.mkdir(parents=True, exist_ok=True)
    backup_path: Path | None = None

    if path.exists() and create_backup:
        backup_path = _unique_backup_path(path)
        shutil.copy2(path, backup_path)

    payload = serialize_entries(entries, text_format)
    temp_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="wb",
            prefix=f".{path.name}.",
            suffix=".tmp",
            dir=path.parent,
            delete=False,
        ) as temporary:
            temp_path = Path(temporary.name)
            temporary.write(payload)
            temporary.flush()
            os.fsync(temporary.fileno())

        if path.exists():
            temp_path.chmod(path.stat().st_mode)
        os.replace(temp_path, path)
        temp_path = None
    except OSError as exc:
        raise LanguageFileError(f"无法写入 {path}: {exc}") from exc
    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)

    return backup_path


def unified_diff_for_entries(
    original: LanguageDocument, updated_entries: Mapping[str, str]
) -> str:
    old_text = original.original_text
    if not old_text:
        old_text = serialize_entries(original.entries, original.text_format).decode(
            "utf-8-sig"
        )
    new_text = serialize_entries(updated_entries, original.text_format).decode(
        "utf-8-sig"
    )
    return "".join(
        difflib.unified_diff(
            old_text.splitlines(keepends=True),
            new_text.splitlines(keepends=True),
            fromfile=str(original.ref.path),
            tofile=str(original.ref.path),
        )
    )


def scan_and_validate(root: Path | str) -> tuple[list[LanguageFileRef], list[str]]:
    refs = discover_language_files(root)
    errors: list[str] = []
    for ref in refs:
        try:
            load_language_document(ref)
        except LanguageFileError as exc:
            errors.append(str(exc))
    return refs, errors


class LanguageManagerApp:
    """Tkinter front end for the language file operations."""

    CATEGORY_LABELS = {
        "missing": "缺失",
        "empty": "空值",
        "same": "与主语言同值",
        "extra": "目标独有",
        "translated": "已翻译",
    }

    def __init__(self, root: Any, project_root: Path) -> None:
        import tkinter as tk
        from tkinter import ttk

        self.tk = tk
        self.ttk = ttk
        self.root = root
        self.project_root = project_root.resolve()
        self.refs: list[LanguageFileRef] = []
        self.ref_by_display: dict[str, LanguageFileRef] = {}
        self.primary_document: LanguageDocument | None = None
        self.target_document: LanguageDocument | None = None
        self.comparison: ComparisonResult | None = None

        root.title("StarRailExpress 语言键管理器")
        root.geometry("1180x760")
        root.minsize(980, 640)

        self.scan_root_var = tk.StringVar(value=str(self.project_root))
        self.primary_var = tk.StringVar()
        self.target_var = tk.StringVar()
        self.status_var = tk.StringVar(value="准备就绪")
        self.include_primary_values_var = tk.BooleanVar(value=True)
        self.export_missing_var = tk.BooleanVar(value=True)
        self.export_empty_var = tk.BooleanVar(value=False)
        self.export_same_var = tk.BooleanVar(value=False)
        self.aggregate_locale_var = tk.StringVar()
        self.ai_api_key_var = tk.StringVar(value=os.getenv("DASHSCOPE_API_KEY", ""))
        self.ai_base_url_var = tk.StringVar(
            value="https://dashscope.aliyuncs.com/compatible-mode/v1"
        )
        self.ai_model_var = tk.StringVar(value="qwen-plus")
        self.ai_batch_size_var = tk.StringVar(value="30")
        self.ai_config_status_var = tk.StringVar(
            value=(
                "已从 DASHSCOPE_API_KEY 读取 API Key"
                if self.ai_api_key_var.get()
                else "未检测到 DASHSCOPE_API_KEY，可在下方临时填写"
            )
        )
        self.ai_busy = False

        self._build_ui()
        self.scan_files()

    def _build_ui(self) -> None:
        from tkinter import ttk

        outer = ttk.Frame(self.root, padding=10)
        outer.pack(fill="both", expand=True)

        scan_frame = ttk.LabelFrame(outer, text="扫描范围", padding=8)
        scan_frame.pack(fill="x")
        ttk.Entry(scan_frame, textvariable=self.scan_root_var).pack(
            side="left", fill="x", expand=True
        )
        ttk.Button(scan_frame, text="选择目录", command=self.choose_scan_root).pack(
            side="left", padx=(8, 0)
        )
        ttk.Button(scan_frame, text="重新扫描", command=self.scan_files).pack(
            side="left", padx=(8, 0)
        )
        ttk.Button(scan_frame, text="添加语言文件", command=self.add_language_file).pack(
            side="left", padx=(8, 0)
        )

        notebook = ttk.Notebook(outer)
        notebook.pack(fill="both", expand=True, pady=(10, 6))
        compare_tab = ttk.Frame(notebook, padding=10)
        ai_tab = ttk.Frame(notebook, padding=10)
        aggregate_tab = ttk.Frame(notebook, padding=10)
        notebook.add(compare_tab, text="比较、导出与同步")
        notebook.add(ai_tab, text="AI 翻译设置")
        notebook.add(aggregate_tab, text="跨命名空间汇总")
        self._build_compare_tab(compare_tab)
        self._build_ai_tab(ai_tab)
        self._build_aggregate_tab(aggregate_tab)

        ttk.Label(outer, textvariable=self.status_var, anchor="w").pack(fill="x")

    def _build_compare_tab(self, parent: Any) -> None:
        from tkinter import ttk

        selection = ttk.LabelFrame(parent, text="语言文件", padding=8)
        selection.pack(fill="x")
        ttk.Label(selection, text="主语言：").grid(row=0, column=0, sticky="w")
        self.primary_combo = ttk.Combobox(
            selection, textvariable=self.primary_var, state="readonly"
        )
        self.primary_combo.grid(row=0, column=1, sticky="ew", padx=(6, 8))
        self.primary_combo.bind("<<ComboboxSelected>>", self.on_primary_selected)
        ttk.Label(selection, text="目标语言：").grid(row=1, column=0, sticky="w", pady=(8, 0))
        self.target_combo = ttk.Combobox(
            selection, textvariable=self.target_var, state="readonly"
        )
        self.target_combo.grid(
            row=1, column=1, sticky="ew", padx=(6, 8), pady=(8, 0)
        )
        self.target_combo.bind("<<ComboboxSelected>>", self.on_target_selected)
        ttk.Button(selection, text="比较", command=self.compare_selected).grid(
            row=0, column=2, rowspan=2, sticky="ns"
        )
        selection.columnconfigure(1, weight=1)

        summary = ttk.Frame(parent)
        summary.pack(fill="x", pady=(8, 4))
        self.summary_label = ttk.Label(summary, text="请选择主语言和目标语言")
        self.summary_label.pack(side="left")

        table_frame = ttk.Frame(parent)
        table_frame.pack(fill="both", expand=True, pady=(0, 8))
        columns = ("status", "key", "primary", "target")
        self.comparison_tree = ttk.Treeview(
            table_frame, columns=columns, show="headings", selectmode="extended"
        )
        self.comparison_tree.heading("status", text="状态")
        self.comparison_tree.heading("key", text="语言键")
        self.comparison_tree.heading("primary", text="主语言值")
        self.comparison_tree.heading("target", text="目标语言值")
        self.comparison_tree.column("status", width=105, stretch=False)
        self.comparison_tree.column("key", width=330)
        self.comparison_tree.column("primary", width=300)
        self.comparison_tree.column("target", width=300)
        tree_scroll = ttk.Scrollbar(
            table_frame, orient="vertical", command=self.comparison_tree.yview
        )
        self.comparison_tree.configure(yscrollcommand=tree_scroll.set)
        self.comparison_tree.pack(side="left", fill="both", expand=True)
        tree_scroll.pack(side="right", fill="y")

        actions = ttk.LabelFrame(parent, text="操作", padding=8)
        actions.pack(fill="x")
        export_options = ttk.Frame(actions)
        export_options.pack(fill="x")
        ttk.Checkbutton(
            export_options, text="缺失键", variable=self.export_missing_var
        ).pack(side="left")
        ttk.Checkbutton(
            export_options, text="空值键", variable=self.export_empty_var
        ).pack(side="left", padx=(10, 0))
        ttk.Checkbutton(
            export_options,
            text="与主语言同值",
            variable=self.export_same_var,
        ).pack(side="left", padx=(10, 0))
        ttk.Checkbutton(
            export_options,
            text="包含主语言原文（方便翻译）",
            variable=self.include_primary_values_var,
        ).pack(side="left", padx=(18, 0))

        buttons = ttk.Frame(actions)
        buttons.pack(fill="x", pady=(8, 0))
        ttk.Button(buttons, text="导出勾选类别", command=self.export_untranslated).pack(
            side="left"
        )
        ttk.Button(buttons, text="一键排序整理", command=self.organize_target).pack(
            side="left", padx=(8, 0)
        )
        ttk.Button(buttons, text="同步合并缺失键", command=self.synchronize_target).pack(
            side="left", padx=(8, 0)
        )
        ttk.Button(
            buttons, text="导入翻译并合并", command=self.import_translations
        ).pack(side="left", padx=(8, 0))
        self.ai_translate_button = ttk.Button(
            buttons, text="AI 翻译选中键", command=self.translate_selected_with_ai
        )
        self.ai_translate_button.pack(side="left", padx=(8, 0))
        self.ai_translate_filtered_button = ttk.Button(
            buttons,
            text="AI 翻译勾选类别",
            command=self.translate_filtered_with_ai,
        )
        self.ai_translate_filtered_button.pack(side="left", padx=(8, 0))

    def _build_ai_tab(self, parent: Any) -> None:
        from tkinter import ttk

        ttk.Label(
            parent,
            text=(
                "配置 OpenAI 兼容接口。API Key 只保存在当前进程内，"
                "不会写入项目文件。"
            ),
            justify="left",
        ).pack(anchor="w")

        form = ttk.LabelFrame(parent, text="阿里云百炼 / OpenAI 兼容配置", padding=12)
        form.pack(fill="x", pady=(12, 0))
        ttk.Label(form, text="API Key：").grid(row=0, column=0, sticky="w")
        ttk.Entry(form, textvariable=self.ai_api_key_var, show="*").grid(
            row=0, column=1, sticky="ew", padx=(8, 0)
        )
        ttk.Label(form, text="Base URL：").grid(
            row=1, column=0, sticky="w", pady=(10, 0)
        )
        ttk.Entry(form, textvariable=self.ai_base_url_var).grid(
            row=1, column=1, sticky="ew", padx=(8, 0), pady=(10, 0)
        )
        ttk.Label(form, text="模型：").grid(
            row=2, column=0, sticky="w", pady=(10, 0)
        )
        ttk.Entry(form, textvariable=self.ai_model_var).grid(
            row=2, column=1, sticky="ew", padx=(8, 0), pady=(10, 0)
        )
        ttk.Label(form, text="每批键数：").grid(
            row=3, column=0, sticky="w", pady=(10, 0)
        )
        ttk.Spinbox(
            form,
            from_=1,
            to=200,
            textvariable=self.ai_batch_size_var,
            width=10,
        ).grid(row=3, column=1, sticky="w", padx=(8, 0), pady=(10, 0))
        form.columnconfigure(1, weight=1)

        ttk.Label(
            parent,
            textvariable=self.ai_config_status_var,
        ).pack(anchor="w", pady=(10, 0))
        ttk.Label(
            parent,
            text=(
                "首次使用请安装 SDK：python -m pip install openai\n"
                "使用流程：可以在“比较”页选择一行或多行后翻译选中键，"
                "也可以按缺失键、空值键、与主语言同值三个勾选类别批量翻译。"
            ),
            justify="left",
        ).pack(anchor="w", pady=(12, 0))

    def _build_aggregate_tab(self, parent: Any) -> None:
        from tkinter import ttk

        controls = ttk.Frame(parent)
        controls.pack(fill="x")
        ttk.Label(controls, text="语言代码：").pack(side="left")
        self.aggregate_locale_combo = ttk.Combobox(
            controls,
            textvariable=self.aggregate_locale_var,
            state="readonly",
            width=18,
        )
        self.aggregate_locale_combo.pack(side="left", padx=(6, 8))
        self.aggregate_locale_combo.bind(
            "<<ComboboxSelected>>", self.refresh_aggregate_files
        )
        ttk.Button(
            controls, text="刷新文件列表", command=self.refresh_aggregate_files
        ).pack(side="left")

        ttk.Label(
            parent,
            text="选择参与汇总的命名空间文件（Ctrl/Shift 可多选）：",
        ).pack(anchor="w", pady=(12, 4))
        body = ttk.Frame(parent)
        body.pack(fill="both", expand=True)
        list_frame = ttk.Frame(body)
        list_frame.pack(side="left", fill="both", expand=True)
        self.aggregate_listbox = self.tk.Listbox(
            list_frame, selectmode="extended", exportselection=False
        )
        aggregate_scroll = ttk.Scrollbar(
            list_frame, orient="vertical", command=self.aggregate_listbox.yview
        )
        self.aggregate_listbox.configure(yscrollcommand=aggregate_scroll.set)
        self.aggregate_listbox.pack(side="left", fill="both", expand=True)
        aggregate_scroll.pack(side="left", fill="y")

        side = ttk.Frame(body, padding=(10, 0, 0, 0))
        side.pack(side="right", fill="y")
        ttk.Button(side, text="全选", command=self.select_all_aggregate).pack(fill="x")
        ttk.Button(side, text="清除选择", command=self.clear_aggregate_selection).pack(
            fill="x", pady=(8, 0)
        )
        ttk.Button(side, text="汇总并导出", command=self.aggregate_and_export).pack(
            fill="x", pady=(24, 0)
        )

    def choose_scan_root(self) -> None:
        from tkinter import filedialog

        selected = filedialog.askdirectory(
            title="选择项目或源码目录", initialdir=self.scan_root_var.get()
        )
        if selected:
            self.scan_root_var.set(selected)
            self.scan_files()

    def add_language_file(self) -> None:
        from tkinter import filedialog, messagebox

        selected = filedialog.askopenfilename(
            title="添加语言文件",
            filetypes=[("JSON 语言文件", "*.json"), ("所有文件", "*.*")],
        )
        if not selected:
            return
        try:
            ref = language_file_ref(selected)
            load_language_document(ref)
        except LanguageFileError as exc:
            messagebox.showerror("无法添加", str(exc), parent=self.root)
            return
        if ref not in self.refs:
            self.refs.append(ref)
            self.refs.sort(key=lambda item: item.display_name.lower())
        self._refresh_file_choices()
        self.status_var.set(f"已添加：{ref.path}")

    def scan_files(self) -> None:
        from tkinter import messagebox

        try:
            refs, errors = scan_and_validate(self.scan_root_var.get())
        except LanguageFileError as exc:
            messagebox.showerror("扫描失败", str(exc), parent=self.root)
            return
        self.refs = refs
        self._refresh_file_choices()
        if errors:
            messagebox.showwarning(
                "扫描完成但存在错误",
                "\n\n".join(errors[:10]),
                parent=self.root,
            )
        self.status_var.set(
            f"发现 {len(refs)} 个语言文件"
            + (f"，其中 {len(errors)} 个无法加载" if errors else "")
        )

    def _refresh_file_choices(self) -> None:
        self.ref_by_display = {ref.display_name: ref for ref in self.refs}
        displays = list(self.ref_by_display)
        self.primary_combo["values"] = displays
        locales = sorted({ref.locale for ref in self.refs})
        self.aggregate_locale_combo["values"] = locales

        preferred = next(
            (
                ref.display_name
                for ref in self.refs
                if ref.locale == "zh_cn" and ref.namespace == "starrailexpress"
            ),
            displays[0] if displays else "",
        )
        if self.primary_var.get() not in self.ref_by_display:
            self.primary_var.set(preferred)
        if locales and self.aggregate_locale_var.get() not in locales:
            self.aggregate_locale_var.set(
                "zh_cn" if "zh_cn" in locales else locales[0]
            )
        self.on_primary_selected()
        self.refresh_aggregate_files()

    def on_primary_selected(self, _event: Any = None) -> None:
        display = self.primary_var.get()
        ref = self.ref_by_display.get(display)
        self.primary_document = None
        self.target_document = None
        self.comparison = None
        if ref is None:
            self.target_combo["values"] = ()
            self.target_var.set("")
            return

        targets = [
            candidate
            for candidate in self.refs
            if candidate.path.parent == ref.path.parent and candidate.path != ref.path
        ]
        target_displays = [candidate.display_name for candidate in targets]
        self.target_combo["values"] = target_displays
        if self.target_var.get() not in target_displays:
            self.target_var.set(target_displays[0] if target_displays else "")
        self._clear_comparison_view()

    def on_target_selected(self, _event: Any = None) -> None:
        self.target_document = None
        self.comparison = None
        self._clear_comparison_view()

    def _load_selected_documents(
        self,
    ) -> tuple[LanguageDocument, LanguageDocument] | None:
        from tkinter import messagebox

        primary_ref = self.ref_by_display.get(self.primary_var.get())
        target_ref = self.ref_by_display.get(self.target_var.get())
        if primary_ref is None or target_ref is None:
            messagebox.showwarning(
                "未选择文件", "请选择主语言和目标语言文件。", parent=self.root
            )
            return None
        try:
            primary = load_language_document(primary_ref)
            target = load_language_document(target_ref)
        except LanguageFileError as exc:
            messagebox.showerror("加载失败", str(exc), parent=self.root)
            return None
        self.primary_document = primary
        self.target_document = target
        return primary, target

    def _clear_comparison_view(self) -> None:
        for item in self.comparison_tree.get_children():
            self.comparison_tree.delete(item)
        self.summary_label.configure(text="点击“比较”查看语言键状态")

    @staticmethod
    def _preview(value: str) -> str:
        value = value.replace("\n", r"\n")
        return value if len(value) <= 160 else value[:157] + "..."

    def compare_selected(self) -> None:
        loaded = self._load_selected_documents()
        if loaded is None:
            return
        primary, target = loaded
        comparison = compare_documents(primary, target)
        self.comparison = comparison
        self._clear_comparison_view()

        for key, primary_value in primary.entries.items():
            if is_section_marker(key, primary_value):
                continue
            category = comparison.category_for(key) or "translated"
            self.comparison_tree.insert(
                "",
                "end",
                values=(
                    self.CATEGORY_LABELS[category],
                    key,
                    self._preview(primary_value),
                    self._preview(target.entries.get(key, "")),
                ),
            )
        for key in comparison.extra:
            self.comparison_tree.insert(
                "",
                "end",
                values=(
                    self.CATEGORY_LABELS["extra"],
                    key,
                    "",
                    self._preview(target.entries[key]),
                ),
            )

        self.summary_label.configure(
            text=(
                f"缺失 {len(comparison.missing)} | 空值 {len(comparison.empty)} | "
                f"与主语言同值 {len(comparison.same_as_primary)} | "
                f"目标独有 {len(comparison.extra)}"
                + (
                    f" | 重复键 "
                    f"{len(primary.duplicate_keys) + len(target.duplicate_keys)}"
                    if primary.duplicate_keys or target.duplicate_keys
                    else ""
                )
                + (
                    f"（其中不同值 "
                    f"{len(primary.conflicting_duplicate_keys) + len(target.conflicting_duplicate_keys)}，"
                    "末项生效）"
                    if primary.conflicting_duplicate_keys
                    or target.conflicting_duplicate_keys
                    else ""
                )
            )
        )
        duplicate_count = len(primary.duplicate_keys) + len(target.duplicate_keys)
        conflict_count = len(primary.conflicting_duplicate_keys) + len(
            target.conflicting_duplicate_keys
        )
        self.status_var.set(
            "比较完成"
            + (
                f"；已折叠 {duplicate_count} 个重复键"
                if duplicate_count
                else ""
            )
            + (f"，其中 {conflict_count} 个采用最后一项的值" if conflict_count else "")
        )

    def _ensure_comparison(
        self,
    ) -> tuple[LanguageDocument, LanguageDocument, ComparisonResult] | None:
        if (
            self.primary_document is None
            or self.target_document is None
            or self.comparison is None
        ):
            self.compare_selected()
        if (
            self.primary_document is None
            or self.target_document is None
            or self.comparison is None
        ):
            return None
        return self.primary_document, self.target_document, self.comparison

    def export_untranslated(self) -> None:
        from tkinter import filedialog, messagebox

        state = self._ensure_comparison()
        if state is None:
            return
        primary, target, comparison = state
        options = ExportOptions(
            include_primary_values=self.include_primary_values_var.get(),
            include_missing=self.export_missing_var.get(),
            include_empty=self.export_empty_var.get(),
            include_same_as_primary=self.export_same_var.get(),
        )
        entries = build_translation_export(primary, comparison, options)
        if not entries:
            messagebox.showinfo(
                "没有可导出的键", "当前勾选类别没有匹配的语言键。", parent=self.root
            )
            return

        suggested = f"{target.ref.locale}_待翻译.json"
        destination = filedialog.asksaveasfilename(
            title="导出待翻译语言键",
            initialdir=str(target.ref.path.parent),
            initialfile=suggested,
            defaultextension=".json",
            filetypes=[("JSON 文件", "*.json")],
        )
        if not destination:
            return
        preview_format = TextFormat(
            has_bom=False,
            newline="\n",
            ends_with_newline=True,
        )
        preview = serialize_entries(entries, preview_format).decode("utf-8")
        if not self._show_text_preview(
            "导出待翻译语言键",
            (
                f"将导出 {len(entries)} 项到：\n{destination}\n\n"
                "请确认导出内容："
            ),
            preview,
            confirm_text="确认导出",
        ):
            return
        try:
            backup = atomic_write_entries(
                destination,
                entries,
                primary.text_format,
                create_backup=True,
            )
        except LanguageFileError as exc:
            messagebox.showerror("导出失败", str(exc), parent=self.root)
            return
        message = f"已导出 {len(entries)} 项到：\n{destination}"
        if backup:
            message += f"\n\n原文件备份：\n{backup}"
        messagebox.showinfo("导出完成", message, parent=self.root)
        self.status_var.set(message.splitlines()[0])

    def organize_target(self) -> None:
        loaded = self._load_selected_documents()
        if loaded is None:
            return
        primary, target = loaded
        entries = organize_target_entries(primary, target)
        self._preview_and_save(target, entries, "排序整理")

    def synchronize_target(self) -> None:
        loaded = self._load_selected_documents()
        if loaded is None:
            return
        primary, target = loaded
        entries = synchronize_target_entries(primary, target)
        self._preview_and_save(target, entries, "同步合并")

    def import_translations(self) -> None:
        from tkinter import filedialog, messagebox

        loaded = self._load_selected_documents()
        if loaded is None:
            return
        primary, target = loaded
        selected = filedialog.askopenfilename(
            title="导入已翻译的语言键",
            initialdir=str(target.ref.path.parent),
            filetypes=[
                ("JSON 或文本", "*.json *.txt"),
                ("JSON 文件", "*.json"),
                ("文本文件", "*.txt"),
                ("所有文件", "*.*"),
            ],
        )
        if not selected:
            return
        try:
            raw = Path(selected).read_bytes()
            text = raw.decode("utf-8-sig")
            imported = parse_translation_text(text)
            result = merge_imported_translations(primary, target, imported)
        except (OSError, UnicodeDecodeError, LanguageFileError) as exc:
            messagebox.showerror("导入失败", str(exc), parent=self.root)
            return

        summary = (
            f"读取 {len(imported)} 个键；将合并 {len(result.applied)} 个，"
            f"未变化 {len(result.unchanged)} 个，跳过空值 {len(result.skipped_empty)} 个，"
            f"跳过标题 {len(result.skipped_markers)} 个，"
            f"未知键 {len(result.unknown)} 个。"
        )
        if not result.applied:
            messagebox.showinfo("没有可合并内容", summary, parent=self.root)
            return
        self._preview_and_save(target, result.entries, "导入翻译合并", summary)

    def _selected_primary_entries(
        self, primary: LanguageDocument
    ) -> dict[str, str]:
        selected: dict[str, str] = {}
        for item_id in self.comparison_tree.selection():
            values = self.comparison_tree.item(item_id, "values")
            if len(values) < 2:
                continue
            key = str(values[1])
            value = primary.entries.get(key)
            if value is None or is_section_marker(key, value):
                continue
            selected[key] = value
        return selected

    def translate_selected_with_ai(self) -> None:
        from tkinter import messagebox

        state = self._ensure_comparison()
        if state is None:
            return
        primary, target, _comparison = state
        selected_entries = self._selected_primary_entries(primary)
        if not selected_entries:
            messagebox.showwarning(
                "未选择语言键",
                "请先在比较表格中选择一个或多个主语言键。",
                parent=self.root,
            )
            return
        self._start_ai_translation(
            primary,
            target,
            selected_entries,
            "表格中手动选择的语言键",
        )

    def translate_filtered_with_ai(self) -> None:
        from tkinter import messagebox

        state = self._ensure_comparison()
        if state is None:
            return
        primary, target, comparison = state
        options = ExportOptions(
            include_primary_values=True,
            include_missing=self.export_missing_var.get(),
            include_empty=self.export_empty_var.get(),
            include_same_as_primary=self.export_same_var.get(),
        )
        filtered_entries = build_ai_translation_input(
            primary, comparison, options
        )
        if not filtered_entries:
            messagebox.showinfo(
                "没有可翻译的键",
                "当前勾选类别没有匹配的语言键。",
                parent=self.root,
            )
            return
        category_labels: list[str] = []
        if options.include_missing:
            category_labels.append("缺失键")
        if options.include_empty:
            category_labels.append("空值键")
        if options.include_same_as_primary:
            category_labels.append("与主语言同值")
        self._start_ai_translation(
            primary,
            target,
            filtered_entries,
            "勾选类别：" + "、".join(category_labels),
        )

    def _set_ai_busy(self, busy: bool) -> None:
        self.ai_busy = busy
        state = "disabled" if busy else "normal"
        self.ai_translate_button.configure(state=state)
        self.ai_translate_filtered_button.configure(state=state)

    def _start_ai_translation(
        self,
        primary: LanguageDocument,
        target: LanguageDocument,
        selected_entries: Mapping[str, str],
        scope_description: str,
    ) -> None:
        from tkinter import messagebox

        if self.ai_busy:
            messagebox.showinfo(
                "AI 翻译进行中", "请等待当前翻译请求完成。", parent=self.root
            )
            return
        try:
            batch_size = int(self.ai_batch_size_var.get())
        except ValueError:
            messagebox.showerror(
                "配置错误", "每批键数必须是整数。", parent=self.root
            )
            return
        config = AITranslationConfig(
            api_key=self.ai_api_key_var.get(),
            base_url=self.ai_base_url_var.get(),
            model=self.ai_model_var.get(),
            batch_size=batch_size,
        )
        if not config.api_key.strip():
            messagebox.showerror(
                "未配置 API Key",
                "请在“AI 翻译设置”中填写 API Key，"
                "或设置 DASHSCOPE_API_KEY 环境变量后重启工具。",
                parent=self.root,
            )
            return
        if not messagebox.askyesno(
            "确认 AI 翻译",
            (
                f"将使用模型 {config.model} 翻译 {len(selected_entries)} 个键，"
                f"目标语言为 {target.ref.locale}。\n"
                f"翻译范围：{scope_description}。\n\n"
                "API 调用可能产生费用。翻译完成后仍会显示差异预览，"
                "确认后才写入目标文件。"
            ),
            parent=self.root,
        ):
            return

        self._set_ai_busy(True)
        self.status_var.set(f"AI 翻译准备中：0/{len(selected_entries)}")
        primary_ref = primary.ref
        target_ref = target.ref

        def report_progress(
            batch_index: int, batch_count: int, translated: int, total: int
        ) -> None:
            self.root.after(
                0,
                lambda: self.status_var.set(
                    f"AI 翻译中：批次 {batch_index}/{batch_count}，"
                    f"已完成 {translated}/{total}"
                ),
            )

        def worker() -> None:
            try:
                translations = translate_entries_with_openai(
                    selected_entries,
                    target_ref.locale,
                    config,
                    progress=report_progress,
                )
            except Exception as exc:
                self.root.after(0, lambda error=exc: finish_error(error))
                return
            self.root.after(
                0,
                lambda result=translations: finish_success(result),
            )

        def finish_error(error: Exception) -> None:
            self._set_ai_busy(False)
            self.status_var.set("AI 翻译失败")
            messagebox.showerror("AI 翻译失败", str(error), parent=self.root)

        def finish_success(translations: dict[str, str]) -> None:
            self._set_ai_busy(False)
            skipped_keys = [
                key for key in selected_entries if key not in translations
            ]
            try:
                current_primary = load_language_document(primary_ref)
                current_target = load_language_document(target_ref)
                result = merge_imported_translations(
                    current_primary, current_target, translations
                )
            except LanguageFileError as exc:
                finish_error(exc)
                return
            summary = (
                f"AI 返回 {len(translations)} 个键；将合并 {len(result.applied)} 个，"
                f"未变化 {len(result.unchanged)} 个"
                + (
                    f"，经过重试仍跳过 {len(skipped_keys)} 个："
                    + "、".join(skipped_keys[:10])
                    if skipped_keys
                    else ""
                )
                + "。"
            )
            self.status_var.set("AI 翻译完成，等待确认保存")
            self._preview_and_save(
                current_target,
                result.entries,
                "AI 翻译合并",
                summary,
            )

        threading.Thread(target=worker, daemon=True).start()

    def _preview_and_save(
        self,
        target: LanguageDocument,
        entries: Mapping[str, str],
        operation: str,
        summary: str | None = None,
    ) -> None:
        from tkinter import messagebox

        diff = unified_diff_for_entries(target, entries)
        if not diff:
            messagebox.showinfo(
                operation, "目标文件已经符合要求，无需修改。", parent=self.root
            )
            return
        if not self._show_diff_dialog(operation, diff, summary):
            return
        try:
            backup = atomic_write_entries(
                target.ref.path,
                entries,
                target.text_format,
                create_backup=True,
            )
        except LanguageFileError as exc:
            messagebox.showerror(f"{operation}失败", str(exc), parent=self.root)
            return
        messagebox.showinfo(
            f"{operation}完成",
            f"已更新：\n{target.ref.path}\n\n备份：\n{backup}",
            parent=self.root,
        )
        self.status_var.set(f"{operation}完成：{target.ref.path}")
        self.compare_selected()

    def _show_diff_dialog(
        self, title: str, diff: str, summary: str | None = None
    ) -> bool:
        description = "确认以下差异后再写入。保存时会在原文件旁创建时间戳备份。"
        if summary:
            description = summary + "\n\n" + description
        return self._show_text_preview(
            title,
            description,
            diff,
            confirm_text="确认保存",
        )

    def _show_text_preview(
        self,
        title: str,
        description: str,
        content: str,
        *,
        confirm_text: str,
    ) -> bool:
        from tkinter import ttk

        dialog = self.tk.Toplevel(self.root)
        dialog.title(f"{title} - 预览")
        dialog.geometry("1000x650")
        dialog.transient(self.root)
        dialog.grab_set()
        result = {"confirmed": False}

        ttk.Label(
            dialog,
            text=description,
            padding=10,
            justify="left",
        ).pack(fill="x")
        text_frame = ttk.Frame(dialog)
        text_frame.pack(fill="both", expand=True, padx=10)
        text = self.tk.Text(text_frame, wrap="none", font=("Consolas", 10))
        vertical = ttk.Scrollbar(text_frame, orient="vertical", command=text.yview)
        horizontal = ttk.Scrollbar(text_frame, orient="horizontal", command=text.xview)
        text.configure(yscrollcommand=vertical.set, xscrollcommand=horizontal.set)
        text.grid(row=0, column=0, sticky="nsew")
        vertical.grid(row=0, column=1, sticky="ns")
        horizontal.grid(row=1, column=0, sticky="ew")
        text_frame.rowconfigure(0, weight=1)
        text_frame.columnconfigure(0, weight=1)
        text.insert("1.0", content)
        text.configure(state="disabled")

        buttons = ttk.Frame(dialog, padding=10)
        buttons.pack(fill="x")

        def confirm() -> None:
            result["confirmed"] = True
            dialog.destroy()

        ttk.Button(buttons, text="取消", command=dialog.destroy).pack(side="right")
        ttk.Button(buttons, text=confirm_text, command=confirm).pack(
            side="right", padx=(0, 8)
        )
        dialog.protocol("WM_DELETE_WINDOW", dialog.destroy)
        self.root.wait_window(dialog)
        return result["confirmed"]

    def refresh_aggregate_files(self, _event: Any = None) -> None:
        locale = self.aggregate_locale_var.get()
        self.aggregate_refs = [ref for ref in self.refs if ref.locale == locale]
        self.aggregate_listbox.delete(0, "end")
        for ref in self.aggregate_refs:
            relative = self._relative_display_path(ref.path)
            self.aggregate_listbox.insert(
                "end", f"{ref.namespace} | {relative}"
            )

    def _relative_display_path(self, path: Path) -> str:
        try:
            return str(path.relative_to(self.project_root))
        except ValueError:
            return str(path)

    def select_all_aggregate(self) -> None:
        self.aggregate_listbox.selection_set(0, "end")

    def clear_aggregate_selection(self) -> None:
        self.aggregate_listbox.selection_clear(0, "end")

    def aggregate_and_export(self) -> None:
        from tkinter import filedialog, messagebox

        selected_indices = self.aggregate_listbox.curselection()
        if not selected_indices:
            messagebox.showwarning(
                "未选择文件", "请选择至少一个语言文件。", parent=self.root
            )
            return
        try:
            documents = [
                load_language_document(self.aggregate_refs[index])
                for index in selected_indices
            ]
        except LanguageFileError as exc:
            messagebox.showerror("汇总失败", str(exc), parent=self.root)
            return

        result = aggregate_documents(documents)
        resolutions: dict[str, AggregateChoice] = {}
        if result.conflicts:
            selected = self._resolve_conflicts_dialog(result.conflicts)
            if selected is None:
                return
            resolutions = selected
        try:
            entries = resolve_aggregate(result, resolutions)
        except LanguageFileError as exc:
            messagebox.showerror("汇总失败", str(exc), parent=self.root)
            return

        locale = self.aggregate_locale_var.get()
        destination = filedialog.asksaveasfilename(
            title="导出汇总语言文件",
            initialdir=str(self.project_root),
            initialfile=f"{locale}_汇总.json",
            defaultextension=".json",
            filetypes=[("JSON 文件", "*.json")],
        )
        if not destination:
            return
        preview_format = TextFormat(
            has_bom=False,
            newline="\n",
            ends_with_newline=True,
        )
        preview = serialize_entries(entries, preview_format).decode("utf-8")
        if not self._show_text_preview(
            "导出汇总语言文件",
            (
                f"将汇总 {len(documents)} 个文件、{len(entries)} 个唯一键到：\n"
                f"{destination}\n\n请确认导出内容："
            ),
            preview,
            confirm_text="确认导出",
        ):
            return
        try:
            backup = atomic_write_entries(
                destination,
                entries,
                documents[0].text_format,
                create_backup=True,
            )
        except LanguageFileError as exc:
            messagebox.showerror("导出失败", str(exc), parent=self.root)
            return
        message = (
            f"已汇总 {len(documents)} 个文件、{len(entries)} 个唯一键：\n{destination}"
        )
        if backup:
            message += f"\n\n原文件备份：\n{backup}"
        messagebox.showinfo("汇总完成", message, parent=self.root)
        self.status_var.set(message.splitlines()[0])

    def _resolve_conflicts_dialog(
        self, conflicts: Mapping[str, AggregateConflict]
    ) -> dict[str, AggregateChoice] | None:
        from tkinter import messagebox, ttk

        dialog = self.tk.Toplevel(self.root)
        dialog.title("解决汇总冲突")
        dialog.geometry("1050x650")
        dialog.transient(self.root)
        dialog.grab_set()
        result: dict[str, Any] = {"resolutions": None}
        resolutions: dict[str, AggregateChoice] = {}
        conflict_list = list(conflicts.values())
        option_map: dict[str, AggregateChoice] = {}
        current_key = self.tk.StringVar()
        choice_var = self.tk.StringVar()

        ttk.Label(
            dialog,
            text=(
                f"发现 {len(conflict_list)} 个同名键具有不同值。"
                "逐项选择采用的命名空间来源后才能导出。"
            ),
            padding=10,
        ).pack(fill="x")

        columns = ("status", "key", "selected")
        tree = ttk.Treeview(dialog, columns=columns, show="headings")
        tree.heading("status", text="状态")
        tree.heading("key", text="冲突键")
        tree.heading("selected", text="已选来源")
        tree.column("status", width=80, stretch=False)
        tree.column("key", width=430)
        tree.column("selected", width=430)
        tree.pack(fill="both", expand=True, padx=10)
        for conflict in conflict_list:
            tree.insert("", "end", iid=conflict.key, values=("未解决", conflict.key, ""))

        editor = ttk.LabelFrame(dialog, text="当前冲突", padding=10)
        editor.pack(fill="x", padx=10, pady=10)
        ttk.Label(editor, textvariable=current_key).pack(anchor="w")
        choice_combo = ttk.Combobox(
            editor, textvariable=choice_var, state="readonly"
        )
        choice_combo.pack(fill="x", pady=(8, 0))

        def select_conflict(_event: Any = None) -> None:
            nonlocal option_map
            selected_items = tree.selection()
            if not selected_items:
                return
            key = selected_items[0]
            conflict = conflicts[key]
            current_key.set(key)
            option_map = {}
            labels: list[str] = []
            for index, choice in enumerate(conflict.choices, start=1):
                label = f"{index}. {choice.label}"
                labels.append(label)
                option_map[label] = choice
            choice_combo["values"] = labels
            existing = resolutions.get(key)
            if existing is None:
                choice_var.set("")
            else:
                choice_var.set(
                    next(
                        label
                        for label, choice in option_map.items()
                        if choice == existing
                    )
                )

        def apply_choice() -> None:
            selected_items = tree.selection()
            choice = option_map.get(choice_var.get())
            if not selected_items or choice is None:
                messagebox.showwarning(
                    "尚未选择", "请先选择冲突键及采用的来源。", parent=dialog
                )
                return
            key = selected_items[0]
            resolutions[key] = choice
            tree.item(
                key,
                values=("已解决", key, choice.source.namespace),
            )

        def accept() -> None:
            unresolved = set(conflicts).difference(resolutions)
            if unresolved:
                messagebox.showwarning(
                    "仍有冲突",
                    f"还有 {len(unresolved)} 个冲突未选择来源。",
                    parent=dialog,
                )
                return
            result["resolutions"] = dict(resolutions)
            dialog.destroy()

        tree.bind("<<TreeviewSelect>>", select_conflict)
        button_row = ttk.Frame(editor)
        button_row.pack(fill="x", pady=(8, 0))
        ttk.Button(button_row, text="采用此来源", command=apply_choice).pack(
            side="left"
        )
        ttk.Button(button_row, text="取消汇总", command=dialog.destroy).pack(
            side="right"
        )
        ttk.Button(button_row, text="全部解决并继续", command=accept).pack(
            side="right", padx=(0, 8)
        )

        if conflict_list:
            tree.selection_set(conflict_list[0].key)
            tree.focus(conflict_list[0].key)
            select_conflict()
        dialog.protocol("WM_DELETE_WINDOW", dialog.destroy)
        self.root.wait_window(dialog)
        return result["resolutions"]


def _command_line_scan(root: Path) -> int:
    try:
        refs, errors = scan_and_validate(root)
    except LanguageFileError as exc:
        print(f"扫描失败: {exc}")
        return 1

    print(f"发现 {len(refs)} 个语言文件")
    for ref in refs:
        print(f"  {ref.namespace}/{ref.locale}: {ref.path}")
    if errors:
        print(f"\n发现 {len(errors)} 个错误:")
        for error in errors:
            print(f"  - {error}")
        return 1
    print("所有语言文件均可正常加载。")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="StarRailExpress 语言键管理器")
    parser.add_argument(
        "--scan",
        metavar="目录",
        type=Path,
        help="只扫描并验证语言文件，不启动 UI",
    )
    args = parser.parse_args(argv)
    if args.scan is not None:
        return _command_line_scan(args.scan)

    import tkinter as tk

    project_root = Path(__file__).resolve().parent.parent
    root = tk.Tk()
    LanguageManagerApp(root, project_root)
    root.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
