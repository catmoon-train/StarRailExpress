package io.wifi.starrailexpress.compat.cobblemon;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CobblemonMechanicsPolicyTest {
    @Test void onlyCobblemonNamespaceIsDisabled() {
        assertTrue(CobblemonMechanics.isCobblemon(ResourceLocation.parse("cobblemon:healing_machine")));
        assertFalse(CobblemonMechanics.isCobblemon(ResourceLocation.parse("minecraft:brewing_stand")));
        assertFalse(CobblemonMechanics.isCobblemon(ResourceLocation.parse("starrailexpress:seat")));
        assertFalse(CobblemonMechanics.isCobblemon(null));
    }

    @Test void onlyAddedLootReferencesAreFiltered() {
        assertTrue(CobblemonMechanics.injectedLoot(ResourceLocation.parse("cobblemon:injection/chests/village_house")));
        assertTrue(CobblemonMechanics.injectedLoot(ResourceLocation.parse("cobblemon:injection/gameplay/fishing/treasure")));
        assertFalse(CobblemonMechanics.injectedLoot(ResourceLocation.parse("minecraft:chests/village/village_plains_house")));
        assertFalse(CobblemonMechanics.injectedLoot(ResourceLocation.parse("cobblemon:blocks/healing_machine")));
        assertFalse(CobblemonMechanics.injectedLoot(ResourceLocation.parse("other:injection/chests/village_house")));
    }

    @Test void gameplayHooksAreBlockedButSavingAndDeathCleanupAreRetained() {
        String prefix = "com.cobblemon.mod.common.platform.events.";
        for (String event : new String[]{"ServerPlayerTickEvent$Pre", "ServerPlayerTickEvent$Post",
                "ServerPlayerEvent$RightClickBlock", "ServerPlayerEvent$RightClickEntity", "ServerPlayerEvent$AdvancementEarned"}) {
            assertTrue(CobblemonMechanics.gameplayEvent(prefix + event), event);
        }
        for (String event : new String[]{"ServerEvent$Starting", "ServerEvent$Stopped", "ServerPlayerEvent$Login",
                "ServerPlayerEvent$Logout", "ServerPlayerEvent$Death", "ServerPlayerEvent$Respawn", "ChangeDimensionEvent",
                "ServerTickEvent$Pre", "ServerTickEvent$Post"}) {
            assertFalse(CobblemonMechanics.gameplayEvent(prefix + event), event);
        }
    }

    @Test void gameplayRequestsCannotPassForCleanup() {
        String prefix = "com.cobblemon.mod.common.net.messages.server.";
        for (String packet : new String[]{"SendOutPokemonPacket", "SelectStarterPacket", "BattleChallengePacket",
                "trade.PerformTradePacket", "riding.DamageOnCollisionPacket", "AcceptEvolutionPacket"}) {
            assertFalse(CobblemonMechanics.cleanupPacket(prefix + packet), packet);
        }
    }

    @Test void ClosingAndCancelRequestsStillPass() {
        String prefix = "com.cobblemon.mod.common.net.messages.server.";
        for (String packet : new String[]{"UnlinkPlayerFromPCPacket", "trade.CancelTradePacket", "riding.DismountPokemonPacket",
                "dialogue.EscapeDialoguePacket", "callback.partymove.PartyMoveSelectCancelledPacket"}) {
            assertTrue(CobblemonMechanics.cleanupPacket(prefix + packet), packet);
        }
        assertFalse(CobblemonMechanics.cleanupPacket("other.mod.CancelTradePacket"));
    }
}
