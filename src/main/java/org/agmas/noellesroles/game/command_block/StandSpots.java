package org.agmas.noellesroles.game.command_block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** 沿水平朝向找一块能站的地。找不到就返回 null，不穿墙硬塞。 */
public final class StandSpots {
    private StandSpots() {
    }

    /** sign 为 1 是面朝方向，-1 是背后。 */
    public static Vec3 horizontal(ServerPlayer player, double sign) {
        double rad = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(rad) * sign, 0.0D, Math.cos(rad) * sign);
    }

    public static Vec3 find(ServerLevel level, Vec3 from, Vec3 direction, double distance) {
        if (direction.lengthSqr() < 1.0E-4) {
            return null;
        }
        Vec3 step = direction.normalize();
        for (double d = distance; d >= 1.0D; d -= 1.0D) {
            Vec3 dest = from.add(step.scale(d));
            BlockPos spot = findStand(level, BlockPos.containing(dest));
            if (spot != null) {
                return new Vec3(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
            }
        }
        return null;
    }

    public static void place(ServerPlayer player, Vec3 spot) {
        player.stopRiding();
        player.teleportTo(spot.x, spot.y, spot.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.connection.teleport(spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
    }

    private static BlockPos findStand(ServerLevel level, BlockPos origin) {
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos feet = origin.offset(0, dy, 0);
            if (canStand(level, feet)) {
                return feet;
            }
        }
        return null;
    }

    private static boolean canStand(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && !level.getBlockState(feet.below()).isAir();
    }
}