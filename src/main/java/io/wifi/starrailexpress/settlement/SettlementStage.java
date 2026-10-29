package io.wifi.starrailexpress.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashSet;
import java.util.Set;

/**
 * 每 tick 有界写入的封闭黑箱舞台。坐标指定地板中心，内部不生成有行为的实体。
 *
 * <p>方块摆放分两层：
 * <ul>
 * <li>本体负责中性的舞台骨架（黑箱外壳、镜面黑石地板、台口金线、脚灯、顶灯、侧墙）；</li>
 * <li>皮肤可选的方块覆盖由客户端注册表提供——但服务端拿不到客户端注册表，
 * 因此这里只接受一个「方块覆盖器」回调，由 {@code SettlementManager}
 * 在服务端侧按快照里的皮肤 ID 解析后传入。未提供时完全走默认摆放。</li>
 * </ul>
 */
final class SettlementStage {
    private static final int WIDTH = 33, DEPTH = 31, HEIGHT = 15;
    final ServerLevel level;
    final BlockPos origin;
    private final Set<Long> tickets = new HashSet<>();
    private int cursor;
    /** 皮肤方块覆盖器；{@code null} 表示不覆盖。 */
    private final BlockOverride override;
    private int pinCursor;

    /**
     * 皮肤方块覆盖器。
     *
     * <p>服务端不认识具体皮肤，只把快照里的皮肤 ID 交出去；
     * 覆盖逻辑由注册在服务端的实现决定（当前无内置实现，默认返回 {@code null}）。
     */
    @FunctionalInterface
    interface BlockOverride {
        /**
         * @return 要放置的方块，或 {@code null} 表示用默认摆放
         */
        BlockState blockAt(int x, int y, int z);
    }

    SettlementStage(ServerLevel level, BlockPos origin, BlockOverride override) {
        this.level = level;
        this.origin = origin;
        this.override = override;
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
            // 皮肤方块覆盖：只有注册了覆盖器的皮肤才接管，未注册时保持上面的中性舞台。
            if (override != null) {
                BlockState overridden = override.blockAt(x, y, z);
                if (overridden != null) state = overridden;
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
