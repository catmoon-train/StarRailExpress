package org.agmas.noellesroles.game.halloween;

import com.google.gson.JsonParser;
import org.agmas.noellesroles.game.halloween.village.VillageInventory;
import org.agmas.noellesroles.game.halloween.village.VillageRules;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenIntegrationTest {
    @Test void combatStateMachineChecksRunInGradle() { HalloweenCombatStateTest.main(new String[0]); }

    @Test void knockingDistributionAndGlobalCooldownBoundaries() {
        int[] outcomes = new int[3];
        for (int roll = 0; roll < 100; roll++) outcomes[VillageRules.knockReward(roll)]++;
        assertArrayEquals(new int[]{60, 30, 10}, outcomes);
        assertThrows(IllegalArgumentException.class, () -> VillageRules.knockReward(100));
        assertFalse(VillageRules.cooldownReady(299, 300));
        assertTrue(VillageRules.cooldownReady(300, 300));
    }

    @Test void ghostNightHasExactStartEndAndRepeat() {
        assertFalse(VillageRules.isNight(2399, 2400, 1200, 3600));
        assertTrue(VillageRules.isNight(2400, 2400, 1200, 3600));
        assertTrue(VillageRules.isNight(3599, 2400, 1200, 3600));
        assertFalse(VillageRules.isNight(3600, 2400, 1200, 3600));
        assertTrue(VillageRules.isNight(6000, 2400, 1200, 3600));
    }

    private record Stack(String id, int count) {}
    private static final VillageInventory.Slots<Stack> SLOTS = new VillageInventory.Slots<>() {
        public int count(Stack s) { return s.count; }
        public int limit(Stack s) { return 64; }
        public boolean currency(Stack s) { return s.id.equals("pumpkin"); }
        public boolean matches(Stack a, Stack b) { return a.id.equals(b.id); }
        public Stack withCount(Stack s, int count) { return new Stack(s.id, count); }
    };

    @Test void failedPurchaseNeverMutatesInventoryAndPaymentCanFreeSlot() {
        var original = List.of(new Stack("pumpkin", 4), new Stack("stone", 64));
        assertNull(VillageInventory.plan(original, 3, new Stack("candy", 1), 1, SLOTS));
        assertEquals(4, original.getFirst().count);
        var bought = VillageInventory.plan(original, 4, new Stack("candy", 1), 2, SLOTS);
        assertNotNull(bought);
        assertEquals(new Stack("candy", 2), bought.getFirst());
        assertEquals(4, original.getFirst().count);
        assertNull(VillageInventory.plan(original, 5, new Stack("candy", 1), 1, SLOTS));
    }

    @Test void registeredMixinsAndItemResourcesExist() throws Exception {
        Path resources = Path.of("src/main/resources");
        var mixins = JsonParser.parseString(Files.readString(resources.resolve("noellesroles.mixins.json"))).getAsJsonObject();
        for (String section : List.of("mixins", "client")) {
            for (var entry : mixins.getAsJsonArray(section)) {
                String name = entry.getAsString();
                if (name.startsWith("halloween.")) assertTrue(Files.isRegularFile(Path.of(
                        "src/main/java/org/agmas/noellesroles/mixin", name.replace('.', '/') + ".java")), name);
            }
        }
        assertTrue(mixins.getAsJsonArray("client").asList().stream()
                .anyMatch(e -> e.getAsString().equals("halloween.village.VillageLightTextureMixin")));
        var language = JsonParser.parseString(Files.readString(resources.resolve("assets/halloween/lang/zh_cn.json"))).getAsJsonObject();
        for (String id : List.of("pumpkin_president", "blood_saw", "heart", "candy", "candy_bag", "pumpkin",
                "soul_link", "bone_sword", "coin", "fright_gun", "soul_contract", "pumpkin_helmet",
                "pumpkin_chestplate", "pumpkin_leggings", "pumpkin_boots")) {
            assertTrue(language.has("item.noellesroles.halloween_" + id), id);
            assertTrue(Files.isRegularFile(resources.resolve("assets/noellesroles/models/item/halloween_" + id + ".json")), id);
        }
    }
}
