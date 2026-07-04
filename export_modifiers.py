#!/usr/bin/env python3
"""Export all modifiers and their descriptions from Minecraft language JSON files to Markdown."""

from __future__ import annotations

import argparse
import json
import re
from collections.abc import Iterable
from dataclasses import dataclass, field
from pathlib import Path

DEFAULT_LOCALE = "zh_cn"

# Matches bare form:  announcement.star.modifier.lovers  -> id="lovers"
# Matches namespaced:  announcement.star.modifier.noellesroles.last_gasp  -> ns="noellesroles", id="last_gasp"
MODIFIER_NAME_RE = re.compile(r"^announcement\.star\.modifier(?:\.([a-z0-9_]+))?\.([a-z0-9_]+)$")
# Descriptions are always bare:  info.screen.modifier.lovers
MODIFIER_DESC_RE = re.compile(r"^info\.screen\.modifier\.([a-z0-9_]+)$")


@dataclass(frozen=True)
class LanguageSource:
    path: Path
    entries: dict[str, str]


@dataclass
class ModifierEntry:
    namespace: str
    modifier_id: str
    display_name: str = ""
    desc: str = ""
    desc_simple: str = ""
    source_paths: list[str] = field(default_factory=list)


class ExportError(ValueError):
    """Raised when modifiers cannot be exported."""


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
        if isinstance(key, str) and isinstance(value, str) and value.strip():
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


def is_modifier_desc(key: str) -> bool:
    """Check if a key is a modifier description (not a role description)."""
    return key.count(".") == 3 and key.startswith("info.screen.modifier.")


def collect_modifiers(sources: list[LanguageSource]) -> dict[tuple[str, str], ModifierEntry]:
    """Collect modifiers from all language sources. Returns dict keyed by (namespace, modifier_id).

    The namespace defaults to "general" for bare keys like announcement.star.modifier.lovers.
    """
    merged = merge_entries(sources)
    modifiers: dict[tuple[str, str], ModifierEntry] = {}

    # Step 1: collect names from announcement.star.modifier keys
    for source in sources:
        for key, value in source.entries.items():
            m = MODIFIER_NAME_RE.match(key)
            if not m:
                continue
            ns = m.group(1) or "general"
            mod_id = m.group(2)

            mod_key = (ns, mod_id)
            if mod_key not in modifiers:
                modifiers[mod_key] = ModifierEntry(namespace=ns, modifier_id=mod_id)

            entry = modifiers[mod_key]
            if not entry.display_name:
                entry.display_name = value
            if str(source.path) not in entry.source_paths:
                entry.source_paths.append(str(source.path))

    # Step 2: attach descriptions from info.screen.modifier keys
    for (ns, mod_id), entry in modifiers.items():
        desc_key = f"info.screen.modifier.{mod_id}"
        if desc_key in merged:
            entry.desc = merged[desc_key]

        simple_key = f"info.screen.modifier.{mod_id}.simple"
        if simple_key in merged and merged[simple_key].strip():
            entry.desc_simple = merged[simple_key]

    return modifiers


def display_path(path: str, root: Path) -> str:
    try:
        return Path(path).relative_to(root).as_posix()
    except ValueError:
        return path


def normalize_multiline(text: str) -> str:
    lines = [line.rstrip() for line in text.strip().splitlines()]
    return "\n".join(lines).strip()


def render_markdown(
    modifiers: dict[tuple[str, str], ModifierEntry],
    locale: str,
    sources: list[LanguageSource],
    root: Path,
) -> str:
    source_lines = "\n".join(
        f"- `{display_path(source.path, root)}`" for source in sources
    )

    lines: list[str] = [
        f"# Modifier Documentation ({locale})",
        "",
        f"Total modifiers: {len(modifiers)}",
        "",
        "Sources:",
        source_lines,
        "",
        "---",
        "",
    ]

    by_namespace: dict[str, list[ModifierEntry]] = {}
    for entry in modifiers.values():
        by_namespace.setdefault(entry.namespace, []).append(entry)

    for namespace in sorted(by_namespace):
        ns_mods = sorted(by_namespace[namespace], key=lambda e: e.modifier_id)
        lines.append(f"## {namespace}  ({len(ns_mods)} modifiers)")
        lines.append("")

        lines.append("| ID | Name | Description (Simple) |")
        lines.append("|----|------|----------------------|")

        for entry in ns_mods:
            name = entry.display_name or entry.modifier_id
            desc = entry.desc_simple or entry.desc or "-"
            desc = desc.replace("\n", "<br>").replace("|", "\\|")
            name = name.replace("|", "\\|")
            lines.append(f"| `{entry.modifier_id}` | {name} | {desc} |")

        lines.append("")

        # Detailed descriptions section
        lines.append(f"### {namespace} — Full Descriptions")
        lines.append("")
        for entry in ns_mods:
            display = entry.display_name or entry.modifier_id
            full_desc = entry.desc or entry.desc_simple or ""
            if not full_desc:
                continue
            lines.append(f"**{display}** (`{entry.modifier_id}`)")
            lines.append("")
            lines.append(normalize_multiline(full_desc))
            lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def write_output(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export modifier docs from lang JSON keys to Markdown."
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=Path.cwd(),
        help="Project root. Defaults to current directory.",
    )
    parser.add_argument(
        "--locale",
        default=DEFAULT_LOCALE,
        help=f"Locale to auto-discover. Defaults to {DEFAULT_LOCALE}.",
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
        help="Output Markdown path. Defaults to docs/modifiers_<locale>.md.",
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
    modifiers = collect_modifiers(sources)

    output = args.output or (root / "docs" / f"modifiers_{args.locale}.md")
    text = render_markdown(modifiers, args.locale, sources, root)
    write_output(output.resolve(), text)
    print(f"Exported {len(modifiers)} modifiers to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
