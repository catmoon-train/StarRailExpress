package org.agmas.noellesroles.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 井盖位置登记表（按维度，瞬态）。井盖方块实体在加载/卸载时登记/注销，
 * 用于“沿视线方向传送到另一井盖”的目标查找，无需扫描世界。
 */
public final class ManholeRegistry {
    private ManholeRegistry() {
    }

    private static final Map<ResourceKey<Level>, Set<BlockPos>> MANHOLES = new HashMap<>();

    public static void add(ServerLevel level, BlockPos pos) {
        MANHOLES.computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = MANHOLES.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    public static Set<BlockPos> all(ServerLevel level) {
        return MANHOLES.getOrDefault(level.dimension(), Set.of());
    }

    /**
     * 查找玩家视线方向上最合适的另一个井盖。
     *
     * @param maxRange 最大水平距离
     * @return 目标井盖坐标，找不到返回 null
     */
    public static BlockPos findInLookDirection(ServerLevel level, Player player, BlockPos self, double maxRange) {
        Vec3 look = player.getLookAngle();
        Vec3 lookFlat = new Vec3(look.x, 0, look.z);
        if (lookFlat.lengthSqr() < 1.0e-4) {
            return null;
        }
        lookFlat = lookFlat.normalize();
        Vec3 from = player.position();

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : all(level)) {
            if (pos.equals(self)) {
                continue;
            }
            Vec3 to = new Vec3(pos.getX() + 0.5 - from.x, 0, pos.getZ() + 0.5 - from.z);
            double dist = to.length();
            if (dist > maxRange || dist < 1.0e-3) {
                continue;
            }
            double dot = to.normalize().dot(lookFlat);
            if (dot > 0.5 && dist < bestDist) {
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }
}
