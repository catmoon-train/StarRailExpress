package org.agmas.noellesroles.game.roles.neutral.silver_wing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilverWingRulesTest {
    @Test
    void skillAndEmpMatchTheRoleDoc() {
        assertEquals(50, SilverWingRules.EMP_SKILL_COOLDOWN_SECONDS);
        assertEquals(15, SilverWingRules.EMP_USE_COOLDOWN_SECONDS);
        assertEquals(1, SilverWingRules.EMP_MAX_OWNED);
        assertEquals(4, SilverWingRules.EMP_ITEM_BAN_SECONDS);
        assertEquals(4, SilverWingRules.EMP_SLOWNESS_SECONDS);
        assertEquals(2, SilverWingRules.EMP_SLOWNESS_AMPLIFIER);
        assertEquals(50, SilverWingRules.TASK_GOLD);
    }

    @Test
    void birdShopAndExplosionMatchTheRoleDoc() {
        assertEquals(125, SilverWingRules.BIRD_SHOP_PRICE);
        assertEquals(25, SilverWingRules.BIRD_LIFETIME_SECONDS);
        assertEquals(2.5D, SilverWingRules.BIRD_EXPLOSION_RADIUS);
        assertEquals(3, SilverWingRules.BIRD_BLINDNESS_SECONDS);
        assertEquals(3, SilverWingRules.BIRD_ITEM_BAN_SECONDS);
        assertEquals(8, SilverWingRules.BIRD_SKILL_BAN_SECONDS);
        assertEquals(0.30F, SilverWingRules.BIRD_MOOD_DRAIN);
        assertEquals(25, SilverWingRules.BIRD_GOLD_PENALTY);
        assertEquals(0.75F, SilverWingRules.BIRD_SHIELD_BREAK_CHANCE);
        assertEquals(3, SilverWingRules.BIRD_DASH_EXPLODE_SECONDS);
        assertEquals(1.0F, SilverWingRules.BIRD_HURT_DAMAGE);
    }

    @Test
    void empBombIsCappedAtOne() {
        assertFalse(SilverWingRules.alreadyHasEmpBomb(0));
        assertTrue(SilverWingRules.alreadyHasEmpBomb(1));
        assertTrue(SilverWingRules.alreadyHasEmpBomb(2));
    }

    @Test
    void throwingEmpOnlyStartsFifteenSecondCooldownWhenSkillIsReady() {
        assertTrue(SilverWingRules.shouldApplyEmpUseCooldown(false));
        assertFalse(SilverWingRules.shouldApplyEmpUseCooldown(true));
    }

    @Test
    void birdExplosionUsesExactRadiusAndAuraIsLarger() {
        assertTrue(SilverWingRules.isWithinExplosion(2.5D * 2.5D));
        assertFalse(SilverWingRules.isWithinExplosion(2.5D * 2.5D + 0.01D));
        assertTrue(SilverWingRules.isWithinAura(4.0D * 4.0D));
        assertFalse(SilverWingRules.isWithinAura(4.0D * 4.0D + 0.01D));
    }

    @Test
    void shieldBreakUsesSeventyFivePercentChance() {
        assertTrue(SilverWingRules.shouldBreakShield(0.0F));
        assertTrue(SilverWingRules.shouldBreakShield(0.749F));
        assertFalse(SilverWingRules.shouldBreakShield(0.75F));
        assertFalse(SilverWingRules.shouldBreakShield(1.0F));
    }

    @Test
    void explosionDrainsThirtyPercentSanAndTwentyFiveGoldWithoutGoingNegative() {
        assertEquals(0.7F, SilverWingRules.drainMood(1.0F), 0.0001F);
        assertEquals(0.0F, SilverWingRules.drainMood(0.2F), 0.0001F);
        assertEquals(75, SilverWingRules.deductGold(100));
        assertEquals(0, SilverWingRules.deductGold(10));
        assertEquals(0, SilverWingRules.deductGold(0));
    }

    @Test
    void explosionKnockbackPushesAwayFromTheBlast() {
        double[] side = SilverWingRules.horizontalKnockback(2.0D, 0.0D);
        assertEquals(SilverWingRules.BIRD_KNOCKBACK, side[0], 0.0001D);
        assertEquals(0.0D, side[1], 0.0001D);
        double[] centered = SilverWingRules.horizontalKnockback(0.0D, 0.0D);
        assertEquals(SilverWingRules.BIRD_KNOCKBACK, centered[0], 0.0001D);
        assertEquals(0.0D, centered[1], 0.0001D);
    }
}
