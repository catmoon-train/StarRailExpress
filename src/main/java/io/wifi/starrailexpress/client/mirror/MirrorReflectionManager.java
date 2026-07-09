package io.wifi.starrailexpress.client.mirror;

import com.mojang.authlib.GameProfile;
import io.wifi.starrailexpress.content.block.MirrorBlock;
import io.wifi.starrailexpress.content.block_entity.MirrorBlockEntity;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端镜子反射的总调度。
 *
 * <p>思路是"把倒影真的建出来"而不是"把世界再渲染一遍"：镜面前方的房间被镜像写入镜面后方的空腔，
 * 实体则各自获得一个镜像副本。倒影因此是普通的世界几何，与 Iris / Sodium 天然兼容——
 * 二次世界渲染在 Iris 下不可行（{@code WorldRenderingPipeline} 是不可重入的单帧状态机）。
 *
 * <p>代价：镜子背后的空腔会被客户端覆盖（失活时还原），所以建图时要给镜子留出足够的空腔。
 */
public final class MirrorReflectionManager {

    private static final int SCAN_INTERVAL = 20;
    private static final double ACTIVATION_DISTANCE = 32.0D;

    /** 副本用负 networkId，避开服务端下发的实体 id 空间。 */
    private static final int FIRST_COPY_ID = -2_000_000;

    private static final Map<BlockPos, MirrorReflection> ACTIVE = new HashMap<>();
    private static final Map<BlockPos, MirrorReflection.Surface> ACTIVE_SURFACES = new HashMap<>();
    private static final IntSet COPY_IDS = new IntOpenHashSet();

    private static @Nullable ClientLevel boundLevel;
    private static int nextCopyId = FIRST_COPY_ID;
    private static int scanCountdown = 0;

    private MirrorReflectionManager() {
    }

    public static void init() {
        ClientTickEvents.END_WORLD_TICK.register(MirrorReflectionManager::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> forget());
    }

    private static void tick(ClientLevel level) {
        if (boundLevel != level) {
            // 换世界/换维度：旧的 ClientLevel 已经作废，直接丢状态，不去碰它的方块。
            forget();
            boundLevel = level;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (--scanCountdown <= 0) {
            scanCountdown = SCAN_INTERVAL;
            rescan(level, player);
        }
        for (MirrorReflection reflection : ACTIVE.values()) {
            reflection.tick();
        }
    }

    private static void forget() {
        ACTIVE.clear();
        ACTIVE_SURFACES.clear();
        COPY_IDS.clear();
        nextCopyId = FIRST_COPY_ID;
        boundLevel = null;
    }

    // ---------------------------------------------------------------- 镜面发现

    private static void rescan(ClientLevel level, LocalPlayer player) {
        Map<BlockPos, MirrorReflection.Surface> found = discoverSurfaces(level, player);

        Iterator<Map.Entry<BlockPos, MirrorReflection>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, MirrorReflection> entry = it.next();
            BlockPos anchor = entry.getKey();
            MirrorReflection.Surface surface = found.get(anchor);
            // 镜面被拆改后包围盒会变，此时必须重建而不是沿用旧体积。
            if (surface == null || !surface.equals(ACTIVE_SURFACES.get(anchor))) {
                entry.getValue().close();
                ACTIVE_SURFACES.remove(anchor);
                it.remove();
            }
        }

        for (Map.Entry<BlockPos, MirrorReflection.Surface> entry : found.entrySet()) {
            if (!ACTIVE.containsKey(entry.getKey())) {
                ACTIVE.put(entry.getKey(), new MirrorReflection(level, entry.getValue()));
                ACTIVE_SURFACES.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static Map<BlockPos, MirrorReflection.Surface> discoverSurfaces(ClientLevel level, LocalPlayer player) {
        Map<BlockPos, MirrorBlockEntity> mirrors = new HashMap<>();
        int chunkRadius = Mth.ceil(ACTIVATION_DISTANCE / 16.0D) + 1;
        ChunkPos center = player.chunkPosition();
        for (int cx = center.x - chunkRadius; cx <= center.x + chunkRadius; cx++) {
            for (int cz = center.z - chunkRadius; cz <= center.z + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<BlockPos, ?> entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof MirrorBlockEntity mirror) {
                        mirrors.put(entry.getKey(), mirror);
                    }
                }
            }
        }
        if (mirrors.isEmpty()) {
            return Map.of();
        }

        Vec3 eye = player.getEyePosition();
        Map<BlockPos, MirrorReflection.Surface> surfaces = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();

        for (BlockPos seed : mirrors.keySet()) {
            if (visited.contains(seed)) {
                continue;
            }
            BlockState seedState = level.getBlockState(seed);
            if (!(seedState.getBlock() instanceof MirrorBlock)) {
                visited.add(seed);
                continue;
            }
            Direction facing = seedState.getValue(HorizontalDirectionalBlock.FACING);
            MirrorPlane plane = MirrorPlane.of(seed, facing);
            int layer = MirrorPlane.coord(seed, plane.axis());

            List<BlockPos> group = new ArrayList<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(seed);
            visited.add(seed);
            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                group.add(current);
                for (BlockPos neighbour : inPlaneNeighbours(current, plane.axis())) {
                    if (visited.contains(neighbour) || !mirrors.containsKey(neighbour)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(neighbour);
                    if (!(state.getBlock() instanceof MirrorBlock)
                            || state.getValue(HorizontalDirectionalBlock.FACING) != facing
                            || MirrorPlane.coord(neighbour, plane.axis()) != layer) {
                        continue;
                    }
                    visited.add(neighbour);
                    queue.add(neighbour);
                }
            }

            int uMin = Integer.MAX_VALUE;
            int uMax = Integer.MIN_VALUE;
            int vMin = Integer.MAX_VALUE;
            int vMax = Integer.MIN_VALUE;
            int depth = 0;
            int margin = 0;
            for (BlockPos pos : group) {
                int u = uOf(pos, plane.axis());
                int v = vOf(pos, plane.axis());
                uMin = Math.min(uMin, u);
                uMax = Math.max(uMax, u);
                vMin = Math.min(vMin, v);
                vMax = Math.max(vMax, v);
                MirrorBlockEntity mirror = mirrors.get(pos);
                depth = Math.max(depth, mirror.getDepth());
                margin = Math.max(margin, mirror.getLateralMargin());
            }

            if (!plane.inFront(eye)) {
                continue; // 站在镜子背面，没有可反射的视野
            }
            double cu = (uMin + uMax + 1) * 0.5D;
            double cv = (vMin + vMax + 1) * 0.5D;
            Vec3 apertureCenter = plane.axis() == Direction.Axis.X
                    ? new Vec3(plane.boundary(), cu, cv)
                    : new Vec3(cu, cv, plane.boundary());
            if (eye.distanceToSqr(apertureCenter) > ACTIVATION_DISTANCE * ACTIVATION_DISTANCE) {
                continue;
            }

            BlockPos anchor = group.stream().min(Comparator.naturalOrder()).orElse(seed);
            surfaces.put(anchor, new MirrorReflection.Surface(plane, uMin, uMax, vMin, vMax, depth, margin));
        }
        return surfaces;
    }

    /** 同一层内的四邻：X 面在 (Y, Z) 上展开，Z 面在 (X, Y) 上展开。 */
    private static List<BlockPos> inPlaneNeighbours(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.X
                ? List.of(pos.above(), pos.below(), pos.north(), pos.south())
                : List.of(pos.above(), pos.below(), pos.east(), pos.west());
    }

    private static int uOf(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.X ? pos.getY() : pos.getX();
    }

    private static int vOf(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.X ? pos.getZ() : pos.getY();
    }

    // ---------------------------------------------------------------- 副本工厂

    static boolean canReflect(Entity entity) {
        if (COPY_IDS.contains(entity.getId())) {
            return false; // 别让倒影再照出倒影
        }
        return entity instanceof LivingEntity || entity instanceof ItemEntity;
    }

    static @Nullable Entity createCopy(ClientLevel level, Entity source) {
        Entity copy;
        if (source instanceof Player player) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), player.getGameProfile().getName());
            copy = new MirrorPlayerCopy(level, profile, player.getUUID());
        } else {
            copy = source.getType().create(level);
            if (copy == null) {
                return null;
            }
            try {
                CompoundTag tag = new CompoundTag();
                source.saveWithoutId(tag);
                copy.load(tag);
            } catch (Throwable ignored) {
                // 带 CCA 组件的实体在客户端序列化可能抛异常；退化成裸副本，位置与姿态仍会每 tick 同步。
            }
            // load() 会把源实体的 UUID 一并写入，必须在其后覆盖：EntityLookup 见到重复 UUID 会直接丢弃副本。
            copy.setUUID(UUID.randomUUID());
            copy.noPhysics = true;
            copy.setNoGravity(true);
            copy.setSilent(true);
        }
        copy.setId(nextCopyId--);
        COPY_IDS.add(copy.getId());
        return copy;
    }

    static void discardCopy(ClientLevel level, Entity copy) {
        COPY_IDS.remove(copy.getId());
        level.removeEntity(copy.getId(), Entity.RemovalReason.DISCARDED);
    }
}
