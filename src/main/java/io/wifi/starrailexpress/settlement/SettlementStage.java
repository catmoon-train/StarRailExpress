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
    private final boolean enderDragon;
    private int pinCursor;

    SettlementStage(ServerLevel level, BlockPos origin, boolean enderDragon) {
        this.level = level;
        this.origin = origin;
        this.enderDragon = enderDragon;
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
            // 外壳：黑混凝土墙体与顶盖，隔绝外界景物。
            if (y == 0 || y == 14 || Math.abs(x) == 16 || Math.abs(z) == 15)
                state = Blocks.BLACK_CONCRETE.defaultBlockState();
            // 地板：前排镜面黑石舞台，后排尸体区保持哑光黑，界线清晰。
            if (y == 0) state = z >= 4
                    ? Blocks.BLACK_CONCRETE.defaultBlockState()
                    : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            // 台口金线：舞台前沿一道细窄的黄铜镶边，呼应车票配色。
            if (y == 0 && z == -3 && Math.abs(x) < 14)
                state = Blocks.WAXED_EXPOSED_COPPER.defaultBlockState();
            // 脚灯：台口两侧的落地暖光，灯体嵌在地面里而不是悬空。
            if (y == 0 && z == -4 && (Math.abs(x) == 6 || Math.abs(x) == 12))
                state = Blocks.WAXED_EXPOSED_COPPER.defaultBlockState();
            if (y == 1 && z == -4 && (Math.abs(x) == 6 || Math.abs(x) == 12))
                state = Blocks.LANTERN.defaultBlockState();
            // 顶灯：三盏吊灯从顶盖垂下，落在舞台中央与两侧。
            if (y == 13 && z == 0 && (x == -8 || x == 0 || x == 8))
                state = Blocks.LANTERN.defaultBlockState();
            // 侧墙装饰：内收一格的立柱 + 黄铜腰线，给封闭空间纵深又不破坏外墙。
            if ((Math.abs(x) == 14) && z >= -12 && z <= 10) {
                if (y >= 1 && y <= 13) state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                if (y == 6) state = Blocks.WAXED_EXPOSED_COPPER.defaultBlockState();
            }
            // 后排保持裸露黑地板：尸体直接堆在台上，不加任何围台/抬台方块。
            if (enderDragon) {
                state = y == 0 ? Blocks.END_STONE.defaultBlockState()
                        : y == 14 || Math.abs(x) == 16 || Math.abs(z) == 15
                        ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                // 后景黑曜石柱，不挡住前排获胜者和后台尸堆。
                if (Math.abs(x) >= 11 && Math.abs(x) <= 12 && (z == 10 || z == 11)
                        && y >= 1 && y <= 10) state = Blocks.OBSIDIAN.defaultBlockState();
                if (y == 1 && (z == -4 || z == 8) && (Math.abs(x) == 8 || Math.abs(x) == 12))
                    state = Blocks.END_ROD.defaultBlockState();
            }
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
