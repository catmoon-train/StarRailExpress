package org.agmas.noellesroles.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * 里世界的有限局部客户端换肤。只写当前 ClientLevel，不发包、不接触服务端世界。
 * 快照只拥有仍等于本地替换状态的格子；原生更新和区块包到达时立即让出所有权。
 */
public final class SackGhostBlockChanges {
    static final SackGhostBlockChanges INSTANCE = new SackGhostBlockChanges();

    private static final int RADIUS = 12;
    private static final int BELOW = 4;
    private static final int ABOVE = 6;
    private static final int MAX_SNAPSHOTS = 3072;
    private static final int SCAN_BUDGET = 512;
    private static final int WRITE_BUDGET = 48;
    private static final int MAINTENANCE_BUDGET = 96;
    private static final int RESTORE_BUDGET = 192;
    private static final int COORDINATE_LIMIT = 29_999_984;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Offset[] OFFSETS = makeOffsets();
    // 均为无实体、无流体、不发光的完整立方体，不引入哭泣黑曜石的额外光照更新。
    private static final BlockState[] PALETTE = {
            Blocks.OBSIDIAN.defaultBlockState(),
            Blocks.BLACK_CONCRETE.defaultBlockState(),
            Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState(),
            Blocks.RED_NETHER_BRICKS.defaultBlockState(),
            Blocks.RED_TERRACOTTA.defaultBlockState()
    };

    private final Long2ObjectLinkedOpenHashMap<Snapshot> snapshots = new Long2ObjectLinkedOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private boolean running;
    private int age;
    private int scanIndex;
    private int localWrites;

    private SackGhostBlockChanges() {
    }

    /** 由场景管理器的静态初始化调用一次，不随进出里世界动态注册。 */
    static void registerLifecycle() {
        ClientChunkEvents.CHUNK_UNLOAD.register(INSTANCE::forgetChunk);
        // 完整区块包也可能原地重写同一个 LevelChunk，不能只比较区块对象。
        ClientChunkEvents.CHUNK_LOAD.register(INSTANCE::forgetChunk);
        ClientTickEvents.END_CLIENT_TICK.register(INSTANCE::lifecycleTick);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath("noellesroles", "sack_ghost_blocks");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                Minecraft.getInstance().execute(() -> OtherworldSceneManager.INSTANCE.reset());
            }
        });
    }

    void activate(ClientLevel next) {
        forceRestore();
        level = next;
        running = true;
        age = scanIndex = 0;
    }

    void deactivate() {
        running = false;
        age = scanIndex = 0;
        // 普通退出由独立客户端 tick 分批还原，即使游戏暂停/时停也照常完成。
    }

    boolean isRestoring() {
        return !running && !snapshots.isEmpty();
    }

    /** 不参与动画时钟：退出、局末、死亡和换世界的清理不能被暂停挡住。 */
    private void lifecycleTick(Minecraft mc) {
        if (level == null) return;
        if (level != mc.level || mc.player == null) {
            OtherworldSceneManager.INSTANCE.reset();
            return;
        }
        if (running && !OtherworldShader.isAnyOtherworldActive()) {
            OtherworldSceneManager.INSTANCE.deactivate();
        }
        if (!running) restoreBatch(RESTORE_BUDGET);
    }

    void tick(Minecraft mc) {
        if (!running || level != mc.level || mc.player == null || !SackGhostClient.clockRunning()) return;
        int x = Mth.floor(mc.player.getX());
        int y = Mth.floor(mc.player.getY());
        int z = Mth.floor(mc.player.getZ());
        age = Math.min(age + 1, RADIUS * 2);
        maintain(x, y, z);
        if (snapshots.size() >= MAX_SNAPSHOTS) return;
        double spread = Math.min(RADIUS, 1.0D + age * 0.5D);
        int writes = 0;
        for (int scanned = 0; scanned < SCAN_BUDGET && writes < WRITE_BUDGET; scanned++) {
            Offset offset = OFFSETS[scanIndex++];
            if (scanIndex == OFFSETS.length) scanIndex = 0;
            if (offset.radiusSquared > spread * spread) {
                scanIndex = 0;
                break;
            }
            cursor.set(x + offset.x, y + offset.y, z + offset.z);
            if (!inBounds(cursor) || snapshots.containsKey(cursor.asLong())) continue;
            LevelChunk chunk = loadedChunk(cursor);
            if (chunk == null) continue;
            BlockState original = chunk.getBlockState(cursor);
            if (!safeCube(original, chunk) || !exposed()) continue;
            BlockState visual = replacement(cursor);
            if (visual == original) continue;
            BlockPos pos = cursor.immutable();
            if (writeLocal(pos, visual)) {
                snapshots.put(pos.asLong(), new Snapshot(pos, original, visual, chunk));
                writes++;
                if (snapshots.size() == MAX_SNAPSHOTS) break;
            }
        }
    }

    /** 轮转检查有限快照，不遍历地图；移动后逐批释放旧范围，为新范围留出空间。 */
    private void maintain(int x, int y, int z) {
        int count = Math.min(MAINTENANCE_BUDGET, snapshots.size());
        for (int i = 0; i < count; i++) {
            Snapshot snapshot = snapshots.removeFirst();
            LevelChunk chunk = loadedChunk(snapshot.pos);
            if (chunk != snapshot.chunk || chunk.getBlockState(snapshot.pos) != snapshot.visual) continue;
            long dx = (long) snapshot.pos.getX() - x;
            long dz = (long) snapshot.pos.getZ() - z;
            long dy = (long) snapshot.pos.getY() - y;
            if (dx * dx + dz * dz > (RADIUS + 2) * (RADIUS + 2) || dy < -BELOW - 2 || dy > ABOVE + 2) {
                restoreOwned(snapshot, snapshot.original);
            } else {
                snapshots.put(snapshot.pos.asLong(), snapshot);
            }
        }
    }

    private boolean safeCube(BlockState state, LevelChunk chunk) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || chunk.getBlockEntities().containsKey(cursor) || state.getLightEmission() != 0
                || state.isSignalSource() || state.hasAnalogOutputSignal()) return false;
        Block block = state.getBlock();
        if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")) return false;
        // 仅普通建筑材料，排除任务/交互方块与所有自定义行为方块；不靠“看起来是方块”猜测。
        Class<?> type = block.getClass();
        if (type != Block.class && type != RotatedPillarBlock.class
                && type != GrassBlock.class && type != SnowyDirtBlock.class) return false;
        return block.getFriction() == 0.6F && block.getSpeedFactor() == 1.0F && block.getJumpFactor() == 1.0F
                && state.getDestroySpeed(level, cursor) >= 0.0F
                && state.isSolidRender(level, cursor) && state.isCollisionShapeFullBlock(level, cursor);
    }

    private boolean exposed() {
        for (Direction direction : DIRECTIONS) {
            neighbor.setWithOffset(cursor, direction);
            if (!inBounds(neighbor)) continue;
            LevelChunk chunk = loadedChunk(neighbor);
            if (chunk != null && !chunk.getBlockState(neighbor).isSolidRender(level, neighbor)) return true;
        }
        return false;
    }

    private static BlockState replacement(BlockPos pos) {
        // 连贯的暗色底与血红细脉，裂砖穿插其间；不使用随机数，也不随帧闪动。
        int vein = Math.floorMod(pos.getX() * 3 + pos.getZ() * 5 + pos.getY() * 2, 17);
        if (vein == 0 || vein == 1) return PALETTE[3];
        if (vein == 2) return PALETTE[4];
        long hash = Mth.getSeed(pos);
        return PALETTE[Math.floorMod(hash, 3)];
    }

    private boolean inBounds(BlockPos pos) {
        return Math.abs((long) pos.getX()) <= COORDINATE_LIMIT && Math.abs((long) pos.getZ()) <= COORDINATE_LIMIT
                && !level.isOutsideBuildHeight(pos);
    }

    private LevelChunk loadedChunk(BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
    }

    private boolean writeLocal(BlockPos pos, BlockState state) {
        if (level == null || level != Minecraft.getInstance().level) return false;
        LevelChunk chunk = loadedChunk(pos);
        if (chunk == null) return false;
        localWrites++;
        try {
            // 不走 ClientLevel.setBlock：预测交互期间也不能把本地外观记入原生预测回滚表。
            BlockState previous = chunk.setBlockState(pos, state, false);
            if (previous == null) return false;
            level.setBlocksDirty(pos, previous, state);
            level.sendBlockUpdated(pos, previous, state, FLAGS);
            return true;
        } finally {
            localWrites--;
        }
    }

    private void restoreOwned(Snapshot snapshot, BlockState state) {
        LevelChunk chunk = loadedChunk(snapshot.pos);
        if (chunk == snapshot.chunk && chunk.getBlockState(snapshot.pos) == snapshot.visual) {
            writeLocal(snapshot.pos, state);
        }
    }

    private void restoreBatch(int budget) {
        if (level != Minecraft.getInstance().level) {
            snapshots.clear();
        } else {
            for (int i = 0; i < budget && !snapshots.isEmpty(); i++) {
                Snapshot snapshot = snapshots.removeFirst();
                restoreOwned(snapshot, snapshot.original);
            }
        }
        if (snapshots.isEmpty()) level = null;
    }

    /** 强制生命周期结束时同步清完至多 MAX_SNAPSHOTS 项；绝不把旧世界快照写入新世界。 */
    void forceRestore() {
        running = false;
        if (level != null) restoreBatch(MAX_SNAPSHOTS);
        snapshots.clear();
        level = null;
        age = scanIndex = 0;
    }

    private void forgetChunk(ClientLevel world, LevelChunk chunk) {
        if (world != level || chunk == null) return;
        int x = chunk.getPos().x;
        int z = chunk.getPos().z;
        snapshots.values().removeIf(snapshot -> (snapshot.pos.getX() >> 4) == x && (snapshot.pos.getZ() >> 4) == z);
    }

    private Snapshot take(ClientLevel world, BlockPos pos) {
        return localWrites != 0 || world != level ? null : snapshots.remove(pos.asLong());
    }

    /**
     * 原生更新前归还原块并失效快照，随后由原生方法保存/应用最新服务端状态。
     * 不抢先写入传入的新状态，保留原生预测延迟、邻居更新与碰撞纠正语义。
     * 即使新状态恰好等于本地外观，退出时也不会再拿旧快照覆盖它。
     */
    public static void beforeNativeUpdate(ClientLevel world, BlockPos pos) {
        Snapshot snapshot = INSTANCE.take(world, pos);
        if (snapshot != null) INSTANCE.restoreOwned(snapshot, snapshot.original);
    }

    private static Offset[] makeOffsets() {
        ArrayList<Offset> offsets = new ArrayList<>();
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                int distance = x * x + z * z;
                if (distance > RADIUS * RADIUS) continue;
                for (int y = -BELOW; y <= ABOVE; y++) offsets.add(new Offset(x, y, z, distance));
            }
        }
        offsets.sort(Comparator.comparingInt(Offset::radiusSquared).thenComparingInt(offset -> Math.abs(offset.y)));
        return offsets.toArray(Offset[]::new);
    }

    private record Offset(int x, int y, int z, int radiusSquared) {
    }

    private record Snapshot(BlockPos pos, BlockState original, BlockState visual, LevelChunk chunk) {
    }
}
