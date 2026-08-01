/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.wallbreak;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 破墙弹逻辑：命中方块后拆除半径内的墙壁（记入 {@link WallBreakSavedData}），
 * {@link #RESTORE_DELAY_TICKS}（5s）后按持久化记录恢复。
 * 带 BlockEntity/NBT 的方块（容器、售货机、抽奖机、小游戏任务点等）不拆除；恢复时把卡在墙里的玩家顶到安全位置。
 */
public final class WallBreakManager {

    /** 恢复延迟：5 秒。 */
    private static final int RESTORE_DELAY_TICKS = 100;

    private WallBreakManager() {
    }

    /** 命中落点：拆除以 center 为球心、半径 radius 内的可拆方块。 */
    public static void breakWalls(ServerLevel world, BlockPos center, int radius, @Nullable ServerPlayer thrower) {
        WallBreakSavedData data = WallBreakSavedData.get(world.getServer());
        long restoreAt = world.getGameTime() + RESTORE_DELAY_TICKS;
        ResourceLocation dim = world.dimension().location();
        int r2 = radius * radius;
        int broken = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) {
                        continue;
                    }
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!isBreakable(world, pos, state)) {
                        continue;
                    }
                    data.add(new WallBreakSavedData.Entry(dim, pos.immutable(), state, restoreAt));
                    world.removeBlock(pos, false);
                    world.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.01);
                    broken++;
                }
            }
        }
        if (broken > 0) {
            world.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 1.1F);
        }
    }

    /**
     * 能否拆除：跳过空气、液体、不可破坏方块（基岩/屏障），以及一切带 BlockEntity/NBT 的方块
     * （容器、售货机、抽奖机、供给箱、小游戏任务点、赌台等均为 BaseEntityBlock，此判定即可全部排除）。
     */
    private static boolean isBreakable(ServerLevel world, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (state.getDestroySpeed(world, pos) < 0) {
            return false;
        }
        return !state.hasBlockEntity();
    }

    /** 每服务端 tick：恢复到期的被拆方块（读取持久化记录，重启后照常继续）。 */
    public static void tick(MinecraftServer server) {
        WallBreakSavedData data = WallBreakSavedData.get(server);
        if (data.entries().isEmpty()) {
            return;
        }
        List<WallBreakSavedData.Entry> due = new ArrayList<>();
        for (WallBreakSavedData.Entry entry : data.entries()) {
            ServerLevel world = server.getLevel(dimensionKey(entry.dimension));
            if (world != null && world.getGameTime() >= entry.restoreAtGameTime) {
                due.add(entry);
            }
        }
        for (WallBreakSavedData.Entry entry : due) {
            ServerLevel world = server.getLevel(dimensionKey(entry.dimension));
            if (world != null) {
                restore(world, entry);
            }
            data.remove(entry);
        }
    }

    private static void restore(ServerLevel world, WallBreakSavedData.Entry entry) {
        BlockState current = world.getBlockState(entry.pos);
        // 仅当原位为空气/可替换时恢复，避免覆盖玩家新放置的方块
        if (current.isAir() || current.canBeReplaced()) {
            world.setBlock(entry.pos, entry.state, Block.UPDATE_ALL);
            pushPlayersOut(world, entry.pos);
        }
    }

    /** 把卡在恢复方块里的玩家向上顶到无碰撞的安全位置。 */
    private static void pushPlayersOut(ServerLevel world, BlockPos pos) {
        AABB blockBox = new AABB(pos).inflate(0.5);
        for (ServerPlayer player : world.getEntitiesOfClass(ServerPlayer.class, blockBox)) {
            int attempts = 0;
            while (!world.noBlockCollision(player, player.getBoundingBox()) && attempts++ < 12) {
                player.teleportTo(player.getX(), player.getY() + 1.0, player.getZ());
            }
        }
    }

    private static ResourceKey<Level> dimensionKey(ResourceLocation dimension) {
        return ResourceKey.create(Registries.DIMENSION, dimension);
    }
}
