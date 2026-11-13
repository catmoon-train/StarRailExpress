/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.wifi.starrailexpress.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 仅由 baseInitialize 的开局出生循环持有；不修改房间锚点或共享传送方法。 */
final class GameStartSpawnScatter {
    private final Map<ServerLevel, List<AABB>> reservations = new HashMap<>();

    /** 返回 null 表示范围内无安全点，由调用方执行旧传送并告警。 */
    Vec3 find(ServerPlayer player, Vec3 origin, AABB playArea) {
        ServerLevel level = player.serverLevel();
        var dimensions = player.getDimensions(Pose.STANDING);
        double halfWidth = Math.max(0.3, dimensions.width() / 2.0);
        double height = Math.max(1.8, dimensions.height());
        List<AABB> reserved = reservations.computeIfAbsent(level, key -> new ArrayList<>());
        var start = new SpawnScatterSearch.Point(origin.x, origin.y, origin.z);
        var selected = SpawnScatterSearch.find(start, level.random::nextInt,
                column -> safeHeights(level, column, halfWidth, height, playArea),
                point -> reserved.stream().noneMatch(box -> box.intersects(body(point, halfWidth, height))));
        // 即使回退旧落点，也要让随后出生的玩家尽量避开它。
        reserved.add(body(selected != null ? selected : start, halfWidth, height).inflate(0.1));
        return selected == null ? null : new Vec3(selected.x(), selected.y(), selected.z());
    }

    private static List<Double> safeHeights(ServerLevel level, SpawnScatterSearch.Point column,
            double halfWidth, double height, AABB playArea) {
        List<Double> heights = new ArrayList<>();
        int minY = Math.max(level.getMinBuildHeight(),
                (int) Math.floor(column.y() - SpawnScatterSearch.MAX_VERTICAL_OFFSET) - 1);
        int maxY = Math.min(level.getMaxBuildHeight() - 1,
                (int) Math.floor(column.y() + SpawnScatterSearch.MAX_VERTICAL_OFFSET));
        for (int y = maxY; y >= minY; y--) {
            BlockPos support = BlockPos.containing(column.x(), y, column.z());
            if (!level.hasChunkAt(support)) continue;
            BlockState state = level.getBlockState(support);
            if (dangerous(state)) continue;
            // 用真实碰撞面支持半砖等非整块地板；细栏杆、开门等不足以承托整个人的不选。
            for (AABB surface : state.getCollisionShape(level, support).toAabbs()) {
                double feetY = y + surface.maxY;
                if (Math.abs(feetY - column.y()) > SpawnScatterSearch.MAX_VERTICAL_OFFSET) continue;
                if (support.getX() + surface.minX > column.x() - halfWidth
                        || support.getX() + surface.maxX < column.x() + halfWidth
                        || support.getZ() + surface.minZ > column.z() - halfWidth
                        || support.getZ() + surface.maxZ < column.z() + halfWidth) continue;
                AABB body = body(new SpawnScatterSearch.Point(column.x(), feetY, column.z()), halfWidth, height);
                if (safe(level, body, playArea)) heights.add(feetY);
            }
        }
        return heights;
    }

    private static AABB body(SpawnScatterSearch.Point point, double halfWidth, double height) {
        return new AABB(point.x() - halfWidth, point.y(), point.z() - halfWidth,
                point.x() + halfWidth, point.y() + height, point.z() + halfWidth);
    }

    private static boolean safe(ServerLevel level, AABB body, AABB playArea) {
        if (body.minY < level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(body)) return false;
        if (playArea != null && (body.minX < playArea.minX || body.maxX > playArea.maxX
                || body.minY < playArea.minY || body.maxY > playArea.maxY
                || body.minZ < playArea.minZ || body.maxZ > playArea.maxZ)) return false;
        // 包括脚下接触面与头部空间；拒绝含水方块、任何液体及非碰撞型危险方块。
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(body.minX - 0.05, body.minY - 0.05, body.minZ - 0.05),
                BlockPos.containing(body.maxX + 0.05, body.maxY + 0.05, body.maxZ + 0.05))) {
            if (!level.hasChunkAt(pos) || dangerous(level.getBlockState(pos))) return false;
        }
        // 仅查方块碰撞，避免其他玩家尚未离开的旧位置影响本轮分配；玩家由 reservations 避让。
        for (var collision : level.getBlockCollisions(null, body.deflate(1.0E-7))) {
            if (!collision.isEmpty()) return false;
        }
        return true;
    }

    private static boolean dangerous(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)
                || state.is(Blocks.COBWEB) || state.is(Blocks.BIG_DRIPLEAF) || state.is(Blocks.SCAFFOLDING);
    }
}
