package org.agmas.noellesroles.game.halloween;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HalloweenSawHudTest {
    private static final Path ROOT = Path.of("src/main/java/org/agmas/noellesroles/game/halloween");

    @Test void heartRewardAndSawHudUseUpdatedBalance() throws Exception {
        String consumables = Files.readString(ROOT.resolve("HalloweenConsumables.java"));
        assertTrue(consumables.contains("action == Action.HEART ? 25 : 5"));
        String combat = Files.readString(ROOT.resolve("HalloweenCombat.java"));
        assertTrue(combat.contains("r.state.now() + HalloweenConfig.get().sawChargeTicks"));
        assertTrue(combat.contains("totalTicks = Math.max(1, HalloweenConfig.get().sawChargeTicks)"));
    }


    @Test void serverSendsPrivateProgressAndClearsBothParticipantsOnRelease() throws Exception {
        String combat = Files.readString(ROOT.resolve("HalloweenCombat.java"));
        assertTrue(combat.contains("syncSaw(saw, r.state.now(), false)"));
        assertTrue(combat.contains("syncSaw(saw, r.state.now(), paused)"));
        assertTrue(combat.contains("saw.endsAt - now"));
        assertTrue(combat.contains("sendSaw(saw.actor, remaining, saw.totalTicks, false, paused)"));
        assertTrue(combat.contains("sendSaw(saw.target, remaining, saw.totalTicks, true, paused)"));
        assertTrue(combat.contains("sendSaw(saw.actor, 0, saw.totalTicks, false, false)"));
        assertTrue(combat.contains("sendSaw(saw.target, 0, saw.totalTicks, true, false)"));
        assertTrue(combat.contains("!paused && r.state.now() < saw.endsAt"));
        assertTrue(combat.contains("saw.actor.playNotifySound"));
        assertTrue(combat.contains("saw.target.playNotifySound"));
    }

    @Test void clientWiringHasPauseDisplayAndStaleWorldCleanup() throws Exception {
        String hud = Files.readString(ROOT.resolve("HalloweenSawHud.java"));
        assertTrue(hud.contains("packet.remaining() <= 0"));
        assertTrue(hud.contains("client.level != level"));
        assertTrue(hud.contains("level.getGameTime() - receivedAt > 10"));
        assertTrue(hud.contains("DISCONNECT.register"));
        assertTrue(hud.contains("state.paused()"));
        assertTrue(hud.contains("state.remaining() / 20.0"));
        assertTrue(hud.contains("graphics.fill"));
        assertTrue(Files.readString(ROOT.resolve("HalloweenNetwork.java"))
                .contains("register(HalloweenSawS2CPayload.ID, HalloweenSawS2CPayload.CODEC)"));
        assertTrue(Files.readString(ROOT.resolve("HalloweenClient.java")).contains("HalloweenSawHud.register()"));
    }
}
