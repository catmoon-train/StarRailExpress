package pro.fazeclan.river.stupid_express.role.arsonist;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纵火犯短过场的只读区块预览，不添加票据、不改变服务端视距或玩家追踪。
 * 初始化时注册一次；服务端主线程先 prepare，再发轨道/Intro；结束、取消及传送前 restore。
 * 过场期间应固定玩家本体，避免原版移动/其它相机系统重新设置客户端缓存中心。
 */
public final class ArsonistOverviewChunks {
    public static final int MAX_RADIUS = 32;
    public static final int MAX_SNAPSHOTS = 512;
    private static final Logger LOGGER = LoggerFactory.getLogger(ArsonistOverviewChunks.class);
    private static final Map<ServerPlayer, Preview> ACTIVE = new IdentityHashMap<>();
    private static boolean registered;

    private ArsonistOverviewChunks() {}

    /** normalRadius 是原版登录/视距包的值，并非玩家请求的视距或渲染设置。 */
    public record PrepS2C(ResourceLocation dimension, int centerX, int centerZ,
                          int renderRadius, int normalRadius, int returnX, int returnZ)
            implements CustomPacketPayload {
        public static final Type<PrepS2C> ID = new Type<>(id("arsonist_overview_prepare"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PrepS2C> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeResourceLocation(packet.dimension());
                    buf.writeInt(packet.centerX());
                    buf.writeInt(packet.centerZ());
                    buf.writeVarInt(packet.renderRadius());
                    buf.writeVarInt(packet.normalRadius());
                    buf.writeInt(packet.returnX());
                    buf.writeInt(packet.returnZ());
                }, buf -> new PrepS2C(buf.readResourceLocation(), buf.readInt(), buf.readInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record RestoreS2C(ResourceLocation dimension, int centerX, int centerZ, int normalRadius)
            implements CustomPacketPayload {
        public static final Type<RestoreS2C> ID = new Type<>(id("arsonist_overview_restore"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RestoreS2C> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    buf.writeResourceLocation(packet.dimension());
                    buf.writeInt(packet.centerX());
                    buf.writeInt(packet.centerZ());
                    buf.writeVarInt(packet.normalRadius());
                }, buf -> new RestoreS2C(buf.readResourceLocation(), buf.readInt(), buf.readInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** 客户端超时或换世界时只能撤销自己的现有预览，不能指定区块或要求加载。 */
    public record FinishC2S() implements CustomPacketPayload {
        public static final Type<FinishC2S> ID = new Type<>(id("arsonist_overview_finish"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FinishC2S> CODEC =
                StreamCodec.of((buf, packet) -> {}, buf -> new FinishC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void register() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.playS2C().register(PrepS2C.ID, PrepS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(RestoreS2C.ID, RestoreS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(FinishC2S.ID, FinishC2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FinishC2S.ID,
                (packet, context) -> restore(context.player()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIVE.remove(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    /** 只扫描实际 XZ 矩形内、距地图中心不超过 32 区块的已加载 FULL 区块。 */
    public static void prepare(ServerLevel level, List<ServerPlayer> viewers, AABB area) {
        if (level == null || viewers == null || area == null || viewers.isEmpty()) return;
        requireServerThread(level);
        if (!validBounds(area)) return;
        int centerX = (int) Math.floor((area.minX + area.maxX) / 32.0);
        int centerZ = (int) Math.floor((area.minZ + area.maxZ) / 32.0);
        int minX = Math.max(centerX - MAX_RADIUS, (int) Math.floor(area.minX / 16.0));
        int maxX = Math.min(centerX + MAX_RADIUS, (int) Math.ceil(area.maxX / 16.0) - 1);
        int minZ = Math.max(centerZ - MAX_RADIUS, (int) Math.floor(area.minZ / 16.0));
        int maxZ = Math.min(centerZ + MAX_RADIUS, (int) Math.ceil(area.maxZ / 16.0) - 1);
        int dx = Math.max(centerX - minX, maxX - centerX);
        int dz = Math.max(centerZ - minZ, maxZ - centerZ);
        int renderRadius = Math.min(MAX_RADIUS, Math.max(2, (int) Math.ceil(Math.hypot(dx, dz)) + 1));
        List<ChunkPos> candidates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) candidates.add(new ChunkPos(x, z));
        }
        candidates.sort(Comparator.comparingLong(pos ->
                (long) (pos.x - centerX) * (pos.x - centerX) + (long) (pos.z - centerZ) * (pos.z - centerZ)));
        List<ClientboundLevelChunkWithLightPacket> packets = new ArrayList<>();
        int unavailable = 0;
        int omitted = 0;
        for (ChunkPos pos : candidates) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                unavailable++;
            } else if (packets.size() < MAX_SNAPSHOTS) {
                packets.add(snapshot(level, chunk));
            } else {
                omitted++;
            }
        }
        boolean clipped = area.minX < (centerX - MAX_RADIUS) * 16.0
                || area.maxX > (centerX + MAX_RADIUS + 1) * 16.0
                || area.minZ < (centerZ - MAX_RADIUS) * 16.0
                || area.maxZ > (centerZ + MAX_RADIUS + 1) * 16.0;
        if (unavailable > 0 || omitted > 0 || clipped) {
            LOGGER.warn("纵火犯俯瞰预览不完整：发送 {}，未加载 FULL {}，超过快照上限 {}，范围裁剪 {}；不额外加载区块",
                    packets.size(), unavailable, omitted, clipped);
        }
        Map<Long, ClientboundLevelChunkWithLightPacket> sharedBackups = new HashMap<>();
        List<ChunkPos> previewPositions = packets.stream().map(packet -> new ChunkPos(packet.getX(), packet.getZ())).toList();
        for (ServerPlayer viewer : viewers) {
            if (viewer == null || viewer.serverLevel() != level || viewer.hasDisconnected()
                    || !ServerPlayNetworking.canSend(viewer, PrepS2C.ID)
                    || !ServerPlayNetworking.canSend(viewer, RestoreS2C.ID)) continue;
            restore(viewer);
            if (packets.isEmpty()) continue;
            int normalRadius = viewer.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view
                    ? view.viewDistance() : level.getServer().getPlayerList().getViewDistance();
            // 原版通常为 2..32；不缩小其它模组设置的更大缓存，以免丢弃无法有界恢复的原区块。
            if (normalRadius < 2 || normalRadius > MAX_RADIUS) continue;
            ChunkPos origin = viewer.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view
                    ? view.center() : viewer.chunkPosition();
            int temporaryStorageRadius = Math.max(normalRadius, renderRadius) + 3;
            int originalStorageRadius = normalRadius + 3;
            Map<Long, ClientboundLevelChunkWithLightPacket> displaced = new LinkedHashMap<>();
            for (ChunkPos pos : previewPositions) {
                int oldX = displacedCoordinate(pos.x, origin.x, temporaryStorageRadius);
                int oldZ = displacedCoordinate(pos.z, origin.z, temporaryStorageRadius);
                if ((oldX == pos.x && oldZ == pos.z)
                        || Math.abs(oldX - origin.x) > originalStorageRadius
                        || Math.abs(oldZ - origin.z) > originalStorageRadius) continue;
                long key = ChunkPos.asLong(oldX, oldZ);
                ClientboundLevelChunkWithLightPacket backup = sharedBackups.get(key);
                if (backup == null) {
                    LevelChunk original = level.getChunkSource().getChunkNow(oldX, oldZ);
                    if (original == null) continue;
                    backup = snapshot(level, original);
                    sharedBackups.put(key, backup);
                }
                displaced.put(key, backup);
            }
            ACTIVE.put(viewer, new Preview(level, origin, normalRadius, previewPositions, displaced));
            // 同一连接有序发包；Fabric 接收器在客户端主线程直接完成准备，不再二次异步排队。
            ServerPlayNetworking.send(viewer, new PrepS2C(level.dimension().location(), centerX, centerZ,
                    renderRadius, normalRadius, origin.x, origin.z));
            for (ClientboundLevelChunkWithLightPacket packet : packets) viewer.connection.send(packet);
        }
    }

    /** 幂等；先恢复客户端中心/半径，再补回循环缓存中被预览覆盖的原区块（最多 512 个）。 */
    public static void restore(ServerPlayer viewer) {
        if (viewer == null) return;
        requireServerThread(viewer.serverLevel());
        Preview preview = ACTIVE.remove(viewer);
        if (preview == null || viewer.hasDisconnected()) return;
        boolean sameLevel = viewer.serverLevel() == preview.level();
        if (sameLevel) {
            for (ChunkPos pos : preview.positions()) {
                if (Math.abs(pos.x - preview.origin().x) > preview.normalRadius() + 3
                        || Math.abs(pos.z - preview.origin().z) > preview.normalRadius() + 3) {
                    viewer.connection.send(new ClientboundForgetLevelChunkPacket(pos));
                }
            }
        }
        ServerPlayNetworking.send(viewer, new RestoreS2C(preview.level().dimension().location(),
                preview.origin().x, preview.origin().z, preview.normalRadius()));
        if (!sameLevel) return; // 绝不把旧维度快照塞进重生后的新世界。
        for (Map.Entry<Long, ClientboundLevelChunkWithLightPacket> entry : preview.displaced().entrySet()) {
            ChunkPos pos = new ChunkPos(entry.getKey());
            LevelChunk current = preview.level().getChunkSource().getChunkNow(pos.x, pos.z);
            viewer.connection.send(current == null ? entry.getValue() : snapshot(preview.level(), current));
        }
    }

    // 1.21.1 的 ClientChunkCache.Storage 使用 floorMod 坐标索引，存储半径为 viewRadius + 3。
    private static int displacedCoordinate(int coordinate, int origin, int storageRadius) {
        return origin + Math.floorMod(coordinate - origin + storageRadius, storageRadius * 2 + 1) - storageRadius;
    }

    private static ClientboundLevelChunkWithLightPacket snapshot(ServerLevel level, LevelChunk chunk) {
        return new ClientboundLevelChunkWithLightPacket(chunk, level.getChunkSource().getLightEngine(), null, null);
    }

    private static boolean validBounds(AABB area) {
        return Double.isFinite(area.minX) && Double.isFinite(area.maxX)
                && Double.isFinite(area.minZ) && Double.isFinite(area.maxZ)
                && area.minX < area.maxX && area.minZ < area.maxZ
                && Math.abs(area.minX) <= 30_000_000 && Math.abs(area.maxX) <= 30_000_000
                && Math.abs(area.minZ) <= 30_000_000 && Math.abs(area.maxZ) <= 30_000_000;
    }

    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("俯瞰区块准备/恢复必须在服务端主线程调用");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("stupid_express", path);
    }

    private record Preview(ServerLevel level, ChunkPos origin, int normalRadius, List<ChunkPos> positions,
                           Map<Long, ClientboundLevelChunkWithLightPacket> displaced) {}
}
