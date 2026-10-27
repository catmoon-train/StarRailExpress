"""回声展示资源回归：python -X utf8 src/test/python/test_echo_ui_resources.py。无第三方依赖。"""
import hashlib
import json
from pathlib import Path
import re
import struct
import unittest
import zlib

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src/main/resources/assets"
JAVA = ROOT / "src/main/java/org/agmas/noellesroles/game/modes/echo"


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def resource(location, kind, suffix):
    namespace, path = location.split(":", 1)
    return ASSETS / namespace / kind / (path + suffix)


class EchoResourcesTest(unittest.TestCase):
    def check_png(self, path, size=None):
        self.assertTrue(path.is_file(), str(path))
        data = path.read_bytes()
        self.assertEqual(data[:8], b"\x89PNG\r\n\x1a\n", str(path))
        width, height = struct.unpack(">II", data[16:24])
        self.assertGreater(width, 0)
        self.assertGreater(height, 0)
        if size:
            self.assertEqual((width, height), size, str(path))
        offset, compressed = 8, b""
        while offset < len(data):
            length = struct.unpack(">I", data[offset:offset + 4])[0]
            kind = data[offset + 4:offset + 8]
            body = data[offset + 8:offset + 8 + length]
            crc = struct.unpack(">I", data[offset + 8 + length:offset + 12 + length])[0]
            self.assertEqual(crc, zlib.crc32(kind + body) & 0xffffffff, str(path))
            if kind == b"IDAT":
                compressed += body
            offset += length + 12
        self.assertTrue(zlib.decompress(compressed), str(path))
        return hashlib.sha256(data).hexdigest()

    def check_model(self, location, trail=()):
        self.assertNotIn(location, trail, "循环模型parent/override")
        if location in ("minecraft:item/generated", "minecraft:item/handheld"):
            return
        path = resource(location, "models", ".json")
        self.assertTrue(path.is_file(), str(path))
        model = load(path)
        self.assertIn("parent", model, str(path))
        self.check_model(model["parent"], trail + (location,))
        for texture in model.get("textures", {}).values():
            self.assertFalse(texture.startswith("#"), "展示栈不得依赖未解析纹理变量")
            self.assertTrue(texture.split(":")[1].startswith(("item/", "block/")),
                            "物品图集不自动收录gui目录：" + texture)
            self.check_png(resource(texture, "textures", ".png"))
        for override in model.get("overrides", []):
            self.check_model(override["model"], trail + (location,))

    def test_all_projection_models_parents_and_atlas_textures_resolve(self):
        for name in ("echo_resource", "echo_cutter", "echo_card_proxy", "echo_skill_token", "echo_editor"):
            self.check_model("noellesroles:item/" + name)

    def test_custom_model_indices_match_java_projection_and_all_37_cards(self):
        registry = (JAVA / "cards/EchoCardRegistry.java").read_text(encoding="utf-8")
        cards = sorted(re.findall(r'cards\.add\((?:base|up)\("([a-z_]+)"', registry))
        self.assertEqual(len(cards), 37)
        self.assertEqual(len(set(cards)), 37)
        projection = (JAVA / "content/EchoResourceItem.java").read_text(encoding="utf-8")
        self.assertIn(".map(c -> c.id()).sorted().toList()", projection)
        self.assertIn("100 + CARD_MODELS.indexOf(resource.definitionId())", projection)
        self.assertIn("1 + resource.quality().ordinal()", projection)
        quality = (JAVA / "cards/EchoDraftService.java").read_text(encoding="utf-8")
        self.assertRegex(quality, r"COMMON\s*,\s*RARE\s*,\s*BLACK_MARKET")
        overrides = load(ASSETS / "noellesroles/models/item/echo_resource.json")["overrides"]
        actual = [(o["predicate"]["custom_model_data"], o["model"]) for o in overrides]
        expected = list(enumerate(["echo_hotbar:item/" + n for n in
                                   ("pack_common", "pack_rare", "pack_black_market", "reserved")], 1))
        expected += [(100 + i, "echo_hotbar:item/" + name) for i, name in enumerate(cards)]
        self.assertEqual(actual, expected)
        hashes = set()
        for card in cards:
            texture = load(ASSETS / "echo_hotbar/models/item" / (card + ".json"))["textures"]["layer0"]
            self.assertEqual(texture, "echo_hotbar:item/cards/" + card)
            path = resource(texture, "textures", ".png")
            hashes.add(self.check_png(path, (64, 64)))
            self.assertEqual(path.read_bytes(), (ASSETS / "noellesroles/textures/gui/echo/cards" / (card + ".png")).read_bytes())
        self.assertEqual(len(hashes), 37, "每张卡牌必须保留自己的图标")

    def test_pack_and_reservation_icons_are_distinct(self):
        hashes = {self.check_png(ASSETS / "echo_hotbar/textures/item" / (name + ".png"), (32, 32))
                  for name in ("pack_common", "pack_rare", "pack_black_market", "reserved")}
        self.assertEqual(len(hashes), 4)

    def test_new_strings_exist_in_all_supported_languages(self):
        for namespace in ("echo_hud", "echo_inventory"):
            languages = [load(ASSETS / namespace / "lang" / (language + ".json"))
                         for language in ("zh_cn", "zh_tw", "en_us")]
            keys = set(languages[0])
            for language in languages:
                self.assertEqual(set(language), keys)
                self.assertTrue(all(language.values()))


if __name__ == "__main__":
    unittest.main(verbosity=2)
