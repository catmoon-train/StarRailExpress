package org.agmas.noellesroles.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VirtualHealthHudStateTest {
    @Test
    void fullHealthHiddenAndInitialSyncDoesNotCountAsDamage() {
        var state = new VirtualHealthHudState();
        assertFalse(state.visible());
        state.tick(20, 20);
        assertFalse(state.visible());
        assertEquals(0F, state.damageStrength(0));
        state.reset();
        state.tick(12, 20);
        assertTrue(state.visible());
        assertFalse(state.flashing());
        assertEquals(0F, state.damageStrength(0));
    }

    @Test
    void damageFlashesAndRedEdgesFadeThenRepeatedDamageRestartsThem() {
        var state = new VirtualHealthHudState();
        state.tick(20, 20);
        state.tick(15, 20);
        assertTrue(state.visible());
        assertTrue(state.flashing());
        assertEquals(1F, state.damageStrength(0));
        state.tick(15, 20);
        assertTrue(state.damageStrength(0.5F) < state.damageStrength(0));
        state.tick(15, 20);
        assertFalse(state.flashing());
        state.tick(10, 20);
        assertEquals(1F, state.damageStrength(0));
        for (int i = 0; i < VirtualHealthHudState.DAMAGE_TICKS; i++) {
            state.tick(10, 20);
        }
        assertEquals(0F, state.damageStrength(0));
        assertFalse(state.flashing());
        assertTrue(state.visible());
    }

    @Test
    void healingFlashesWithoutDamageAndFullHealthHidesImmediately() {
        var state = new VirtualHealthHudState();
        state.tick(10, 20);
        state.tick(11, 20);
        assertTrue(state.flashing());
        assertTrue(state.visible());
        assertEquals(0F, state.damageStrength(0));
        state.tick(20, 20);
        assertFalse(state.visible());
        assertEquals(0F, state.damageStrength(0));
    }

    @Test
    void resetAndMaxHealthChangesDoNotCreateDamageFeedback() {
        var state = new VirtualHealthHudState();
        state.tick(20, 20);
        state.tick(10, 20);
        state.reset();
        assertFalse(state.visible());
        assertFalse(state.flashing());
        assertEquals(0F, state.damageStrength(0));
        state.tick(20, 20);
        state.tick(10, 10);
        assertFalse(state.visible());
        assertFalse(state.flashing());
        assertEquals(0F, state.damageStrength(0));
    }
}
