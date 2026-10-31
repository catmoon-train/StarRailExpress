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

    @Test void pactCannotAttackAndOnlyOneContractCanBeAcceptedAcrossWorlds() throws Exception {
        String combat = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenCombat.java"));
        String attack = combat.substring(combat.indexOf("private static InteractionResult attack("),
                combat.indexOf("private static void attemptKill("));
        assertTrue(attack.contains("if (isActionLocked(player) || HalloweenPactRole.isPact(player)) return InteractionResult.FAIL;"));
        assertFalse(attack.contains("GameUtils.killPlayer"));
        assertFalse(combat.contains("AllowPlayerPunching.EVENT.register"));
        assertTrue(combat.contains("e.getKey().getServer() == p.getServer()"));
        assertTrue(combat.contains("e.getValue().state.hasAnyAcceptedPact()"));
    }

    @Test void staminaProgressionOnlyReceivesServerPlayers() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/main/java/io/wifi/starrailexpress/mixin/entity/player/PlayerEntityMixin.java"));
        assertTrue(mixin.contains("if ((Object) this instanceof ServerPlayer serverPlayer)"));
        assertTrue(mixin.contains("ProgressionDataManager.onStaminaConsumed(serverPlayer, 1)"));
        assertFalse(mixin.contains("onStaminaConsumed((ServerPlayer) (Object) this"));
    }

    @Test void knockingDistributionAndCooldownBoundaries() {
        int[] outcomes = new int[3];
        for (int roll = 0; roll < 100; roll++) outcomes[VillageRules.knockReward(roll)]++;
        assertArrayEquals(new int[]{60, 30, 10}, outcomes);
        assertThrows(IllegalArgumentException.class, () -> VillageRules.knockReward(100));
        assertFalse(VillageRules.cooldownReady(299, 300));
        assertTrue(VillageRules.cooldownReady(300, 300));
    }

    @Test void ghostNightEventCannotActivateOrRenderEffects() throws Exception {
        String village = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenVillage.java"));
        String client = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenVillageClient.java"));
        assertTrue(village.contains("isGhostNight(Level level) { return false; }"));
        assertFalse(village.contains("VillageRules.isNight("));
        assertFalse(village.contains("VillageGhostVoice.publish("));
        assertFalse(village.contains("message(player, \"night_start\")"));
        assertTrue(client.contains("shadeLightmap(int color) { return color; }"));
        assertFalse(client.contains("WorldRenderEvents"));
        assertFalse(client.contains("packet.active()"));
        assertFalse(client.contains("VillageGhostAudio.setAllowed"));
    }

    @Test void knockingUsesPersonalCooldownVanillaTitleAndSuccessSound() throws Exception {
        String village = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenVillage.java"));
        assertTrue(village.contains("Map<MinecraftServer, Map<UUID, Long>> NEXT_KNOCK"));
        assertTrue(village.contains("NEXT_KNOCK.computeIfAbsent(level.getServer()"));
        assertTrue(village.contains("nextKnocks.getOrDefault(player.getUUID(), 0L)"));
        assertTrue(village.contains("nextKnocks.put(player.getUUID(), now + Math.max(1, config.knockCooldownTicks))"));
        assertTrue(village.contains("SRENetworkMessageUtils.sendTitle(player, Component.translatable(\"halloween.village.\" + key, args))"));
        assertTrue(village.contains("SRENetworkMessageUtils.sendTitleTime(player, 5, 40, 10)"));
        assertTrue(village.contains("SRENetworkMessageUtils.sendSubtitle(player, Component.empty())"));
        assertTrue(village.contains("player.playNotifySound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR"));
        assertTrue(village.indexOf("if (!VillageRules.cooldownReady(now, next))")
                < village.indexOf("player.playNotifySound(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR"));
        assertFalse(village.contains("player.displayClientMessage(Component.translatable(\"halloween.village.\" + key, args), true)"));
        assertFalse(village.contains("SubtitleS2CPayload"));
        assertTrue(village.contains("NEXT_KNOCK.remove(level.getServer())"));
    }

    /** 固定残月只认地图坐标；(0,0,0) 等于未设置，旧的双南瓜灯扫描已移除。 */
    @Test void fixedReaperSpawnPositionComesFromMapSettingsOnly() throws Exception {
        assertFalse(VillageRules.isSpawnConfigured(0, 0, 0));
        assertTrue(VillageRules.isSpawnConfigured(35073, 20, 20000));
        assertTrue(VillageRules.isSpawnConfigured(0, 20, 0));
        assertFalse(Files.exists(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/village/VillageScanner.java")));
    }

    @Test void npcOwnershipIsRegisteredBeforeWorldInsertionAndSummonsAreNotAutoDiscarded() throws Exception {
        String village = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/HalloweenVillage.java"));
        String npc = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/halloween/village/VillageNpc.java"));
        assertTrue(npc.contains("eventManaged && !HalloweenVillage.owns(this)"));
        assertTrue(village.contains("npc.markEventManaged()"));
        assertTrue(village.indexOf("state.reapers.put(state.reaperKey, created)")
                < village.indexOf("level.addFreshEntity(created)"));
        assertTrue(village.indexOf("state.candy.put(candy,")
                < village.indexOf("level.addFreshEntity(candy)"));
        String factory = village.substring(village.indexOf("private static VillageNpc createNpc"),
                village.indexOf("private static void openShop"));
        assertFalse(factory.contains("addFreshEntity"));
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

    @Test void reducedRewardMayBeZeroWithoutChangingOriginalPaymentPlan() {
        var original = List.of(new Stack("pumpkin", 4), new Stack("stone", 64));
        assertNull(VillageInventory.plan(original, 2, new Stack("pumpkin", 1), 0, SLOTS));
        var plan = VillageInventory.plan(original, 2, new Stack("pumpkin", 1), 0, SLOTS, true);
        assertNotNull(plan);
        assertEquals(2, plan.getFirst().count);
        assertEquals(4, original.getFirst().count);
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
