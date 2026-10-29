package org.agmas.noellesroles.game.modes.echo.state;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** 机器投影、三语及原生模型资源的离线回归，不启动 Minecraft 注册表。 */
class EchoMachineViewTest {
    private EchoMachineView machine(EchoMachineView.Status status, int remaining) {
        return new EchoMachineView(12, 64, -4, EchoMachineView.Type.BEACON, "b", status, 3, 3, 999, remaining);
    }
    private JsonObject json(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }

    @Test void timersFreezeAndNeverTurnIntoLocalSuccess() {
        var cooldown = machine(EchoMachineView.Status.COOLDOWN, 40);
        assertEquals(2, cooldown.seconds(-1, false));
        assertEquals(2, cooldown.seconds(1000, true));
        assertEquals(2, cooldown.seconds(1000, false));
        var almost = machine(EchoMachineView.Status.COOLDOWN, 1);
        assertEquals(0, almost.seconds(10, false));
        assertFalse(almost.status().actionable());
        assertEquals(EchoMachineView.Status.COOLDOWN, almost.status());
        assertTrue(machine(EchoMachineView.Status.READY, 0).status().actionable());
    }
    @Test void boundedProjectionRejectsInvalidNetworkValues() {
        assertThrows(IllegalArgumentException.class, () -> machine(EchoMachineView.Status.READY, -1));
        assertThrows(IllegalArgumentException.class, () -> machine(EchoMachineView.Status.READY, 720001));
        assertThrows(IllegalArgumentException.class, () -> new EchoMachineView(Integer.MIN_VALUE,64,0,EchoMachineView.Type.BEACON,"x",EchoMachineView.Status.READY,0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new EchoMachineView(0,64,0,EchoMachineView.Type.BEACON,"x".repeat(65),EchoMachineView.Status.READY,0,0,0,0));
        assertThrows(IllegalArgumentException.class, () -> new EchoMachineView(0,64,0,EchoMachineView.Type.BEACON,"x",EchoMachineView.Status.READY,4,3,0,0));
        assertThrows(IllegalArgumentException.class, () -> new EchoMachineView(0,64,0,EchoMachineView.Type.BEACON,"x",EchoMachineView.Status.READY,0,3,1001,0));
    }
    @Test void allNamesStatusesAndActionsHaveMatchingThreeLanguageKeys() throws Exception {
        Set<String> expected = null;
        for (String language : new String[]{"zh_cn", "zh_tw", "en_us"}) {
            var lang = json(Path.of("src/main/resources/assets/echo_machines/lang/" + language + ".json"));
            if (expected == null) expected = new HashSet<>(lang.keySet()); else assertEquals(expected, lang.keySet());
            for (var type : EchoMachineView.Type.values()) assertTrue(lang.has(type.nameKey()), type.nameKey());
            for (var status : EchoMachineView.Status.values()) assertTrue(lang.has(status.key()), status.key());
            for (String action : new String[]{"collect", "key", "search", "unlock", "leave", "deliver_black", "submit", "stall", "interact", "overload"})
                assertTrue(lang.has("echo.machine.action." + action), action);
            for (var entry : lang.entrySet()) assertFalse(entry.getValue().getAsString().isBlank(), entry.getKey());
        }
    }
    @Test void everyMachineHasFourFacingsDetailedModelItemLootAndOpaqueTextures() throws Exception {
        Path assets = Path.of("src/main/resources/assets/noellesroles");
        Set<String> large = Set.of("echo_beacon", "echo_core_beacon", "echo_crystal_extractor",
                "echo_black_crystal_extractor", "echo_key_altar");
        Set<Integer> smallParts = Set.of(0, 1, 3, 4, 9, 10, 12, 13);
        Set<String> checked = new HashSet<>();
        for (var type : EchoMachineView.Type.values()) {
            String id = type.path();
            boolean wide = large.contains(id);
            var variants = json(assets.resolve("blockstates/" + id + ".json")).getAsJsonObject("variants");
            assertEquals(72, variants.size(), id);
            String[] directions = {"north", "east", "south", "west"};
            for (int i = 0; i < 4; i++) {
                for (int part = 0; part < 18; part++) {
                    var variant = variants.getAsJsonObject("facing=" + directions[i] + ",part=" + part);
                    assertNotNull(variant, id + " facing=" + directions[i] + ",part=" + part);
                    boolean occupied = wide || smallParts.contains(part);
                    String reference = "noellesroles:block/" + id + (occupied ? "_part" + part : "_empty");
                    assertEquals(reference, variant.get("model").getAsString(), id);
                    assertEquals(i * 90, variant.has("y") ? variant.get("y").getAsInt() : 0, id);
                    if (!occupied) continue;
                    var partModel = json(assets.resolve("models/block/" + id + "_part" + part + ".json"));
                    assertFalse(partModel.getAsJsonArray("elements").isEmpty(), id + " part " + part);
                    checkGeometry(id + "#part" + part, partModel, assets, checked, 0, 16);
                }
            }
            if (!wide) {
                var empty = json(assets.resolve("models/block/" + id + "_empty.json"));
                assertEquals(0, empty.getAsJsonArray("elements").size(), id);
                checkTextures(id + "#empty", empty, assets, checked);
            }
            assertEquals("noellesroles:block/" + id, json(assets.resolve("models/item/" + id + ".json")).get("parent").getAsString());
            var model = json(assets.resolve("models/block/" + id + ".json"));
            assertTrue(model.getAsJsonArray("elements").size() >= 30, id);
            checkGeometry(id, model, assets, checked, -16, 32);
            var loot = json(Path.of("src/main/resources/data/noellesroles/loot_table/blocks/" + id + ".json"));
            assertTrue(loot.toString().contains("noellesroles:" + id), id);
            assertFalse(loot.toString().contains("block_state_property"), id);
        }
        assertEquals(12, EchoMachineView.Type.values().length);
    }

    private void checkGeometry(String id, JsonObject model, Path assets, Set<String> checked, double min, double max) throws Exception {
        var textures = model.getAsJsonObject("textures");
        assertTrue(textures.has("particle"), id);
        for (var element : model.getAsJsonArray("elements")) {
            var e = element.getAsJsonObject();
            for (int axis = 0; axis < 3; axis++) {
                double from = e.getAsJsonArray("from").get(axis).getAsDouble(), to = e.getAsJsonArray("to").get(axis).getAsDouble();
                double lo = axis == 1 ? 0 : min, hi = axis == 1 ? 32 : max;
                if (!id.contains("#part")) {
                    assertTrue(from >= lo && to <= hi && from < to, id);
                } else {
                    assertTrue(from >= 0 && to <= 16 && from < to, id);
                }
            }
            assertFalse(e.has("rotation"), id);
            for (var face : e.getAsJsonObject("faces").entrySet()) {
                var f = face.getValue().getAsJsonObject();
                var uv = f.getAsJsonArray("uv");
                assertEquals(4, uv.size(), id);
                assertTrue(uv.get(0).getAsDouble() < uv.get(2).getAsDouble() && uv.get(1).getAsDouble() < uv.get(3).getAsDouble(), id);
                for (var value : uv) assertTrue(value.getAsDouble() >= 0 && value.getAsDouble() <= 16, id);
                assertTrue(textures.has(f.get("texture").getAsString().substring(1)), id);
            }
        }
        checkTextures(id, model, assets, checked);
    }

    private void checkTextures(String id, JsonObject model, Path assets, Set<String> checked) throws Exception {
        var textures = model.getAsJsonObject("textures");
        for (var texture : textures.entrySet()) {
            String ref = texture.getValue().getAsString();
            while (ref.startsWith("#")) ref = textures.get(ref.substring(1)).getAsString();
            assertTrue(ref.startsWith("noellesroles:"), ref);
            if (!checked.add(ref)) continue;
            var image = ImageIO.read(assets.resolve("textures/" + ref.substring(ref.indexOf(':') + 1) + ".png").toFile());
            assertNotNull(image, ref);
            assertTrue(image.getWidth() == 32 || image.getWidth() == 64, ref);
            assertEquals(image.getWidth(), image.getHeight(), ref);
            for (int x = 0; x < image.getWidth(); x++) for (int y = 0; y < image.getHeight(); y++) assertEquals(255, image.getRGB(x, y) >>> 24, ref);
        }
    }
}
