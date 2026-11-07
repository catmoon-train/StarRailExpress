"""训练场资源的独立 NBT/Anvil 回读测试：python -X utf8 -B tools/test_generate_training_world.py。"""
import gzip
import io
import json
import math
import struct
import unittest
import zipfile
import zlib

import generate_training_world as generator


class NbtReader:
    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def unpack(self, fmt):
        return struct.unpack(fmt, self.stream.read(struct.calcsize(fmt)))[0]

    def text(self):
        return self.stream.read(self.unpack(">H")).decode("utf-8")

    def value(self, kind):
        if kind in (1, 2, 3, 4, 5, 6):
            return self.unpack({1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}[kind])
        if kind == 8:
            return self.text()
        if kind == 9:
            subtype = self.unpack(">B")
            return [self.value(subtype) for _ in range(self.unpack(">i"))]
        if kind == 10:
            result = {}
            while True:
                subtype = self.unpack(">B")
                if subtype == 0:
                    return result
                key = self.text()
                assert key not in result
                result[key] = self.value(subtype)
        if kind == 12:
            return [self.unpack(">Q") for _ in range(self.unpack(">i"))]
        raise AssertionError(f"Unexpected NBT type {kind}")

    def root(self):
        assert self.unpack(">B") == 10
        assert self.text() == ""
        result = self.value(10)
        assert self.stream.read() == b"", "trailing NBT data"
        return result


class TrainingWorldGeneratorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.archive, cls.layout = generator.generate()
        cls.zip = zipfile.ZipFile(io.BytesIO(cls.archive))

    def test_committed_resources_and_determinism(self):
        self.assertEqual(self.archive, (generator.OUTPUT / "world-template.zip").read_bytes())
        self.assertEqual(self.layout, (generator.OUTPUT / "layout.json").read_bytes())
        second_archive, second_layout = generator.generate()
        self.assertEqual(self.archive, second_archive)
        self.assertEqual(self.layout, second_layout)
        self.assertIsNone(self.zip.testzip())
        self.assertEqual(set(self.zip.namelist()), {"level.dat", "region/r.0.0.mca", "layout.json",
            "readyArea.json", "train_maps/training.json", "sre-training.json"})
        self.assertEqual(self.zip.read("sre-training.json"), generator.MARKER)

    def test_level_nbt_and_empty_world_generation(self):
        level = NbtReader(gzip.decompress(self.zip.read("level.dat"))).root()["Data"]
        self.assertEqual(level["DataVersion"], 3955)
        self.assertEqual(level["Version"]["Name"], "1.21.1")
        self.assertEqual(level["GameType"], 2)
        self.assertEqual((level["SpawnX"], level["SpawnY"], level["SpawnZ"]), (24, 65, 97))
        self.assertEqual(level["DayTime"], 6000)
        self.assertNotIn("Player", level)
        self.assertEqual(level["GameRules"]["doMobSpawning"], "false")
        self.assertEqual(level["GameRules"]["doDaylightCycle"], "false")
        self.assertEqual(level["GameRules"]["doWeatherCycle"], "false")
        settings = level["WorldGenSettings"]
        self.assertEqual(settings["generate_features"], 0)
        self.assertEqual(set(settings["dimensions"]), {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"})
        for dimension in settings["dimensions"].values():
            self.assertEqual(dimension["generator"]["type"], "minecraft:flat")
            flat = dimension["generator"]["settings"]
            self.assertEqual(flat["layers"], [{"height": 1, "block": "minecraft:air"}])
            self.assertEqual(flat["structure_overrides"], [])
            self.assertEqual(flat["features"], 0)

    def test_region_offsets_sections_and_every_serialized_block(self):
        data = self.zip.read("region/r.0.0.mca")
        self.assertEqual(len(data) % 4096, 0)
        used = {0, 1}
        chunks = 0
        found_entities = 0
        for index in range(1024):
            location = struct.unpack_from(">I", data, index * 4)[0]
            if location == 0:
                continue
            offset, sectors = location >> 8, location & 255
            claimed = set(range(offset, offset + sectors))
            self.assertFalse(used & claimed)
            used |= claimed
            self.assertGreater(sectors, 0)
            self.assertLessEqual((offset + sectors) * 4096, len(data))
            size = struct.unpack_from(">I", data, offset * 4096)[0]
            self.assertLessEqual(size + 4, sectors * 4096)
            self.assertEqual(data[offset * 4096 + 4], 2)
            chunk = NbtReader(zlib.decompress(data[offset * 4096 + 5:offset * 4096 + 4 + size])).root()
            cx, cz = index % 32, index // 32
            self.assertLess(cx, 10)
            self.assertLess(cz, 8)
            self.assertEqual((chunk["xPos"], chunk["zPos"], chunk["yPos"]), (cx, cz, -4))
            self.assertEqual(chunk["Status"], "minecraft:full")
            self.assertEqual(chunk["DataVersion"], 3955)
            self.assertEqual([s["Y"] for s in chunk["sections"]], list(range(-4, 20)))
            for section in chunk["sections"]:
                states = section["block_states"]
                palette = [(v["Name"], tuple(sorted(v.get("Properties", {}).items()))) for v in states["palette"]]
                if len(palette) == 1:
                    self.assertEqual(palette, [generator.AIR])
                    self.assertNotIn("data", states)
                    continue
                bits = max(4, (len(palette) - 1).bit_length())
                per = 64 // bits
                self.assertEqual(len(states["data"]), math.ceil(4096 / per))
                for i in range(4096):
                    palette_index = (states["data"][i // per] >> ((i % per) * bits)) & ((1 << bits) - 1)
                    self.assertLess(palette_index, len(palette))
                    pos = (cx * 16 + i % 16, section["Y"] * 16 + i // 256, cz * 16 + (i // 16) % 16)
                    self.assertEqual(palette[palette_index], generator.BLOCKS.get(pos, generator.AIR), pos)
            self.assertEqual(len(chunk["Heightmaps"]["WORLD_SURFACE"]), 37)
            found_entities += len(chunk["block_entities"])
            for entity in chunk["block_entities"]:
                self.assertEqual((entity["x"] // 16, entity["z"] // 16), (cx, cz))
                self.assertEqual(entity["id"], generator.ENTITIES[entity["x"], entity["y"], entity["z"]]["id"][1])
            chunks += 1
        self.assertEqual(chunks, 80)
        self.assertEqual(found_entities, len(generator.ENTITIES))
        self.assertEqual(len(used) * 4096, len(data))

    def test_layout_routes_and_lobby_isolation(self):
        layout = json.loads(self.layout)
        self.assertEqual(layout["version"], 1)
        self.assertEqual(layout["bounds"], [0, 61, 0, 160, 96, 128])
        self.assertEqual([a["id"] for a in layout["areas"]], ["lobby", "range", "arena", "train"])
        generator.verify_positions(layout)
        lobby = layout["areas"][0]["bounds"]
        for area in layout["areas"]:
            bounds = area["bounds"]
            self.assertEqual(len(bounds), 6)
            for pos in [area["spawn"], *area["botSpawns"], *area["patrol"], *area["nodes"]]:
                for axis in range(3):
                    self.assertLessEqual(bounds[axis], pos[axis])
                    self.assertLess(pos[axis], bounds[axis + 3])
            if area["id"] != "lobby":
                self.assertGreaterEqual(len(area["botSpawns"]), 32)
                self.assertEqual(len(area["botSpawns"]), len({tuple(p) for p in area["botSpawns"]}))
                self.assertTrue(bounds[3] <= lobby[0] or bounds[0] >= lobby[3]
                                or bounds[5] <= lobby[2] or bounds[2] >= lobby[5])
        ready = json.loads(self.zip.read("readyArea.json"))["readyArea"]
        self.assertEqual(ready, generator.aabb(lobby))
        config = json.loads(self.zip.read("train_maps/training.json"))
        self.assertEqual(config["readyArea"], ready)
        self.assertEqual(config["mapName"], "training")
        self.assertTrue(config["settings"]["noReset"])
        self.assertTrue(config["settings"]["canJump"])
        self.assertEqual(config["roomCount"], 3)
        self.assertEqual(json.loads(self.zip.read("layout.json")), layout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
