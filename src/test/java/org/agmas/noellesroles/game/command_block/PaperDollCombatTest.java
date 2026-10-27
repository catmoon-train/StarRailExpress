package org.agmas.noellesroles.game.command_block;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 无世界环境下检查纸刀目标查询与技能接线，墙体形状仍需游戏内验收。 */
class PaperDollCombatTest {
    private String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/org/agmas/noellesroles", relative));
    }

    @Test
    void paperCutUsesOneTickChargeAndVisibleTargetBeforeBackstab() throws Exception {
        String blade = source("game/command_block/item/PaperCutItem.java");
        assertTrue(blade.contains("CHARGE_TICKS = 1;"));
        assertTrue(blade.contains("GazeRays.firstVisiblePlayer(self, RANGE, 0.4D)"));
        assertFalse(blade.contains("GazeRays.playersAlong"));
        assertTrue(blade.indexOf("firstVisiblePlayer") < blade.indexOf("StandSpots.horizontal"));
        assertTrue(blade.indexOf("StandSpots.horizontal") < blade.indexOf("CommandBlockCombat.tryKill"));
    }

    @Test
    void sharedTargetQueryRequiresWallIntersectionDistanceAndSightAndRange() throws Exception {
        String rays = source("game/command_block/item/GazeRays.java");
        String visible = rays.substring(rays.indexOf("public static ServerPlayer firstVisiblePlayer"),
                rays.indexOf("public static boolean lookingAt"));
        assertTrue(visible.contains("ClipContext.Block.OUTLINE"));
        assertTrue(visible.contains("start.distanceToSqr(wall.getLocation())"));
        assertTrue(visible.contains("distance < closest"));
        assertTrue(visible.contains("!attacker.hasLineOfSight(other)"));
        assertTrue(visible.contains("other.distanceToSqr(attacker) > range * range"));
        assertTrue(visible.contains("!GameUtils.isPlayerAliveAndSurvival(other)"));
    }

    @Test
    void targetIntersectionBehindWallIsRejectedButInFrontIsAccepted() {
        Vec3 start = new Vec3(0, 1.6, 0), end = new Vec3(0, 1.6, 5);
        double wallDistance = start.distanceToSqr(new Vec3(0, 1.6, 2));
        Vec3 behind = new AABB(-0.3, 0, 3, 0.3, 1.8, 3.6).inflate(0.4).clip(start, end).orElseThrow();
        Vec3 front = new AABB(-0.3, 0, 1, 0.3, 1.8, 1.6).inflate(0.4).clip(start, end).orElseThrow();
        assertFalse(start.distanceToSqr(behind) < wallDistance);
        assertTrue(start.distanceToSqr(front) < wallDistance);
    }

    @Test
    void turningHasIndependentCooldownAndPreservesPitchAndPosition() throws Exception {
        String role = source("game/command_block/PaperDollRole.java");
        assertTrue(role.contains("TURN_RANGE = 8.0D;"));
        assertTrue(role.contains(".cooldownSeconds(60)"));
        assertTrue(role.contains(".cooldownSeconds(32)"));
        assertTrue(role.contains("GazeRays.firstVisiblePlayer(self, TURN_RANGE, 0.3D)"));
        assertTrue(role.contains("nextFloat() * 360.0F - 180.0F"));
        assertTrue(role.contains("float pitch = target.getXRot();"));
        assertTrue(role.contains("target.connection.teleport(target.getX(), target.getY(), target.getZ(), yaw, pitch);"));
        assertFalse(role.contains("target.setXRot("));
    }
}
