package io.wifi.starrailexpress.util;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 护甲与耐久附魔标签追加原版装备，不清空模组内容，也不依赖已失效的分类标签。 */
class ArmorEnchantmentTagsTest {
    private static final Path ROOT = Path.of("src/main/resources/data/minecraft/tags/item/enchantable");

    private Set<String> values(String name) throws Exception {
        var json = JsonParser.parseString(Files.readString(ROOT.resolve(name + ".json"))).getAsJsonObject();
        assertFalse(json.get("replace").getAsBoolean(), name);
        var result = new HashSet<String>();
        for (var value : json.getAsJsonArray("values")) {
            assertTrue(result.add(value.getAsString()), "重复条目：" + value);
            assertFalse(value.getAsString().startsWith("#"), "修复不依赖原分类标签");
        }
        return result;
    }

    @Test void allVanillaArmorRemainsEnchantableWithoutReplacingModdedEntries() throws Exception {
        var all = new HashSet<String>();
        for (var entry : Map.of("head_armor", "helmet", "chest_armor", "chestplate",
                "leg_armor", "leggings", "foot_armor", "boots").entrySet()) {
            var expected = new HashSet<String>();
            for (String material : new String[]{"leather", "chainmail", "golden", "iron", "diamond", "netherite"}) {
                expected.add("minecraft:" + material + "_" + entry.getValue());
            }
            if (entry.getKey().equals("head_armor")) expected.add("minecraft:turtle_helmet");
            assertEquals(expected, values(entry.getKey()), entry.getKey());
            all.addAll(expected);
        }
        assertEquals(25, all.size());
        assertEquals(all, values("armor"));
    }

    @Test void durabilitySupportsAllVanillaEquipmentIncludingDiamondPickaxe() throws Exception {
        var expected = new HashSet<>(values("armor"));
        for (String material : new String[]{"wooden", "stone", "iron", "golden", "diamond", "netherite"}) {
            for (String tool : new String[]{"sword", "axe", "pickaxe", "shovel", "hoe"}) {
                expected.add("minecraft:" + material + "_" + tool);
            }
        }
        for (String item : new String[]{"elytra", "shield", "bow", "crossbow", "trident", "flint_and_steel",
                "shears", "brush", "fishing_rod", "carrot_on_a_stick", "warped_fungus_on_a_stick", "mace"}) {
            expected.add("minecraft:" + item);
        }
        var actual = values("durability");
        assertEquals(67, expected.size());
        assertEquals(expected, actual);
        assertTrue(actual.contains("minecraft:diamond_pickaxe"));
        assertFalse(actual.contains("minecraft:enchanted_book"));
    }
}
