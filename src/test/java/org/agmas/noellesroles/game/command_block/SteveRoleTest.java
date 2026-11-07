package org.agmas.noellesroles.game.command_block;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.wifi.starrailexpress.api.data.RoleDataContext;

class SteveRoleTest {
    private SteveRoleData data() {
        return new SteveRoleData(new RoleDataContext(null, null, null, null));
    }

    @Test
    void duplicateCompletionsCannotProduceExtraIronHarvests() {
        var data = data();
        assertTrue(data.tryHarvest(100, 20));
        assertFalse(data.tryHarvest(100, 20));
        assertFalse(data.tryHarvest(119, 20));
        assertTrue(data.tryHarvest(120, 20));
    }

    @Test
    void diamondUsesTenTicksAndRejectedPacketsDoNotExtendTheInterval() {
        var data = data();
        assertTrue(data.tryHarvest(0, 10));
        assertFalse(data.tryHarvest(9, 10));
        assertTrue(data.tryHarvest(10, 10));
    }

    @Test
    void miningCooldownIsPerPlayer() {
        var first = data();
        var second = data();
        assertTrue(first.tryHarvest(1, 20));
        assertTrue(second.tryHarvest(1, 20));
        assertFalse(first.tryHarvest(2, 10));
    }

    @Test
    void clientPredictionMixinIsNeverLoadedOnDedicatedServers() throws Exception {
        var mixins = json("noellesroles.mixins.json");
        String client = "client.SteveMiningPredictionMixin";
        assertTrue(mixins.getAsJsonArray("client").asList().stream().anyMatch(e -> client.equals(e.getAsString())));
        assertFalse(mixins.getAsJsonArray("mixins").asList().stream().anyMatch(e -> client.equals(e.getAsString())));
        for (String common : List.of("StevePickaxeBreakMixin", "SteveMiningPermissionMixin", "SteveMiningProgressMixin")) {
            assertTrue(mixins.getAsJsonArray("mixins").asList().stream().anyMatch(e -> common.equals(e.getAsString())));
        }
    }

    @Test
    void removedLeverHasNoRegistrationShopEntryOrModel() throws Exception {
        Path source = Path.of("src/main/java/org/agmas/noellesroles");
        assertFalse(Files.readString(source.resolve("init/ModItems.java")).contains("STEVE_LEVER"));
        assertFalse(Files.readString(source.resolve("game/command_block/SteveRole.java")).contains("STEVE_LEVER"));
        assertFalse(Files.readString(source.resolve("game/command_block/CommandBlockDirector.java")).contains("rerollNext"));
        assertFalse(Files.exists(source.resolve("game/command_block/item/SteveLeverItem.java")));
        assertFalse(Files.exists(Path.of("src/main/resources/assets/noellesroles/models/item/steve_lever.json")));
    }

    @Test
    void materialsAreVirtualIndependentAndOnlySpentWhenAffordable() {
        var first = data();
        var second = data();
        first.add(SteveMaterial.IRON, 7);
        first.add(SteveMaterial.DIAMOND, 2);
        assertEquals(0, second.count(SteveMaterial.IRON));
        assertFalse(first.take(SteveMaterial.IRON, 8));
        assertFalse(first.take(SteveMaterial.IRON, 0));
        assertFalse(first.take(SteveMaterial.IRON, -1));
        assertEquals(7, first.count(SteveMaterial.IRON));
        assertTrue(first.take(SteveMaterial.IRON, 5));
        assertEquals(2, first.count(SteveMaterial.IRON));
        assertEquals(2, first.count(SteveMaterial.DIAMOND));
    }

    @Test
    void materialCountsSyncAndInvalidBalancesNeverGoNegativeOrOverflow() {
        var first = data();
        first.add(SteveMaterial.PLANKS, Integer.MAX_VALUE);
        first.add(SteveMaterial.PLANKS, 1);
        first.add(SteveMaterial.GOLD, -4);
        assertEquals(Integer.MAX_VALUE, first.count(SteveMaterial.PLANKS));
        assertEquals(0, first.count(SteveMaterial.GOLD));
        var tag = new net.minecraft.nbt.CompoundTag();
        first.writeToSyncNbt(tag, null);
        tag.putInt(SteveMaterial.GOLD.key(), -1);
        var copy = data();
        copy.readFromSyncNbt(tag, null);
        assertEquals(Integer.MAX_VALUE, copy.count(SteveMaterial.PLANKS));
        assertEquals(0, copy.count(SteveMaterial.GOLD));
    }

    @Test
    void fortyEquallyLikelyRollsGiveExactlyThirtyPercentEmptyAndEqualMaterials() {
        int empty = 0;
        int[] counts = new int[SteveMaterial.values().length];
        for (int roll = 0; roll < 40; roll++) {
            SteveMaterial material = SteveMiner.materialDrop(roll);
            if (material == null) empty++;
            else counts[material.ordinal()]++;
        }
        assertEquals(12, empty);
        assertArrayEquals(new int[] {7, 7, 7, 7}, counts);
        assertThrows(IllegalArgumentException.class, () -> SteveMiner.materialDrop(-1));
        assertThrows(IllegalArgumentException.class, () -> SteveMiner.materialDrop(40));
    }

    @Test
    void miningDoesNotCreateStacksAndFailedDeliveryPrecedesMaterialDeduction() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/org/agmas/noellesroles/game/command_block/SteveMiner.java"));
        assertFalse(source.contains("getInventory().add(drop)"));
        assertFalse(source.contains("player.drop("));
        assertTrue(source.contains("data.add(material, 1)"));
        assertTrue(source.contains("pick.hurtAndBreak(1"));
        assertTrue(source.indexOf("insertStackInFreeSlot") < source.indexOf("take(player, cost, amount)"));
    }

    @Test
    void nullRemovesOnlyVictimBodiesAndDetonatorCostsFifty() throws Exception {
        Path root = Path.of("src/main/java/org/agmas/noellesroles/game/command_block");
        String director = Files.readString(root.resolve("CommandBlockDirector.java"));
        int hook = director.indexOf("OnPlayerDeathWithBody.EVENT.register");
        assertTrue(hook >= 0);
        String bodyHook = director.substring(hook, director.indexOf("ServerLivingEntityEvents.ALLOW_DAMAGE", hook));
        assertTrue(bodyHook.contains("killer != victim"));
        assertTrue(bodyHook.contains("ModRoles.ENTITY_NULL"));
        assertTrue(bodyHook.contains("body.discard()"));
        assertTrue(Files.readString(root.resolve("Error404Role.java"))
                .contains("ERROR_DETONATOR.getDefaultInstance(), 50, ShopEntry.Type.TOOL"));
    }

    private JsonObject json(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources", relative))).getAsJsonObject();
    }
}
