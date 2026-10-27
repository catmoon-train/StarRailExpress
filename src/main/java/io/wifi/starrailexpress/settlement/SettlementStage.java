package io.wifi.starrailexpress.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashSet;
import java.util.Set;

/** 每 tick 有界写入的封闭黑箱舞台。坐标指定地板中心，内部不生成有行为的实体。 */
final class SettlementStage {
    private static final int WIDTH = 33, DEPTH = 31, HEIGHT = 15;
    final ServerLevel level;
    final BlockPos origin;
    private final Set<Long> tickets = new HashSet<>();
    private int cursor;
    private int pinCursor;

    SettlementStage(ServerLevel level, BlockPos origin) {
        this.level = level;
        this.origin = origin;
    }

    /** 只撤销本次新增的强加载票据，保留管理员已有的票据。 */
    boolean pinTick() {
        int minX = (origin.getX()-16)>>4, maxX = (origin.getX()+16)>>4;
        int minZ = (origin.getZ()-16)>>4, maxZ = (origin.getZ()+16)>>4;
        int columns = maxX-minX+1;
        int total = columns*(maxZ-minZ+1);
        if (pinCursor >= total) return true;
        int x = minX+pinCursor%columns, z = minZ+pinCursor/columns;
        long packed = net.minecraft.world.level.ChunkPos.asLong(x,z);
        if (!level.getForcedChunks().contains(packed)) {
            level.setChunkForced(x,z,true);
            tickets.add(packed);
        }
        level.getChunk(x,z);
        return ++pinCursor >= total;
    }

    boolean buildTick() {
        int end = Math.min(WIDTH*DEPTH*HEIGHT, cursor+1024);
        for (; cursor < end; cursor++) {
            int x = cursor % WIDTH - 16;
            int z = (cursor / WIDTH) % DEPTH - 15;
            int y = cursor / (WIDTH*DEPTH);
            BlockState state = Blocks.AIR.defaultBlockState();
            if (y == 0 || y == 14 || Math.abs(x) == 16 || Math.abs(z) == 15)
                state = Blocks.BLACK_CONCRETE.defaultBlockState();
            // 克制的暖色脚灯和顶灯，不放火把/粒子等持续音源。
            if ((y == 0 && z == -2 && x % 4 == 0 && Math.abs(x) <= 12)
                    || (y == 13 && z == 1 && Math.abs(x) == 8))
                state = Blocks.SEA_LANTERN.defaultBlockState();
            if (y == 0 && (z == -3 || z == 9) && Math.abs(x) < 14)
                state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            // 后排开放展示台，不设挡住低矮尸堆的围墙。前方二排矮台采用半砖。
            // 后排开放展示台：只抬高一格，不加任何遮挡尸堆的围墙。
            if (y == 1 && Math.abs(x) <= 3 && z >= 4 && z <= 8)
                state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            BlockPos pos = origin.offset(x,y,z);
            if (!level.getBlockState(pos).equals(state)) level.setBlock(pos,state,2);
        }
        return cursor == WIDTH*DEPTH*HEIGHT;
    }

    void close() {
        for (long packed : tickets) level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(packed),
                net.minecraft.world.level.ChunkPos.getZ(packed), false);
        tickets.clear();
    }
}
