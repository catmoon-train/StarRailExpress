package org.agmas.noellesroles.game.command_block;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 无世界环境的数值与安全接线回归；距离纠正和多人叠加仍需游戏内验收。 */
class StringerMechanicsTest {
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/org/agmas/noellesroles", relative));
    }

    @Test
    void loadoutShopAndCooldownsMatchRedesign() throws Exception {
        String role = source("game/command_block/StringerRole.java");
        assertTrue(role.contains("MARK_RANGE = 15.0D"));
        assertTrue(role.contains("MARK_TICKS = 10 * 20"));
        assertTrue(role.contains("LINK_TICKS = 60 * 20"));
        assertTrue(role.contains("LINK_DISTANCE = 10.0D"));
        assertTrue(role.contains("cooldownSeconds(35)"));
        assertTrue(role.contains("cooldownSeconds(90)"));
        assertTrue(role.contains("data.hold(self, 6)"));
        assertTrue(role.contains("LOCKPICK.getDefaultInstance(), 80"));
        assertTrue(role.contains("THREAD_BALL.getDefaultInstance(), 250"));
        assertTrue(role.contains("List.of(ModItems.THREAD_SPOOL.getDefaultInstance(), ModItems.THREAD_BLADE.getDefaultInstance())"));
        assertFalse(role.contains("SELF_GLOW"));
        assertFalse(role.contains("MOVEMENT_SLOWDOWN"));
        assertFalse(role.contains("ModEffects.of(ModEffects.USED_BANED"));
        assertTrue(source("game/command_block/item/ThreadSpoolItem.java").contains("COOLDOWN = 5 * 20"));
        String blade = source("game/command_block/item/ThreadBladeItem.java");
        assertTrue(blade.contains("COOLDOWN = 10 * 20"));
        assertTrue(blade.contains("RANGE = 3.5D"));
        assertTrue(blade.contains("GazeRays.firstVisiblePlayer"));
        assertTrue(blade.contains("data.canCut(mark)"));
    }

    @Test
    void selectionOnlyCommitsThroughValidatedUnifiedSkill() throws Exception {
        String role = source("game/command_block/StringerRole.java");
        assertTrue(role.contains("RoleSkill.beginUse(self, null, 1, RoleSkill.Phase.PRESS, false)"));
        assertTrue(role.contains("first == second"));
        assertTrue(role.contains("first.level() != self.level() || second.level() != self.level()"));
        assertTrue(role.contains("GameUtils.isPlayerAliveAndSurvival(first)"));
        assertTrue(role.contains("GameUtils.isPlayerAliveAndSurvival(second)"));
        assertTrue(role.contains("self.hasEffect(ModEffects.SKILL_BANED)"));
        assertTrue(role.contains("!data.selectionOpen()"));
        assertTrue(role.contains("data.beginSelection();\n            OpenScreenManager.openScreen(self, INVENTORY);\n            return false;"));
        assertTrue(role.contains("finally {\n            data.clearPendingChoice();"));
        String packet = source("packet/StringerLinkC2SPayload.java");
        assertTrue(packet.contains("record StringerLinkC2SPayload(UUID first, UUID second)"));
        assertTrue(packet.contains("context.server().execute"));
        String client = source("client/rolescreen/StringerRoleScreenExtension.java");
        assertTrue(client.contains("new RoleScreenHelper<>"));
        assertTrue(client.contains("new StringerLinkC2SPayload(first, id)"));
        assertFalse(client.contains("teleport"));
    }

    @Test
    void marksLinksAndGrenadesStayServerAuthoritative() throws Exception {
        String data = source("role_data/killer/StringerRoleData.java");
        assertTrue(data.contains("Map<UUID, Target> marks"));
        assertTrue(data.contains("Comparator.comparingDouble(self::distanceToSqr)"));
        assertTrue(data.contains("MobEffects.MOVEMENT_SLOWDOWN, duration, 1"));
        assertTrue(data.contains("!dimension.equals(player.level().dimension())"));
        assertTrue(data.contains("getRole(p) == target.role()"));
        assertTrue(data.contains("RoleData.getNullable(p) == target.data()"));
        assertTrue(data.contains("p.server.getPlayerList().getPlayer(p.getUUID()) == p"));
        assertTrue(data.contains("constrainLink();"));
        assertTrue(data.contains("placeLinked(first, firstSafe)"));
        assertFalse(data.contains("sync();"));
        String entity = source("game/command_block/entity/ThreadBallEntity.java");
        assertTrue(entity.contains("extends ThrowableItemProjectile"));
        assertTrue(entity.contains("RADIUS = 4F"));
        assertTrue(entity.contains("HOLD_TICKS = 2 * 20"));
        assertTrue(entity.contains("MARK_TICKS = 15 * 20"));
        assertTrue(entity.contains("GrenadeEntity.getPlayersAffectedByExplosion"));
        assertTrue(entity.contains("RoleData.getNullable(StringerRoleData.class, owner) == source"));
        String item = source("game/command_block/item/ThreadBallItem.java");
        assertTrue(item.contains("GrenadeItem.MAX_CHARGE_TIME"));
        assertTrue(item.contains("GrenadeItem.addGrenadeCooldown(self)"));
        assertTrue(item.contains("0.4F + 0.75F * charge / GrenadeItem.MAX_CHARGE_TIME"));
    }
}
