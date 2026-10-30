package org.agmas.noellesroles.game.halloween;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 登记与资源契约检查；不初始化 Minecraft，也不把它当作客户端 UI 实测。 */
class HalloweenItemCatalogTest {
    private static final Path SOURCE = Path.of("src/main/java/org/agmas/noellesroles/game/halloween/HalloweenItems.java");

    private static Map<String, String> registeredItems(String source) {
        Map<String, String> items = new HashMap<>();
        var matcher = Pattern.compile("public static final Item (\\w+) = (?:register|basic)\\(\"([^\"]+)\"").matcher(source);
        while (matcher.find()) assertNull(items.put(matcher.group(1), matcher.group(2)), "重复物品字段");
        assertEquals(15, items.size());
        return items;
    }

    private static List<String> list(String values) {
        return Arrays.stream(values.split(",")).map(String::trim).toList();
    }

    @Test void allRegisteredItemsHaveExactlyOneCatalogEntryAndOneFunctionalCategory() throws Exception {
        String source = Files.readString(SOURCE);
        Map<String, String> items = registeredItems(source);
        var all = Pattern.compile("ALL_ITEMS = List\\.of\\(([^;]+)\\);", Pattern.DOTALL).matcher(source);
        assertTrue(all.find());
        List<String> catalog = list(all.group(1));
        assertEquals(items.size(), catalog.size());
        assertEquals(items.keySet(), new HashSet<>(catalog));
        List<String> categorized = new ArrayList<>();
        var groups = Pattern.compile("addToGroup\\(TMMItems\\.(\\w+), List\\.of\\(([^;]+)\\)\\);", Pattern.DOTALL).matcher(source);
        while (groups.find()) categorized.addAll(list(groups.group(2)));
        assertEquals(items.size(), categorized.size(), "分类不应重复或遗漏");
        assertEquals(items.keySet(), new HashSet<>(categorized));
        assertTrue(source.contains("addToGroup(TMMItems.NOELLESROLES_ALL_GROUP, ALL_ITEMS)"));
        assertTrue(source.contains("ItemGroupEvents.modifyEntriesEvent(group).register(entries -> items.forEach(entries::accept))"));
    }

    @Test void introductionAndCreativeRegistrationRunOnceWithoutAnEventGate() throws Exception {
        String source = Files.readString(SOURCE);
        int initialize = source.indexOf("public static void initialize()");
        int guard = source.indexOf("if (initialized) return;", initialize);
        int registration = source.indexOf("TMMDescItems.introItems.addAll(ALL_ITEMS);", initialize);
        int group = source.indexOf("addToGroup(TMMItems.NOELLESROLES_ALL_GROUP", initialize);
        int gameplay = source.indexOf("PumpkinHideouts.initialize();", initialize);
        assertTrue(initialize >= 0 && guard > initialize && registration > guard && group > registration && gameplay > group);
        assertFalse(source.substring(initialize, gameplay).contains("HalloweenRuntime.isEnabled"), "查看物品不应要求活动正在进行");
    }

    @Test void everyActivityItemHasChineseNameIntroductionAndModel() throws Exception {
        var resources = Path.of("src/main/resources/assets");
        var names = JsonParser.parseString(Files.readString(resources.resolve("halloween/lang/zh_cn.json"))).getAsJsonObject();
        var descriptions = JsonParser.parseString(Files.readString(resources.resolve("item_intro/lang/zh_cn.json"))).getAsJsonObject();
        for (String id : registeredItems(Files.readString(SOURCE)).values()) {
            String key = "item.noellesroles.halloween_" + id;
            assertTrue(names.has(key), key);
            assertTrue(descriptions.has(key + ".desc"), key);
            assertFalse(descriptions.get(key + ".desc").getAsString().isBlank(), key);
            assertTrue(Files.isRegularFile(resources.resolve("noellesroles/models/item/halloween_" + id + ".json")), id);
        }
    }
}
