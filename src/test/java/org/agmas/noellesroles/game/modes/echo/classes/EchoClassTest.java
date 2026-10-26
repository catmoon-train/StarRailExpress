package org.agmas.noellesroles.game.modes.echo.classes;

import com.google.gson.JsonParser;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EchoClassTest {
    @Test void threeClassesPerFactionCoveringAllThreeRoles() {
        for (EchoFaction faction : List.of(EchoFaction.INTERCEPTOR, EchoFaction.CARRIER)) {
            var pool = EchoClass.of(faction);
            assertEquals(3, pool.size(), faction + " 应有三个职业");
            assertEquals(Set.of(EchoClass.Role.values()),
                    new HashSet<>(pool.stream().map(EchoClass::role).toList()),
                    "三个职业必须分别覆盖战斗/牵制/循环");
            for (var clazz : pool) assertEquals(faction, clazz.faction());
        }
    }

    @Test void idsAndKeysAreUniqueAndStable() {
        Set<String> ids = new HashSet<>(), icons = new HashSet<>();
        for (var clazz : EchoClass.values()) {
            assertTrue(ids.add(clazz.id()), clazz.id());
            assertTrue(icons.add(clazz.icon()), clazz.icon());
            assertTrue(clazz.nameKey().startsWith("echo.class."));
            assertTrue(clazz.descriptionKey().startsWith("echo.class."));
            assertTrue(clazz.passiveKey().startsWith("echo.class."));
            assertTrue(clazz.skillKey().startsWith("echo.class."));
            assertEquals(clazz, EchoClass.byId(clazz.id()).orElseThrow());
        }
        assertTrue(EchoClass.byId("nope").isEmpty());
        assertTrue(EchoClass.byId(null).isEmpty());
    }

    @Test void defaultAssignmentRotatesWithinFactionAndWrapsSafely() {
        var wolves = EchoClass.of(EchoFaction.INTERCEPTOR);
        assertEquals(wolves.get(0), EchoClass.defaultFor(EchoFaction.INTERCEPTOR, 0));
        assertEquals(wolves.get(1), EchoClass.defaultFor(EchoFaction.INTERCEPTOR, 1));
        assertEquals(wolves.get(0), EchoClass.defaultFor(EchoFaction.INTERCEPTOR, 3));
        assertEquals(wolves.get(2), EchoClass.defaultFor(EchoFaction.INTERCEPTOR, -1));
        // 两个阵营拿到的是各自的职业，不会串阵营
        assertNotEquals(EchoClass.defaultFor(EchoFaction.CARRIER, 0).faction(), EchoFaction.INTERCEPTOR);
    }

    @Test void classIconsExistAsSquareRgbaPng() throws Exception {
        for (var clazz : EchoClass.values()) {
            String path = clazz.icon().substring(clazz.icon().indexOf(':') + 1);
            Path file = Path.of("src/main/resources/assets/noellesroles").resolve(path);
            assertTrue(Files.exists(file), clazz.id() + " 缺少图标");
            byte[] bytes = Files.readAllBytes(file);
            assertTrue(bytes.length > 40, clazz.id() + " 图标为空");
            // PNG 头 + IHDR 宽高相等
            assertEquals((byte) 0x89, bytes[0]);
            int w = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16) | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
            int h = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16) | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
            assertEquals(w, h, clazz.id() + " 图标必须是正方形");
        }
    }

    @Test void everyClassKeyExistsInThreeLanguagesWithSameKeySet() throws Exception {
        Set<String> previous = null;
        for (String language : List.of("zh_cn", "zh_tw", "en_us")) {
            var json = JsonParser.parseString(Files.readString(
                    Path.of("src/main/resources/assets/echo_freight/lang/" + language + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            if (previous != null) assertEquals(previous, json.keySet(), language + " 键集不一致");
            previous = new HashSet<>(json.keySet());
            for (var clazz : EchoClass.values()) {
                for (String key : List.of(clazz.nameKey(), clazz.roleKey(), clazz.descriptionKey(),
                        clazz.passiveKey(), clazz.skillKey()))
                    assertTrue(json.has(key), language + ":" + key);
            }
            assertTrue(json.has("echo.class.locked"));
            assertTrue(json.has("echo.class.selected"));
        }
    }
}
