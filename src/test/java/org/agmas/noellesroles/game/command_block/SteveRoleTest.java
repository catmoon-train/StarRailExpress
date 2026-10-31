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

    private JsonObject json(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources", relative))).getAsJsonObject();
    }
}
