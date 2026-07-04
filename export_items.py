#!/usr/bin/env python3
"""Export all items and their descriptions from Minecraft language JSON files to Markdown."""

from __future__ import annotations

import argparse
import json
import re
from collections.abc import Iterable, Mapping
from dataclasses import dataclass, field
from pathlib import Path


DEFAULT_LOCALE = "zh_cn"
ITEM_KEY_RE = re.compile(r"^item\.([a-z0-9_]+)\.([a-z0-9_]+)$")

# Namespaces that don't register actual in-game items — used only for
# books, config UIs, map info, etc.
NON_ITEM_NAMESPACES: set[str] = {
    "item_intro",
    "map_intro",
    "role_stories",
    "role_modifier_intro",
    "config_translations",
    "sre_commands",
    "harpymodloader",
    "harpy_modloader",
}


@dataclass(frozen=True)
class LanguageSource:
    path: Path
    entries: dict[str, str]


@dataclass
class ItemEntry:
    namespace: str
    item_name: str
    display_name: str = ""
    tooltip: str = ""
    desc: str = ""
    source_paths: list[str] = field(default_factory=list)


class ExportError(ValueError):
    """Raised when items cannot be exported."""


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


def detect_item_namespaces(root: Path) -> set[str]:
    """Find namespaces that actually have item model JSONs."""
    assets_root = root / "src" / "main" / "resources" / "assets"
    if not assets_root.is_dir():
        return set()
    namespaces: set[str] = set()
    for item_json in assets_root.glob("*/models/item/*.json"):
        ns = item_json.parent.parent.parent.name
        namespaces.add(ns)
    return namespaces


def collect_items(
    sources: list[LanguageSource],
    include_namespaces: set[str] | None = None,
    skip_namespaces: set[str] | None = None,
    show_all: bool = False,
) -> dict[tuple[str, str], ItemEntry]:
    """Collect items from all language sources. Returns dict keyed by (namespace, item_name).

    By default, filters out non-item namespaces (config, stories, etc.).
    Pass show_all=True to include everything.
    """
    merged = merge_entries(sources)
    items: dict[tuple[str, str], ItemEntry] = {}
    skip = skip_namespaces or set()

    for source in sources:
        for key, value in source.entries.items():
            m = ITEM_KEY_RE.match(key)
            if not m:
                continue
            namespace, item_name = m.group(1), m.group(2)

            # Filter by namespace
            if not show_all:
                if namespace in skip:
                    continue
                if include_namespaces is not None and namespace not in include_namespaces:
                    continue

            item_key = (namespace, item_name)

            if item_key not in items:
                items[item_key] = ItemEntry(namespace=namespace, item_name=item_name)

            entry = items[item_key]
            if not entry.display_name:
                entry.display_name = value
            if str(source.path) not in entry.source_paths:
                entry.source_paths.append(str(source.path))

    # Attach tooltips and descriptions from merged entries
    for (ns, name), entry in items.items():
        base = f"item.{ns}.{name}"

        tooltip_key = f"{base}.tooltip"
        if tooltip_key in merged:
            entry.tooltip = merged[tooltip_key]

        desc_key = f"{base}.desc"
        if desc_key in merged:
            entry.desc = merged[desc_key]

        # Some items have tooltip2
        tooltip2_key = f"{base}.tooltip2"
        if tooltip2_key in merged:
            extra = merged[tooltip2_key]
            if entry.tooltip:
                entry.tooltip += "\n" + extra
            else:
                entry.tooltip = extra

    return items


def display_path(path: str, root: Path) -> str:
    try:
        return Path(path).relative_to(root).as_posix()
    except ValueError:
        return path


def normalize_multiline(text: str) -> str:
    lines = [line.rstrip() for line in text.strip().splitlines()]
    return "\n".join(lines).strip()


def render_markdown(
    items: dict[tuple[str, str], ItemEntry],
    locale: str,
    sources: list[LanguageSource],
    root: Path,
) -> str:
    source_lines = "\n".join(
        f"- `{display_path(source.path, root)}`" for source in sources
    )

    lines: list[str] = [
        f"# Item Documentation ({locale})",
        "",
        f"Total items: {len(items)}",
        "",
        "Sources:",
        source_lines,
        "",
        "---",
        "",
    ]

    # Group items by namespace
    by_namespace: dict[str, list[ItemEntry]] = {}
    for entry in items.values():
        by_namespace.setdefault(entry.namespace, []).append(entry)

    for namespace in sorted(by_namespace):
        ns_items = sorted(by_namespace[namespace], key=lambda e: e.item_name)
        lines.append(f"## {namespace}  ({len(ns_items)} items)")
        lines.append("")

        lines.append("| Item ID | Name | Tooltip | Description |")
        lines.append("|---------|------|---------|-------------|")

        for entry in ns_items:
            name = entry.display_name or f"*{entry.item_name}*"
            tooltip = entry.tooltip.replace("\n", "<br>") if entry.tooltip else "-"
            desc = entry.desc.replace("\n", "<br>") if entry.desc else "-"
            # Escape pipe characters in markdown table
            name = name.replace("|", "\\|")
            tooltip = tooltip.replace("|", "\\|")
            desc = desc.replace("|", "\\|")
            lines.append(
                f"| `{namespace}:{entry.item_name}` | {name} | {tooltip} | {desc} |"
            )

        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def write_output(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export item docs from lang JSON keys to Markdown."
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
        help="Output Markdown path. Defaults to docs/items_<locale>.md.",
    )
    parser.add_argument(
        "--show-empty",
        action="store_true",
        help="Include items that have no description or tooltip.",
    )
    parser.add_argument(
        "--no-table",
        action="store_true",
        help="Use heading-based format instead of table.",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        dest="show_all",
        help="Include all namespaces (config, stories, etc.), not just item namespaces.",
    )
    parser.add_argument(
        "--namespace",
        action="append",
        default=[],
        dest="namespaces",
        help="Only include specific namespace(s). Can be provided multiple times.",
    )
    return parser.parse_args()


def render_markdown_headings(
    items: dict[tuple[str, str], ItemEntry],
    locale: str,
    sources: list[LanguageSource],
    root: Path,
    show_empty: bool,
) -> str:
    """Alternative format using headings per item instead of a table."""
    source_lines = "\n".join(
        f"- `{display_path(source.path, root)}`" for source in sources
    )

    lines: list[str] = [
        f"# Item Documentation ({locale})",
        "",
        f"Total items: {len(items)}",
        "",
        "Sources:",
        source_lines,
        "",
        "---",
        "",
    ]

    by_namespace: dict[str, list[ItemEntry]] = {}
    for entry in items.values():
        by_namespace.setdefault(entry.namespace, []).append(entry)

    for namespace in sorted(by_namespace):
        ns_items = sorted(by_namespace[namespace], key=lambda e: e.item_name)
        lines.append(f"## {namespace}  ({len(ns_items)} items)")
        lines.append("")

        for entry in ns_items:
            if not show_empty and not entry.tooltip and not entry.desc:
                continue

            display = entry.display_name or entry.item_name
            lines.append(f"### {display}")
            lines.append("")
            lines.append(f"- **ID:** `{namespace}:{entry.item_name}`")

            if entry.tooltip:
                lines.append(f"- **Tooltip:** {normalize_multiline(entry.tooltip)}")
            else:
                lines.append("- **Tooltip:** -")

            if entry.desc:
                lines.append("")
                lines.append(normalize_multiline(entry.desc))
            else:
                lines.append("")
                lines.append("*No description available.*")

            lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    language_paths = [path.resolve() for path in args.lang]
    if not language_paths:
        language_paths = discover_language_paths(root, args.locale)
    if not language_paths:
        raise ExportError(f"No language files found for locale: {args.locale}")

    sources = [load_language_file(path) for path in language_paths]

    # Determine which namespace filter to use
    show_all = args.show_all or bool(args.namespaces)
    if args.namespaces:
        include_ns = set(args.namespaces)
        skip_ns = set()
    elif not show_all:
        item_ns = detect_item_namespaces(root)
        include_ns = item_ns if item_ns else None
        skip_ns = NON_ITEM_NAMESPACES
    else:
        include_ns = None
        skip_ns = set()

    items = collect_items(sources, include_ns, skip_ns, show_all)

    output = args.output or (root / "docs" / f"items_{args.locale}.md")

    if args.no_table:
        text = render_markdown_headings(items, args.locale, sources, root, args.show_empty)
    else:
        text = render_markdown(items, args.locale, sources, root)

    write_output(output.resolve(), text)
    print(f"Exported {len(items)} items to {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
