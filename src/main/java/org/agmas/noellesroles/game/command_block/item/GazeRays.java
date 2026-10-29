package org.agmas.noellesroles.game.command_block.item;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 沿视线打一条线段，按距离排序命中的存活玩家。 */
public final class GazeRays {
    private GazeRays() {
    }

    public static List<ServerPlayer> playersAlong(ServerPlayer attacker, double range, double inflate) {
        Vec3 start = attacker.getEyePosition();
        Vec3 end = start.add(attacker.getLookAngle().scale(range));
        List<ServerPlayer> hit = new ArrayList<>();
        for (ServerPlayer other : attacker.serverLevel().players()) {
            if (other == attacker || !GameUtils.isPlayerAliveAndSurvival(other)) {
                continue;
            }
            if (other.getBoundingBox().inflate(inflate).clip(start, end).isPresent()) {
                hit.add(other);
            }
        }
        hit.sort(Comparator.comparingDouble(player -> player.distanceToSqr(attacker)));
        return hit;
    }

    /** 先截断方块遮挡，再按射线交点距离选择最近的可见存活玩家，不穿墙选人。 */
    public static ServerPlayer firstVisiblePlayer(ServerPlayer attacker, double range, double inflate) {
        Vec3 start = attacker.getEyePosition();
        Vec3 end = start.add(attacker.getLookAngle().scale(range));
        var wall = attacker.level().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, attacker));
        double closest = start.distanceToSqr(wall.getLocation());
        ServerPlayer target = null;
        for (ServerPlayer other : attacker.serverLevel().players()) {
            if (other == attacker || !GameUtils.isPlayerAliveAndSurvival(other)
                    || other.distanceToSqr(attacker) > range * range || !attacker.hasLineOfSight(other)) {
                continue;
            }
            var hit = other.getBoundingBox().inflate(inflate).clip(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceToSqr(hit.get());
                if (distance < closest) {
                    closest = distance;
                    target = other;
                }
            }
        }
        return target;
    }

    public static boolean lookingAt(Player viewer, Player target, double minDot) {
        if (viewer == null || target == null || !viewer.hasLineOfSight(target)) {
            return false;
        }
        Vec3 to = target.getEyePosition().subtract(viewer.getEyePosition());
        if (to.lengthSqr() < 1.0E-4) {
            return true;
        }
        return viewer.getLookAngle().dot(to.normalize()) >= minDot;
    }

    public static float[] lookAt(Player viewer, Vec3 target) {
        Vec3 diff = target.subtract(viewer.getEyePosition());
        double horizontal = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(diff.y, horizontal));
        return new float[] { yaw, pitch };
    }

    public static AABB boxAround(Vec3 center, double radius) {
        return new AABB(center, center).inflate(radius);
    }
}
