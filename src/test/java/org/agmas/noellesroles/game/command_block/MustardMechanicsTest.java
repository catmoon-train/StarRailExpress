package org.agmas.noellesroles.game.command_block;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 无世界环境验证芥末数值和关键接线；多人复活与动画需游戏内验收。 */
class MustardMechanicsTest {
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/org/agmas/noellesroles", relative));
    }

    @Test
    void damageStartsAtFiveAndEachUpgradeAddsFiveWithoutWrapping() {
        assertEquals(5, MustardRules.puppetDamage(0));
        assertEquals(10, MustardRules.puppetDamage(1));
        assertEquals(55, MustardRules.puppetDamage(10));
        assertEquals(5, MustardRules.puppetDamage(-1));
        assertEquals(Integer.MAX_VALUE, MustardRules.puppetDamage(Integer.MAX_VALUE));
    }

    @Test
    void guardExpiresExactlyAfterThirtySecondsAndConsumptionClearsIt() {
        long start = 100;
        long expiresAt = start + MustardRules.GUARD_TICKS;
        assertTrue(MustardRules.guardActive(expiresAt, start));
        assertTrue(MustardRules.guardActive(expiresAt, expiresAt - 1));
        assertFalse(MustardRules.guardActive(expiresAt, expiresAt));
        assertFalse(MustardRules.guardActive(0, start));
        assertEquals(600, MustardRules.GUARD_TICKS);
        assertEquals(120, MustardRules.SKILL_COOLDOWN_SECONDS);
        assertEquals(100, MustardRules.CORE_PRICE);
        assertEquals(25, MustardRules.CORE_PRICE_STEP);
    }

    @Test
    void fogRoleIsRemovedWhileItemsRemainUsable() throws Exception {
        assertFalse(source("role/ModRoles.java").contains("FOG_GUEST"));
        assertTrue(source("init/ModItems.java").contains("FOG_BELL"));
        assertTrue(source("init/ModItems.java").contains("FOG_BLADE"));
        assertFalse(source("game/command_block/item/FogBellItem.java").contains("isRole("));
        assertFalse(source("game/command_block/item/FogBladeItem.java").contains("FogGuestRole"));
        String pool = source("game/command_block/CommandBlockRules.java");
        assertTrue(pool.contains("ModRoles.MUSTARD"));
        assertFalse(pool.contains("ModRoles.MUSTARD_PUPPET"));
    }

    @Test
    void guardUsesAllThreeDamagePathsAndPreservesVirtualShieldFlags() throws Exception {
        String role = source("game/command_block/MustardRole.java");
        assertTrue(role.contains("OnKillPlayerTriggered.EVENT.register"));
        assertTrue(role.contains("data.guardUntil = 0;"));
        assertTrue(role.contains("Comparator.comparingDouble(owner::distanceToSqr)"));
        assertTrue(source("game/command_block/CommandBlockCombat.java").contains("redirectNativeDamage"));
        assertTrue(source("game/roles/killer/dream/DreamHealthComponent.java").contains("redirectVirtualDamage"));
        assertTrue(role.contains("hurt(attacker, amount, reason, ignoreVirtualShield, pierceOtherShields)"));
        assertTrue(role.contains("ShopEvents.BEFORE_PAYMENT.register"));
        assertTrue(role.contains("setSteppedPrice"));
        assertTrue(role.indexOf("puppets.isEmpty()") < role.indexOf("data.guardUntil = GameUtils"));
    }

    @Test
    void puppetsUsePlayerRevivalAndTimeBasedIronGolemAnimation() throws Exception {
        String role = source("game/command_block/MustardRole.java");
        assertTrue(role.contains("GameUtils.revivePlayer(revived"));
        assertTrue(role.contains("EntityType.IRON_GOLEM"));
        assertTrue(role.contains("owner.getUUID().equals(data.owner)"));
        assertTrue(role.contains("broadcastEntityEvent(actor, (byte) 4)"));
        assertTrue(source("client/MustardPuppetAnimation.java").contains("getGameTime() - started"));
        assertTrue(source("client/events/ClientRoleEventHandlers.java")
                .contains("ClientGameplayEvents.DISGUISE_STATE_COPIED.register(org.agmas.noellesroles.client.MustardPuppetAnimation::apply)"));
        String renderer = Files.readString(Path.of("src/main/java/io/wifi/starrailexpress/client/disguise/EntityDisguiseRenderer.java"));
        assertTrue(renderer.contains("ClientGameplayEvents.DISGUISE_STATE_COPIED"));
        assertTrue(renderer.lastIndexOf("dummy.tickCount = player.tickCount;") < renderer.indexOf(".invoker().copied(player, dummy)"));
        assertTrue(source("role_data/killer/MustardPuppetRoleData.java").contains("MustardRole.endPuppet(self)"));
    }
}
