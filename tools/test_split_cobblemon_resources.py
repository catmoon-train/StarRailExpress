"""资源拆分安全与完整性回归；全部测试输入都在 scratch 临时目录。"""
import json
import os
from pathlib import Path
import tempfile
import unittest
import zipfile

import split_cobblemon_resources as split


class SplitResourcesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=os.environ.get("PI_SCRATCH_DIR"))
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.source = self.base / "Cobblemon.jar"
        entries = {
            "fabric.mod.json": json.dumps({"id": "cobblemon", "version": "1.8.4+1.21.1"}).encode(),
            "LICENSE": b"test license",
            "com/example/Code.class": b"unchanged bytecode",
            "data/cobblemon/species/test.json": b"{}",
            "datapacks/test/data/example/test.json": b"{}",
            "META-INF/jars/library.jar": b"unchanged nested jar",
            "assets/cobblemon/lang/zh_cn.json": b"{\"hello\":\"world\"}",
            "assets/cobblemon/textures/pokemon/full.png": b"full texture",
            "assets/cobblemon/sounds/test.ogg": b"sound data",
            "assets/minecraft/textures/test.png": b"vanilla override",
            "assets/supplementaries/textures/test.png": b"other namespace",
            "assets/cobblemon/bedrock/berries/test.json": b"berry fallback",
            "resourcepacks/optional/pack.mcmeta": b'{"pack":{"pack_format":34,"description":"optional"}}',
            "resourcepacks/optional/assets/test/lang/en_us.json": b"{}",
            "resourcepacks/optional/assets/test/textures/test.png": b"optional texture",
        }
        entries.update({name: name.encode() for name in split.FALLBACK_FILES})
        self.entries = entries
        with zipfile.ZipFile(self.source, "w") as jar:
            jar.writestr("assets/", b"")
            for name, data in entries.items():
                jar.writestr(name, data)
        self.hash = split.sha256(self.source)
        self.destination = self.base / "output"

    def prepare(self):
        return split.prepare(self.source, self.destination, self.hash)

    def test_split_preserves_code_data_language_and_only_small_fallback_assets(self):
        manifest_path = self.prepare()
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual(self.hash, split.sha256(self.source))
        with zipfile.ZipFile(self.destination / manifest["stripped"]) as jar:
            for name, data in self.entries.items():
                if split.retained(name):
                    self.assertEqual(data, jar.read(name), name)
                else:
                    self.assertNotIn(name, jar.namelist())
        with zipfile.ZipFile(self.destination / manifest["mainPack"]) as pack:
            self.assertEqual(34, json.loads(pack.read("pack.mcmeta"))["pack"]["pack_format"])
            self.assertFalse(any(split.language(name) for name in pack.namelist()))
            self.assertEqual(b"full texture", pack.read("assets/cobblemon/textures/pokemon/full.png"))
            self.assertEqual(b"other namespace", pack.read("assets/supplementaries/textures/test.png"))
        with zipfile.ZipFile(self.destination / manifest["optionalPacks"]["optional"]) as pack:
            self.assertIn("assets/test/textures/test.png", pack.namelist())
            self.assertIn("assets/test/lang/en_us.json", pack.namelist())
            self.assertNotIn("resourcepacks/optional/pack.mcmeta", pack.namelist())

    def test_wrong_input_hash_and_existing_output_are_rejected(self):
        with self.assertRaises(ValueError):
            split.prepare(self.source, self.destination, "0" * 64)
        self.assertFalse(self.destination.exists())
        self.destination.mkdir()
        with self.assertRaises(FileExistsError):
            self.prepare()
        self.assertEqual(self.hash, split.sha256(self.source))

    def test_apply_keeps_exact_original_backup_and_refuses_second_apply(self):
        manifest_path = self.prepare()
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        backup = split.apply(manifest_path)
        self.assertEqual(self.hash, split.sha256(backup))
        self.assertEqual(manifest["outputs"][manifest["stripped"]]["sha256"], split.sha256(self.source))
        with self.assertRaises(ValueError):
            split.apply(manifest_path)
        self.assertEqual(self.hash, split.sha256(backup))

    def test_tampered_generated_file_or_changed_source_does_not_replace_original(self):
        manifest_path = self.prepare()
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        pack = self.destination / manifest["mainPack"]
        saved = pack.read_bytes()
        pack.write_bytes(b"tampered")
        with self.assertRaises(ValueError):
            split.apply(manifest_path)
        self.assertEqual(self.hash, split.sha256(self.source))
        pack.write_bytes(saved)
        self.source.write_bytes(b"changed by user")
        with self.assertRaises(ValueError):
            split.apply(manifest_path)
        self.assertEqual(b"changed by user", self.source.read_bytes())

    def test_missing_fallback_and_signed_archive_are_rejected(self):
        for suffix, entries in (("missing", {k: v for k, v in self.entries.items()
                                             if k != next(iter(split.FALLBACK_FILES))}),
                                ("signed", {**self.entries, "META-INF/TEST.SF": b"signature"})):
            source = self.base / (suffix + ".jar")
            with zipfile.ZipFile(source, "w") as jar:
                for name, data in entries.items():
                    jar.writestr(name, data)
            with self.assertRaises(ValueError):
                split.prepare(source, self.base / suffix, split.sha256(source))
            self.assertFalse((self.base / suffix).exists())


if __name__ == "__main__":
    unittest.main()
