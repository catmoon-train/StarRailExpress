package org.agmas.noellesroles.game.minigame.roulette;

import java.nio.file.Files;
import java.nio.file.Path;
import org.agmas.noellesroles.client.roulette.RouletteHudLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouletteBalanceAndHudTest {
    @Test void allNewMatchesShareThreeAndFourHealth() {
        assertEquals(3, RouletteBalance.HEALTH); assertEquals(4, RouletteBalance.DEVIL_HEALTH);
        var idle = RouletteView.idle();
        assertEquals(3, idle.frontHealth()); assertEquals(3, idle.frontMaxHealth());
        assertEquals(3, idle.backHealth()); assertEquals(3, idle.backMaxHealth());
    }
    @Test void supplyIsSeventyPercentRoundedAndDoesNotReduceCapacity() {
        assertEquals(0, RouletteBalance.supply(0));
        assertEquals(1, RouletteBalance.supply(2)); assertEquals(2, RouletteBalance.supply(3));
        assertEquals(3, RouletteBalance.supply(4)); assertEquals(4, RouletteBalance.supply(5));
        assertEquals(7, RouletteBalance.supply(10));
        assertEquals(8, RouletteItems.CAPACITY);
    }
    @Test void compactHudStaysInLowerHalfAndClearOfHotbar() {
        for (int width : new int[]{320, 480, 960}) for (int height : new int[]{240, 360, 540}) {
            for (int rows = 0; rows <= 3; rows++) {
                var box = RouletteHudLayout.bottom(width, height, rows);
                assertTrue(box.width() <= 300); assertTrue(box.height() <= 71);
                assertTrue(box.x() >= 8); assertTrue(box.x() + box.width() <= width - 8);
                assertTrue(box.y() >= height / 2);
                assertTrue(box.y() + box.height() <= height - 44);
            }
        }
    }
    @Test void hudRowsAreBoundedEvenWithInvalidCounts() {
        assertEquals(RouletteHudLayout.bottom(480, 240, 0), RouletteHudLayout.bottom(480, 240, -9));
        assertEquals(RouletteHudLayout.bottom(480, 240, 3), RouletteHudLayout.bottom(480, 240, 999));
    }
    @Test void tableSupplyAndPresentationAreWiredToSharedPolicies() throws Exception {
        Path root = Path.of("src/main/java");
        String table = Files.readString(root.resolve("org/agmas/noellesroles/content/block_entity/DevilRouletteTableEntity.java"));
        assertTrue(table.contains("startMatch(RouletteBalance.DEVIL_HEALTH, RouletteBalance.HEALTH"));
        assertTrue(table.contains("startMatch(RouletteBalance.HEALTH, RouletteBalance.HEALTH, null)"));
        String supplies = Files.readString(root.resolve("org/agmas/noellesroles/game/minigame/roulette/RouletteSupply.java"));
        assertTrue(supplies.contains("RouletteBalance.supply(requested)"));
        String presentation = Files.readString(root.resolve("org/agmas/noellesroles/client/roulette/RoulettePresentation.java"));
        assertTrue(presentation.contains("ClientGameplayEvents.ALLOW_AUXILIARY_HUD.register"));
        assertTrue(presentation.contains("ClientGameplayEvents.WORLD_FOV.register"));
        assertFalse(presentation.contains("options.fov().set"));
        String smoke = Files.readString(root.resolve("org/agmas/noellesroles/client/RouletteDevilSmokeRenderer.java"));
        assertTrue(smoke.contains("ParticleTypes.SMOKE")); assertTrue(smoke.contains("particle.setColor"));
        assertFalse(smoke.contains("shadow.png")); assertFalse(smoke.contains("VertexConsumer"));
    }
}
