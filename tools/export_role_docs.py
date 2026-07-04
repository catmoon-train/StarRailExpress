#!/usr/bin/env python3
"""Export role documentation from Minecraft language JSON files."""

from __future__ import annotations

import argparse
import json
import re
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from pathlib import Path


INFO_PREFIX = "info.screen.roleid."
GOAL_PREFIX = "announcement.star.goals."
ROLE_PREFIX = "announcement.star.role."
DEFAULT_LOCALE = "zh_cn"


@dataclass(frozen=True)
class LanguageSource:
    path: Path
    entries: dict[str, str]


@dataclass(frozen=True)
class RoleDoc:
    role_id: str
    name: str
    goal: str
    info: str
    source: Path


class ExportError(ValueError):
    """Raised when role docs cannot be exported."""


def load_language_file(path: Path) -> LanguageSource:
    try:
        with path.open("r", encoding="utf-8-sig") as file:
            data = json.load(file)
    except OSError as exc:
        raise ExportError(f"Unable to read language file: {path}") from exc
    except json.JSONDecodeError as exc:
        raise ExportError(f"Invalid JSON in language file: {path}: {exc}") from exc

    if not isinstance(data, dict):
        raise ExportError(f"Language file root must be an object: {path}")

    entries: dict[str, str] = {}
    for key, value in data.items():
        if not isinstance(key, str) or not isinstance(value, str):
            raise ExportError(f"Language file must contain string keys and values: {path}")
        entries[key] = value

    return LanguageSource(path=path, entries=entries)


def discover_language_paths(root: Path, locale: str) -> list[Path]:
    assets_root = root / "src" / "main" / "resources" / "assets"
    if not assets_root.is_dir():
        raise ExportError(f"Assets directory not found: {assets_root}")

    return sorted(assets_root.glob(f"*/lang/{locale}.json"))


def merge_entries(sources: Iterable[LanguageSource]) -> dict[str, str]:
    merged: dict[str, str] = {}
    for source in sources:
        for key, value in source.entries.items():
            merged.setdefault(key, value)
    return merged


def find_suffix_value(entries: Mapping[str, str], prefix: str, role_id: str) -> str | None:
    suffix = f".{role_id}"
    for key, value in entries.items():
        if key.startswith(prefix) and key.endswith(suffix):
            return value
    return None


def lookup_role_text(
    role_id: str,
    source_entries: Mapping[str, str],
    merged_entries: Mapping[str, str],
    prefix: str,
    show_missing: bool,
) -> str:
    direct_key = f"{prefix}{role_id}"
    value = source_entries.get(direct_key) or merged_entries.get(direct_key)
    if value is not None:
        return value.strip()

    value = (
        find_suffix_value(source_entries, prefix, role_id)
        or find_suffix_value(merged_entries, prefix, role_id)
    )
    if value is not None:
        return value.strip()

    return f"[missing: {direct_key}]" if show_missing else ""


def collect_roles(sources: list[LanguageSource], show_missing: bool) -> list[RoleDoc]:
    merged_entries = merge_entries(sources)
    roles: list[RoleDoc] = []
    seen_ids: set[str] = set()

    for source in sources:
        for key, value in source.entries.items():
            if not key.startswith(INFO_PREFIX):
                continue

            role_id = key[len(INFO_PREFIX) :].strip()
            if not role_id or role_id in seen_ids:
                continue

            seen_ids.add(role_id)
            name = lookup_role_text(
                role_id, source.entries, merged_entries, ROLE_PREFIX, show_missing
            )
            goal = lookup_role_text(
                role_id, source.entries, merged_entries, GOAL_PREFIX, show_missing
            )
            roles.append(
                RoleDoc(
                    role_id=role_id,
                    name=name or role_id,
                    goal=goal,
                    info=value.strip(),
                    source=source.path,
                )
            )

    return roles


def markdown_escape_heading(text: str) -> str:
    return re.sub(r"\s+", " ", text).replace("#", r"\#").strip()


def normalize_multiline(text: str) -> str:
    lines = [line.rstrip() for line in text.strip().splitlines()]
    return "\n".join(lines).strip()


def display_path(path: Path, root: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def render_markdown(
    roles: list[RoleDoc],
    locale: str,
    sources: list[LanguageSource],
    root: Path,
) -> str:
    source_lines = "\n".join(
        f"- `{display_path(source.path, root)}`" for source in sources
    )
    lines: list[str] = [
        f"# Role Documentation ({locale})",
        "",
        f"Total roles: {len(roles)}",
        "",
        "Sources:",
        source_lines,
        "",
    ]

    for role in roles:
        heading_name = markdown_escape_heading(role.name)
        lines.extend(
            [
                f"## {heading_name}",
                "",
                f"- ID: `{role.role_id}`",
                f"- Goal: {role.goal}" if role.goal else "- Goal:",
                "",
                "### Description",
                "",
                normalize_multiline(role.info),
                "",
            ]
        )

    return "\n".join(lines).rstrip() + "\n"


def write_output(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export role docs from lang JSON keys."
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=Path.cwd(),
        help="Project root used for auto-discovery. Defaults to current directory.",
    )
    parser.add_argument(
        "--locale",
        default=DEFAULT_LOCALE,
        help=f"Locale to auto-discover when --lang is omitted. Defaults to {DEFAULT_LOCALE}.",
    )
    parser.add_argument(
        "--lang",
        type=Path,
        action="append",
        default=[],
        help="Language JSON file. Can be provided multiple times.",
    )
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Output Markdown path. Defaults to docs/role_docs_<locale>.md.",
    )
    parser.add_argument(
        "--show-missing",
        action="store_true",
        help="Show missing role/goal keys instead of leaving fields blank.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    language_paths = [path.resolve() for path in args.lang]
    if not language_paths:
        language_paths = discover_language_paths(root, args.locale)
    if not language_paths:
        raise ExportError(f"No language files found for locale: {args.locale}")

    sources = [load_language_file(path) for path in language_paths]
    roles = collect_roles(sources, show_missing=args.show_missing)
    output = args.output or (root / "docs" / f"role_docs_{args.locale}.md")

    write_output(output.resolve(), render_markdown(roles, args.locale, sources, root))
    print(f"Exported {len(roles)} roles to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
