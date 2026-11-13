package org.agmas.noellesroles.game.roles.neutral.roulette_devil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import io.wifi.starrailexpress.index.TMMBlocks;
import org.agmas.noellesroles.content.block.DevilRouletteTable;
import org.agmas.noellesroles.init.ModBlocks;

/** 在远离列车的空区块生成封闭黑室；仅清除自己事先确认全为空气的范围。 */
final class RouletteDevilRoom {
    final ServerLevel level;
    final BlockPos floor;
    final boolean alreadyForced;
    private RouletteDevilRoom(ServerLevel level, BlockPos floor) {
        this.level = level; this.floor = floor;
        ChunkPos chunk = new ChunkPos(floor);
        alreadyForced = level.getForcedChunks().contains(chunk.toLong());
        level.setChunkForced(chunk.x, chunk.z, true);
    }
    BlockPos table() { return floor.above(); }
    BlockPos seat(boolean front) { return table().relative(Direction.NORTH, front ? 2 : -2); }
    static RouletteDevilRoom create(ServerLevel level) {
        int y = Math.min(280, level.getMaxBuildHeight() - 10);
        for (int slot = 0; slot < 128; slot++) {
            BlockPos floor = new BlockPos(240008 + slot * 32, y, 240008);
            if (!level.getWorldBorder().isWithinBounds(floor)) continue;
            boolean empty = true;
            for (BlockPos p : BlockPos.betweenClosed(floor.offset(-6, 0, -6), floor.offset(6, 7, 6))) {
                if (!level.getBlockState(p).isAir()) { empty = false; break; }
            }
            if (!empty) continue;
            RouletteDevilRoom room = new RouletteDevilRoom(level, floor);
            room.build(); return room;
        }
        return null;
    }
    private void build() {
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) for (int y = 0; y <= 7; y++) {
            if (y == 0 || y == 7 || Math.abs(x) == 6 || Math.abs(z) == 6)
                set(floor.offset(x, y, z), (y == 0 && (x + z) % 3 == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.BLACK_CONCRETE));
        }
        // 两盏低亮度红烛照亮桌面，屋顶完全遮天。
        set(floor.offset(-3, 1, 0), Blocks.REDSTONE_TORCH);
        set(floor.offset(3, 1, 0), Blocks.REDSTONE_TORCH);
        level.setBlock(table(), ModBlocks.DEVIL_ROULETTE_TABLE.defaultBlockState(), Block.UPDATE_CLIENTS);
        DevilRouletteTable.placeStructure(level, table(), Direction.NORTH);
        set(seat(true), TMMBlocks.BAR_STOOL); set(seat(false), TMMBlocks.BAR_STOOL);
    }
    private void set(BlockPos p, Block b) { level.setBlock(p, b.defaultBlockState(), Block.UPDATE_CLIENTS); }
    void clear() {
        for (BlockPos p : BlockPos.betweenClosed(floor.offset(-6, 0, -6), floor.offset(6, 7, 6)))
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        if (!alreadyForced) { ChunkPos chunk = new ChunkPos(floor); level.setChunkForced(chunk.x, chunk.z, false); }
    }
}
