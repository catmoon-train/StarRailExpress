package io.wifi.starrailexpress.scenery.server;

import io.netty.buffer.Unpooled;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.MapManager;
import io.wifi.starrailexpress.scenery.SceneAsset;
import io.wifi.starrailexpress.scenery.SceneAssetCodec;
import io.wifi.starrailexpress.scenery.SceneGeometry;
import io.wifi.starrailexpress.scenery.SceneRegistryFingerprint;
import io.wifi.starrailexpress.scenery.network.SceneAssetNetwork;
import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SceneAssetServer {
    public static final int CHUNK_BYTES = 256 * 1024;
    private static final int CAPTURE_SECTIONS_PER_TICK = 8;

    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, State> STATES =
            new ConcurrentHashMap<>();

    private SceneAssetServer() {
    }

    public static CompletableFuture<PublishResult> publish(ServerLevel level, String mapName, boolean force) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (!areas.isSceneAreaConfigured()) {
            return CompletableFuture.completedFuture(PublishResult.failure("当前地图未配置 sceneArea"));
        }

        State state = state(level);
        SceneGeometry.SectionBounds bounds = SceneGeometry.sectionBounds(areas.getSceneArea());
        synchronized (state) {
            if (state.captureJob != null && force && !state.publishing) {
                state.captureJob.future.completeExceptionally(
                        new IllegalStateException("transient capture replaced by forced publish"));
                state.captureJob = null;
                state.transientGeneration++;
            } else if (state.publishing || state.captureJob != null) {
                return CompletableFuture.completedFuture(PublishResult.failure("场景资产正在发布"));
            }
            state.publishing = true;
            state.publishStartedAt = System.currentTimeMillis();
            state.lastLoggedProgress = -1;
        }

        SRE.LOGGER.info("[ScenePublish] start map={} scene={} sections={} force={}",
                mapName, areas.getSceneId(), bounds.sectionCount(), force);
        CompletableFuture<PublishResult> resultFuture = new CompletableFuture<>();
        CompletableFuture<SceneAsset> captureFuture;
        try {
            captureFuture = captureOverTicks(level, areas.getSceneArea());
        } catch (Throwable error) {
            finishPublishFailure(state, mapName, error, resultFuture);
            return resultFuture;
        }
        captureFuture.thenApplyAsync(captured -> {
            SRE.LOGGER.info("[ScenePublish] capture-complete map={} sections={}, encoding asset",
                    mapName, captured.sections().size());
            try {
                byte[] bytes = SceneAssetCodec.encode(captured);
                String hash = SceneAssetCodec.sha256(bytes);
                Path path = assetPath(level, hash);
                Files.createDirectories(path.getParent());
                Path temp = path.resolveSibling(path.getFileName() + ".tmp");
                Files.write(temp, bytes);
                atomicMove(temp, path);
                return new EncodedAsset(hash, bytes.length, path);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((encoded, error) -> {
            level.getServer().execute(() -> {
                if (error != null) {
                    finishPublishFailure(state, mapName, error, resultFuture);
                    return;
                }
                try {
                    areas.setSceneAssetHash(encoded.hash);
                    areas.sync();
                    MapManager.updateSceneAssetMetadata(level, mapName, encoded.hash, areas.getSceneScroll());
                    state.hash = encoded.hash;
                    state.size = encoded.size;
                    state.dirty = false;
                    state.dirtyReason = "";
                    state.publishing = false;
                    state.publishedAt = System.currentTimeMillis();
                    state.transientHash = "";
                    state.transientBytes = null;
                    state.transientGeneration++;
                    SceneAssetNetwork.sendManifestToAll(level);
                    long elapsed = System.currentTimeMillis() - state.publishStartedAt;
                    SRE.LOGGER.info("[ScenePublish] complete map={} scene={} hash={} bytes={} elapsedMs={}",
                            mapName, areas.getSceneId(), encoded.hash, encoded.size, elapsed);
                    resultFuture.complete(PublishResult.success(encoded.hash, encoded.size));
                } catch (Exception e) {
                    finishPublishFailure(state, mapName, e, resultFuture);
                }
            });
        });
        return resultFuture;
    }

    public static SceneAsset capture(ServerLevel level, AABB sourceArea) throws IOException {
        SceneGeometry.SectionBounds bounds = SceneGeometry.sectionBounds(sourceArea);
        List<SceneAsset.SectionData> sections = new ArrayList<>(bounds.sectionCount());

        for (int chunkX = bounds.minX(); chunkX <= bounds.maxX(); chunkX++) {
            for (int chunkZ = bounds.minZ(); chunkZ <= bounds.maxZ(); chunkZ++) {
                for (int sectionY = bounds.minY(); sectionY <= bounds.maxY(); sectionY++) {
                    SceneAsset.SectionData section = captureSection(level, chunkX, sectionY, chunkZ);
                    if (section != null) {
                        sections.add(section);
                    }
                }
            }
        }

        return new SceneAsset(
                SceneAsset.CURRENT_SCHEMA,
                SharedConstants.getCurrentVersion().getName(),
                SceneRegistryFingerprint.compute(level.registryAccess()),
                sourceArea,
                sections);
    }

    public static CompletableFuture<ValidateResult> validate(ServerLevel level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (!areas.isSceneAreaConfigured() || !SceneAssetCodec.isValidHash(areas.getSceneAssetHash())) {
            return CompletableFuture.completedFuture(
                    new ValidateResult(false, false, "", "当前地图没有可校验的已发布场景资产"));
        }

        State state = state(level);
        synchronized (state) {
            if (state.publishing || state.captureJob != null) {
                return CompletableFuture.completedFuture(
                        new ValidateResult(false, false, "", "另一个场景捕获任务正在运行"));
            }
        }

        String publishedHash = areas.getSceneAssetHash();
        CompletableFuture<SceneAsset> captureFuture;
        try {
            captureFuture = captureOverTicks(level, areas.getSceneArea());
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    new ValidateResult(false, false, "", "捕获场景失败: " + e.getMessage()));
        }
        return captureFuture.thenApplyAsync(captured -> {
            try {
                String currentHash = SceneAssetCodec.sha256(SceneAssetCodec.encode(captured));
                boolean matches = publishedHash.equals(currentHash);
                return new ValidateResult(true, matches, currentHash,
                        matches ? "场景内容未变化" : "场景内容与已发布资产不同");
            } catch (IOException e) {
                return new ValidateResult(false, false, "", "校验失败: " + e.getMessage());
            }
        }).exceptionally(error -> new ValidateResult(false, false, "",
                "校验失败: " + rootMessage(error))).thenApply(result -> {
            level.getServer().execute(() -> {
                if (result.success() && result.matches()) {
                    state.dirty = false;
                    state.dirtyReason = "";
                    state.transientHash = "";
                    state.transientBytes = null;
                    state.transientGeneration++;
                } else if (result.success()) {
                    state.dirty = true;
                    state.dirtyReason = "完整校验发现内容变化";
                }
                SceneAssetNetwork.sendManifestToAll(level);
            });
            return result;
        });
    }

    public static void tick(MinecraftServer server) {
        for (Map.Entry<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, State> entry
                : STATES.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            CaptureJob job = entry.getValue().captureJob;
            if (level == null || job == null) {
                continue;
            }
            try {
                for (int i = 0; i < CAPTURE_SECTIONS_PER_TICK && job.cursor < job.positions.size(); i++) {
                    SectionPos pos = job.positions.get(job.cursor++);
                    SceneAsset.SectionData section = captureSection(level, pos.x(), pos.y(), pos.z());
                    if (section != null) {
                        job.sections.add(section);
                    }
                }
                if (entry.getValue().publishing && !job.positions.isEmpty()) {
                    int progress = job.cursor * 100 / job.positions.size();
                    int progressBucket = Math.min(100, progress / 10 * 10);
                    if (progressBucket > entry.getValue().lastLoggedProgress) {
                        entry.getValue().lastLoggedProgress = progressBucket;
                        SRE.LOGGER.info("[ScenePublish] capture-progress dimension={} progress={} captured={}/{}",
                                entry.getKey().location(), progressBucket, job.cursor, job.positions.size());
                    }
                }
                if (job.cursor >= job.positions.size()) {
                    entry.getValue().captureJob = null;
                    job.future.complete(new SceneAsset(
                            SceneAsset.CURRENT_SCHEMA,
                            job.minecraftVersion,
                            job.registryFingerprint,
                            job.sourceArea,
                            job.sections));
                }
            } catch (Throwable error) {
                entry.getValue().captureJob = null;
                SRE.LOGGER.error("[ScenePublish] capture-failed dimension={} captured={}/{}",
                        entry.getKey().location(), job.cursor, job.positions.size(), error);
                job.future.completeExceptionally(error);
            }
        }
    }

    public static void activate(ServerLevel level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        State state = state(level);
        state.hash = areas.getSceneAssetHash();
        state.size = SceneAssetCodec.isValidHash(state.hash) ? fileSize(assetPath(level, state.hash)) : 0L;
        state.dirty = !SceneAssetCodec.isValidHash(state.hash)
                || !Files.isRegularFile(assetPath(level, state.hash));
        state.dirtyReason = state.dirty ? "已发布场景资产缺失" : "";
        state.publishing = false;
        state.transientHash = "";
        state.transientBytes = null;
        state.transientGeneration++;
        ensureTransient(level);
    }

    public static void invalidate(ServerLevel level, String reason) {
        State state = state(level);
        state.dirty = true;
        state.dirtyReason = reason == null || reason.isBlank() ? "手动失效" : reason;
        state.transientHash = "";
        state.transientBytes = null;
        state.transientGeneration++;
    }

    public static void markBlockChanged(ServerLevel level, net.minecraft.core.BlockPos pos) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (SREGameWorldComponent.KEY.get(level).isRunning()) {
            return;
        }
        if (areas.isSceneAreaConfigured() && areas.getSceneArea().contains(pos.getCenter())) {
            invalidate(level, "场景区域方块发生变化");
        }
    }

    public static Status status(ServerLevel level) {
        State state = state(level);
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        SceneGeometry.SectionBounds bounds = SceneGeometry.sectionBounds(areas.getSceneArea());
        return new Status(state.hash, state.size, state.dirty, state.dirtyReason,
                state.publishing || state.captureJob != null,
                bounds.sectionCount(), bounds.chunkCount(), areas.getSceneArea());
    }

    public static byte[] readAsset(ServerLevel level, String hash) throws IOException {
        if (!SceneAssetCodec.isValidHash(hash)) {
            throw new IOException("Invalid scene asset hash");
        }
        Path path = assetPath(level, hash);
        byte[] bytes = Files.readAllBytes(path);
        if (!SceneAssetCodec.sha256(bytes).equals(hash)) {
            throw new IOException("Scene asset hash mismatch");
        }
        return bytes;
    }

    public static void ensureTransient(ServerLevel level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        State state = state(level);
        if (!areas.isSceneAreaConfigured()
                || (!state.dirty && SceneAssetCodec.isValidHash(areas.getSceneAssetHash())
                        && Files.isRegularFile(assetPath(level, areas.getSceneAssetHash())))
                || state.publishing || state.captureJob != null || state.transientBytes != null) {
            return;
        }

        long generation = state.transientGeneration;
        captureOverTicks(level, areas.getSceneArea()).thenApplyAsync(asset -> {
            try {
                return SceneAssetCodec.encode(asset);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((bytes, error) -> level.getServer().execute(() -> {
            if (error != null) {
                SRE.LOGGER.warn("Unable to create transient scene snapshot", error);
                return;
            }
            if (!state.publishing && state.transientGeneration == generation) {
                state.transientBytes = bytes;
                state.transientHash = SceneAssetCodec.sha256(bytes);
                SceneAssetNetwork.sendManifestToAll(level);
            }
        }));
    }

    public static NetworkAsset networkAsset(ServerLevel level) {
        State state = state(level);
        if (state.transientBytes != null && SceneAssetCodec.isValidHash(state.transientHash)) {
            return new NetworkAsset(state.transientHash, state.transientBytes.length, true);
        }
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        String hash = areas.getSceneAssetHash();
        if (SceneAssetCodec.isValidHash(hash) && Files.isRegularFile(assetPath(level, hash))) {
            return new NetworkAsset(hash, fileSize(assetPath(level, hash)), false);
        }
        return new NetworkAsset("", 0L, false);
    }

    public static byte[] transientBytes(ServerLevel level, String hash) {
        State state = state(level);
        return hash.equals(state.transientHash) && state.transientBytes != null
                ? state.transientBytes
                : null;
    }

    public static void sendCurrentManifest(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            SceneAssetNetwork.sendManifest(player, level);
        }
    }

    public static Path assetPath(ServerLevel level, String hash) {
        return level.getServer().getWorldPath(LevelResource.ROOT)
                .resolve("scene_assets")
                .resolve(hash + ".sresc");
    }

    private static State state(ServerLevel level) {
        return STATES.computeIfAbsent(level.dimension(), key -> new State());
    }

    private static CompletableFuture<SceneAsset> captureOverTicks(ServerLevel level, AABB sourceArea) {
        State state = state(level);
        SceneGeometry.SectionBounds bounds = SceneGeometry.sectionBounds(sourceArea);
        List<SectionPos> positions = new ArrayList<>(bounds.sectionCount());
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    positions.add(SectionPos.of(x, y, z));
                }
            }
        }
        CaptureJob job = new CaptureJob(
                sourceArea,
                SharedConstants.getCurrentVersion().getName(),
                SceneRegistryFingerprint.compute(level.registryAccess()),
                positions);
        synchronized (state) {
            if (state.captureJob != null) {
                return CompletableFuture.failedFuture(new IllegalStateException("scene capture already running"));
            }
            state.captureJob = job;
        }
        return job.future;
    }

    private static SceneAsset.SectionData captureSection(ServerLevel level, int chunkX, int sectionY, int chunkZ)
            throws IOException {
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        int index = chunk.getSectionIndexFromSectionY(sectionY);
        if (index < 0 || index >= chunk.getSections().length) {
            return null;
        }
        LevelChunkSection section = chunk.getSection(index);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer(section.getSerializedSize()));
        byte[] payload;
        try {
            section.write(buffer);
            payload = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), payload);
        } finally {
            buffer.release();
        }

        SectionPos sectionPos = SectionPos.of(chunkX, sectionY, chunkZ);
        var lightEngine = level.getChunkSource().getLightEngine();
        byte[] sky = lightBytes(lightEngine.getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos));
        byte[] block = lightBytes(lightEngine.getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos));
        return new SceneAsset.SectionData(chunkX, sectionY, chunkZ, payload, sky, block);
    }

    private static byte[] lightBytes(DataLayer layer) {
        return layer == null ? new byte[0] : layer.getData().clone();
    }

    private static long fileSize(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static void finishPublishFailure(State state, String mapName, Throwable error,
            CompletableFuture<PublishResult> resultFuture) {
        state.publishing = false;
        state.dirty = true;
        state.dirtyReason = "发布失败";
        String message = rootMessage(error);
        SRE.LOGGER.error("[ScenePublish] failed map={} reason={}", mapName, message, error);
        resultFuture.complete(PublishResult.failure("发布场景资产失败: " + message));
    }

    private record EncodedAsset(String hash, long size, Path path) {
    }

    private static final class State {
        private volatile String hash = "";
        private volatile long size;
        private volatile boolean dirty;
        private volatile String dirtyReason = "";
        private volatile boolean publishing;
        private volatile long publishedAt;
        private volatile long publishStartedAt;
        private volatile int lastLoggedProgress = -1;
        private volatile CaptureJob captureJob;
        private volatile String transientHash = "";
        private volatile byte[] transientBytes;
        private volatile long transientGeneration;
    }

    private static final class CaptureJob {
        private final AABB sourceArea;
        private final String minecraftVersion;
        private final String registryFingerprint;
        private final List<SectionPos> positions;
        private final List<SceneAsset.SectionData> sections;
        private final CompletableFuture<SceneAsset> future = new CompletableFuture<>();
        private int cursor;

        private CaptureJob(AABB sourceArea, String minecraftVersion, String registryFingerprint,
                List<SectionPos> positions) {
            this.sourceArea = sourceArea;
            this.minecraftVersion = minecraftVersion;
            this.registryFingerprint = registryFingerprint;
            this.positions = positions;
            this.sections = new ArrayList<>(positions.size());
        }
    }

    public record Status(String hash, long size, boolean dirty, String dirtyReason, boolean publishing,
            int sectionCount, int chunkCount, AABB sourceArea) {
    }

    public record PublishResult(boolean success, String message, String hash, long size) {
        public static PublishResult success(String hash, long size) {
            return new PublishResult(true, "ok", hash, size);
        }

        public static PublishResult failure(String message) {
            return new PublishResult(false, message, "", 0L);
        }
    }

    public record ValidateResult(boolean success, boolean matches, String currentHash, String message) {
    }

    public record NetworkAsset(String hash, long size, boolean transientAsset) {
    }
}
