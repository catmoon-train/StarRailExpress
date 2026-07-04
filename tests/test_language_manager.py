from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from tools.language_manager import (
    AITranslationConfig,
    AggregateChoice,
    ExportOptions,
    LanguageDocument,
    LanguageFileError,
    TextFormat,
    aggregate_documents,
    atomic_write_entries,
    build_ai_translation_input,
    build_translation_export,
    compare_documents,
    discover_language_files,
    is_section_marker,
    language_file_ref,
    load_language_document,
    merge_imported_translations,
    organize_target_entries,
    parse_translation_text,
    resolve_aggregate,
    synchronize_target_entries,
    translate_entries_with_openai,
    unified_diff_for_entries,
)


class LanguageManagerTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def write_language(
        self,
        relative_path: str,
        text: str,
        *,
        bom: bool = False,
    ) -> Path:
        path = self.root / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = text.encode("utf-8")
        if bom:
            payload = b"\xef\xbb\xbf" + payload
        path.write_bytes(payload)
        return path

    def load(self, relative_path: str) -> LanguageDocument:
        return load_language_document(self.root / relative_path)

    def test_discovery_supports_multiple_assets_and_excludes_outputs(self) -> None:
        source = self.write_language(
            "src/main/resources/assets/main/lang/zh_cn.json", "{}"
        )
        nested = self.write_language(
            "module/common/src/main/resources/assets/addon/lang/en_us.json", "{}"
        )
        self.write_language(
            "build/resources/main/assets/main/lang/zh_cn.json", "{}"
        )
        self.write_language("run/resourcepack/assets/main/lang/zh_cn.json", "{}")

        refs = discover_language_files(self.root)

        self.assertEqual([ref.path for ref in refs], [nested.resolve(), source.resolve()])

    def test_marker_detection_is_strict(self) -> None:
        self.assertTrue(is_section_marker("=== 角色 ===", ""))
        self.assertTrue(is_section_marker("==================== 叛徒 ====================", ""))
        self.assertFalse(is_section_marker("message.empty", ""))
        self.assertFalse(is_section_marker("=== 角色 ===", "not empty"))
        self.assertFalse(is_section_marker("== 角色 ==", ""))

    def test_loader_uses_last_duplicate_value_and_reports_conflicts(self) -> None:
        conflicting_duplicate = self.write_language(
            "assets/mod/lang/zh_cn.json", '{"a": "1", "a": "2"}'
        )
        conflicting = load_language_document(conflicting_duplicate)
        self.assertEqual(conflicting.entries, {"a": "2"})
        self.assertEqual(conflicting.duplicate_keys, ("a",))
        self.assertEqual(conflicting.conflicting_duplicate_keys, ("a",))

        same_value_duplicate = self.write_language(
            "assets/mod/lang/zh_tw.json", '{"a": "相同", "a": "相同"}'
        )
        loaded = load_language_document(same_value_duplicate)
        self.assertEqual(loaded.entries, {"a": "相同"})
        self.assertEqual(loaded.duplicate_keys, ("a",))
        self.assertEqual(loaded.conflicting_duplicate_keys, ())

        invalid = self.write_language(
            "assets/mod/lang/en_us.json", '{"a": '
        )
        with self.assertRaises(LanguageFileError):
            load_language_document(invalid)

        non_string = self.write_language(
            "assets/mod/lang/ja_jp.json", '{"a": 1}'
        )
        with self.assertRaises(LanguageFileError):
            load_language_document(non_string)

    def test_organize_diff_detects_duplicate_lines_in_original_text(self) -> None:
        self.write_language(
            "assets/mod/lang/zh_cn.json", '{"a": "主语言"}'
        )
        self.write_language(
            "assets/mod/lang/en_us.json",
            '{\n  "a": "First",\n  "a": "Last"\n}\n',
        )
        primary = self.load("assets/mod/lang/zh_cn.json")
        target = self.load("assets/mod/lang/en_us.json")
        organized = organize_target_entries(primary, target)

        diff = unified_diff_for_entries(target, organized)

        self.assertIn('-  "a": "First",', diff)
        self.assertEqual(organized, {"a": "Last"})
        self.assertEqual(diff.count('"a": "Last"'), 1)

    def test_compare_classifies_keys_and_ignores_localized_markers(self) -> None:
        self.write_language(
            "assets/mod/lang/zh_cn.json",
            """{
  "=== 中文标题 ===": "",
  "missing": "缺失原文",
  "empty": "空值原文",
  "same": "Same",
  "translated": "已翻译原文"
}""",
        )
        self.write_language(
            "assets/mod/lang/en_us.json",
            """{
  "=== English heading ===": "",
  "empty": "",
  "same": "Same",
  "translated": "Translated",
  "extra": "Extra"
}""",
        )

        result = compare_documents(
            self.load("assets/mod/lang/zh_cn.json"),
            self.load("assets/mod/lang/en_us.json"),
        )

        self.assertEqual(result.missing, ("missing",))
        self.assertEqual(result.empty, ("empty",))
        self.assertEqual(result.same_as_primary, ("same",))
        self.assertEqual(result.extra, ("extra",))

    def test_export_defaults_to_primary_values_and_preserves_relevant_markers(
        self,
    ) -> None:
        self.write_language(
            "assets/mod/lang/zh_cn.json",
            """{
  "before": "已有",
  "=== 第一组 ===": "",
  "missing": "方便翻译的主语言原文",
  "present": "已有翻译",
  "=== 第二组 ===": "",
  "not_selected": "不导出"
}""",
        )
        self.write_language(
            "assets/mod/lang/en_us.json",
            '{"before": "Before", "present": "Present", "not_selected": "Done"}',
        )
        primary = self.load("assets/mod/lang/zh_cn.json")
        target = self.load("assets/mod/lang/en_us.json")
        comparison = compare_documents(primary, target)

        exported = build_translation_export(primary, comparison)
        empty_export = build_translation_export(
            primary,
            comparison,
            ExportOptions(include_primary_values=False),
        )

        self.assertEqual(
            exported,
            {
                "=== 第一组 ===": "",
                "missing": "方便翻译的主语言原文",
            },
        )
        self.assertEqual(
            empty_export,
            {
                "=== 第一组 ===": "",
                "missing": "",
            },
        )

    def test_export_can_include_empty_and_same_categories(self) -> None:
        self.write_language(
            "assets/mod/lang/zh_cn.json",
            '{"missing": "M", "empty": "E", "same": "S"}',
        )
        self.write_language(
            "assets/mod/lang/en_us.json",
            '{"empty": "", "same": "S"}',
        )
        primary = self.load("assets/mod/lang/zh_cn.json")
        target = self.load("assets/mod/lang/en_us.json")
        comparison = compare_documents(primary, target)
        options = ExportOptions(
            include_primary_values=True,
            include_missing=False,
            include_empty=True,
            include_same_as_primary=True,
        )

        self.assertEqual(
            build_translation_export(primary, comparison, options),
            {"empty": "E", "same": "S"},
        )

        ai_input = build_ai_translation_input(primary, comparison, options)
        self.assertEqual(ai_input, {"empty": "E", "same": "S"})

    def test_ai_filter_input_matches_export_categories_without_markers(self) -> None:
        self.write_language(
            "assets/mod/lang/zh_cn.json",
            '{"=== 组 ===": "", "missing": "M", "empty": "E", "same": "S"}',
        )
        self.write_language(
            "assets/mod/lang/en_us.json",
            '{"empty": "", "same": "S"}',
        )
        primary = self.load("assets/mod/lang/zh_cn.json")
        target = self.load("assets/mod/lang/en_us.json")
        comparison = compare_documents(primary, target)
        options = ExportOptions(
            include_missing=True,
            include_empty=True,
            include_same_as_primary=False,
        )

        exported = build_translation_export(primary, comparison, options)
        ai_input = build_ai_translation_input(primary, comparison, options)

        self.assertEqual(
            exported,
            {"=== 组 ===": "", "missing": "M", "empty": "E"},
        )
        self.assertEqual(ai_input, {"missing": "M", "empty": "E"})

    def test_parse_translation_json_fence_and_key_value_text(self) -> None:
        fenced = """模型回复如下：
```json
{"translations": {"a": "译文 A", "b": "译文 B"}}
```"""
        plain = """
# translator notes
a=译文 A
b\t译文 B
"c": "译文 C",
"""

        self.assertEqual(
            parse_translation_text(fenced),
            {"a": "译文 A", "b": "译文 B"},
        )
        self.assertEqual(
            parse_translation_text(plain),
            {"a": "译文 A", "b": "译文 B", "c": "译文 C"},
        )

    def test_import_merge_applies_known_nonempty_keys_in_primary_order(self) -> None:
        self.write_language(
            "assets/mod/lang/zh_cn.json",
            '{"=== 组 ===": "", "a": "甲", "b": "乙", "c": "丙"}',
        )
        self.write_language(
            "assets/mod/lang/en_us.json",
            '{"c": "Old C", "a": "Old A", "extra": "Extra"}',
        )
        result = merge_imported_translations(
            self.load("assets/mod/lang/zh_cn.json"),
            self.load("assets/mod/lang/en_us.json"),
            {
                "=== 组 ===": "",
                "a": "New A",
                "b": "New B",
                "c": "",
                "unknown": "Unknown",
            },
        )

        self.assertEqual(result.applied, ("a", "b"))
        self.assertEqual(result.skipped_empty, ("c",))
        self.assertEqual(result.skipped_markers, ("=== 组 ===",))
        self.assertEqual(result.unknown, ("unknown",))
        self.assertEqual(
            result.entries,
            {
                "=== 组 ===": "",
                "a": "New A",
                "b": "New B",
                "c": "Old C",
                "extra": "Extra",
            },
        )

    def test_ai_translation_batches_and_validates_placeholders(self) -> None:
        calls: list[dict[str, object]] = []
        progress: list[tuple[int, int, int, int]] = []

        class FakeCompletions:
            def create(self, **kwargs: object) -> SimpleNamespace:
                calls.append(kwargs)
                user_content = kwargs["messages"][1]["content"]  # type: ignore[index]
                source = __import__("json").loads(str(user_content).split("\n", 1)[1])
                translated = {
                    key: value.replace("你好", "Hello").replace("世界", "World")
                    for key, value in source.items()
                }
                content = __import__("json").dumps(
                    {"translations": translated}, ensure_ascii=False
                )
                return SimpleNamespace(
                    choices=[
                        SimpleNamespace(message=SimpleNamespace(content=content))
                    ]
                )

        class FakeClient:
            def __init__(self, **_kwargs: object) -> None:
                self.chat = SimpleNamespace(completions=FakeCompletions())

        translated = translate_entries_with_openai(
            {
                "a": "你好 %s",
                "b": "世界 {0}",
                "c": "你好\n世界 §a",
            },
            "en_us",
            AITranslationConfig(api_key="test", batch_size=2),
            progress=lambda *args: progress.append(args),
            client_factory=FakeClient,
        )

        self.assertEqual(
            translated,
            {
                "a": "Hello %s",
                "b": "World {0}",
                "c": "Hello\nWorld §a",
            },
        )
        self.assertEqual(len(calls), 2)
        self.assertEqual(progress[-1], (2, 2, 3, 3))

    def test_ai_translation_rejects_changed_placeholders(self) -> None:
        class BadCompletions:
            def create(self, **_kwargs: object) -> SimpleNamespace:
                return SimpleNamespace(
                    choices=[
                        SimpleNamespace(
                            message=SimpleNamespace(
                                content='{"translations":{"a":"Hello"}}'
                            )
                        )
                    ]
                )

        class BadClient:
            def __init__(self, **_kwargs: object) -> None:
                self.chat = SimpleNamespace(completions=BadCompletions())

        with self.assertRaises(LanguageFileError):
            translate_entries_with_openai(
                {"a": "你好 %s"},
                "en_us",
                AITranslationConfig(api_key="test"),
                client_factory=BadClient,
            )

    def test_ai_translation_retries_empty_value_without_losing_batch(self) -> None:
        attempts: dict[str, int] = {}

        class RetryCompletions:
            def create(self, **kwargs: object) -> SimpleNamespace:
                user_content = kwargs["messages"][1]["content"]  # type: ignore[index]
                source = __import__("json").loads(
                    str(user_content).rsplit("\n", 1)[1]
                )
                translated: dict[str, str] = {}
                for key in source:
                    attempts[key] = attempts.get(key, 0) + 1
                    translated[key] = (
                        ""
                        if key == "retry" and attempts[key] == 1
                        else f"Translated {key}"
                    )
                content = __import__("json").dumps(
                    {"translations": translated}
                )
                return SimpleNamespace(
                    choices=[
                        SimpleNamespace(message=SimpleNamespace(content=content))
                    ]
                )

        class RetryClient:
            def __init__(self, **_kwargs: object) -> None:
                self.chat = SimpleNamespace(completions=RetryCompletions())

        translated = translate_entries_with_openai(
            {"good": "甲", "retry": "乙"},
            "en_us",
            AITranslationConfig(api_key="test"),
            client_factory=RetryClient,
        )

        self.assertEqual(
            translated,
            {"good": "Translated good", "retry": "Translated retry"},
        )
        self.assertEqual(attempts, {"good": 1, "retry": 2})

    def test_ai_translation_keeps_success_when_one_retry_still_fails(self) -> None:
        class PartialCompletions:
            def create(self, **kwargs: object) -> SimpleNamespace:
                user_content = kwargs["messages"][1]["content"]  # type: ignore[index]
                source = __import__("json").loads(
                    str(user_content).rsplit("\n", 1)[1]
                )
                translated = {
                    key: "" if key == "bad" else "Translated"
                    for key in source
                }
                content = __import__("json").dumps(
                    {"translations": translated}
                )
                return SimpleNamespace(
                    choices=[
                        SimpleNamespace(message=SimpleNamespace(content=content))
                    ]
                )

        class PartialClient:
            def __init__(self, **_kwargs: object) -> None:
                self.chat = SimpleNamespace(completions=PartialCompletions())

        translated = translate_entries_with_openai(
            {"good": "甲", "bad": "乙"},
            "en_us",
            AITranslationConfig(api_key="test"),
            client_factory=PartialClient,
        )

        self.assertEqual(translated, {"good": "Translated"})

    def test_organize_orders_existing_keys_without_adding_missing_keys(self) -> None:
        self.write_language(
            "assets/mod/lang/zh_cn.json",
            """{
  "top": "顶层",
  "=== 组一 ===": "",
  "a": "甲",
  "b": "乙",
  "=== 空组 ===": "",
  "missing": "缺失"
}""",
        )
        self.write_language(
            "assets/mod/lang/en_us.json",
            """{
  "=== old heading ===": "",
  "b": "B",
  "extra": "Extra",
  "top": "Top",
  "a": "A"
}""",
        )

        organized = organize_target_entries(
            self.load("assets/mod/lang/zh_cn.json"),
            self.load("assets/mod/lang/en_us.json"),
        )

        self.assertEqual(
            organized,
            {
                "top": "Top",
                "=== 组一 ===": "",
                "a": "A",
                "b": "B",
                "extra": "Extra",
            },
        )

    def test_synchronize_adds_primary_values_and_preserves_target_values(self) -> None:
        self.write_language(
            "assets/mod/lang/zh_cn.json",
            '{"=== 组 ===": "", "a": "甲", "b": "乙"}',
        )
        self.write_language(
            "assets/mod/lang/en_us.json",
            '{"a": "Translated A", "extra": "Extra"}',
        )

        synchronized = synchronize_target_entries(
            self.load("assets/mod/lang/zh_cn.json"),
            self.load("assets/mod/lang/en_us.json"),
        )

        self.assertEqual(
            synchronized,
            {
                "=== 组 ===": "",
                "a": "Translated A",
                "b": "乙",
                "extra": "Extra",
            },
        )

    def test_aggregate_deduplicates_values_and_requires_conflict_resolution(
        self,
    ) -> None:
        self.write_language(
            "assets/first/lang/zh_cn.json",
            '{"same": "相同", "conflict": "来源一"}',
        )
        self.write_language(
            "assets/second/lang/zh_cn.json",
            '{"same": "相同", "conflict": "来源二", "other": "其他"}',
        )
        first = self.load("assets/first/lang/zh_cn.json")
        second = self.load("assets/second/lang/zh_cn.json")

        result = aggregate_documents([first, second])

        self.assertEqual(set(result.entries), {"same", "conflict", "other"})
        self.assertEqual(set(result.conflicts), {"conflict"})
        with self.assertRaises(LanguageFileError):
            resolve_aggregate(result, {})

        selected: AggregateChoice = result.conflicts["conflict"].choices[1]
        resolved = resolve_aggregate(result, {"conflict": selected})
        self.assertEqual(resolved["conflict"], "来源二")

    def test_atomic_write_creates_backup_and_preserves_text_format(self) -> None:
        path = self.write_language(
            "assets/mod/lang/zh_cn.json",
            '{\r\n  "old": "旧"\r\n}',
            bom=True,
        )
        document = load_language_document(path)

        backup = atomic_write_entries(
            path,
            {"new": "新"},
            document.text_format,
            create_backup=True,
        )

        self.assertIsNotNone(backup)
        assert backup is not None
        self.assertEqual(
            backup.read_bytes(),
            b'\xef\xbb\xbf{\r\n  "old": "\xe6\x97\xa7"\r\n}',
        )
        raw = path.read_bytes()
        self.assertTrue(raw.startswith(b"\xef\xbb\xbf"))
        self.assertIn(b"\r\n", raw)
        self.assertFalse(raw.endswith(b"\n"))
        self.assertEqual(load_language_document(path).entries, {"new": "新"})

    def test_language_file_ref_requires_assets_layout(self) -> None:
        bad_path = self.write_language("translations/lang/zh_cn.json", "{}")
        with self.assertRaises(LanguageFileError):
            language_file_ref(bad_path)


if __name__ == "__main__":
    unittest.main()
