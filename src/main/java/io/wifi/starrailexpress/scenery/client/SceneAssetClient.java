package io.wifi.starrailexpress.scenery.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.CRC32;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import io.netty.buffer.Unpooled;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.gui.screen.map_dev.SceneManagerScreen;
import io.wifi.starrailexpress.scenery.SceneAsset;
import io.wifi.starrailexpress.scenery.SceneAssetCodec;
import io.wifi.starrailexpress.scenery.SceneGeometry;
import io.wifi.starrailexpress.scenery.SceneRegistryFingerprint;
import io.wifi.starrailexpress.scenery.network.SceneAssetNetwork;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;

import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SceneAssetClient {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long DEFAULT_LIMIT = 2L * 1024L * 1024L * 1024L;
    private static final long DEFAULT_SINGLE_LIMIT = 512L * 1024L * 1024L;
    private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("sre_scene_cache");
    private static final Path CACHE = ROOT.resolve("cache");
    private static final Path IMPORTS = ROOT.resolve("imports");
    private static final Path EXPORTS = ROOT.resolve("exports");
    private static final Path QUARANTINE = ROOT.resolve("quarantine");
    private static final Path INDEX_FILE = ROOT.resolve("index.json");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Long2ObjectOpenHashMap<LevelChunk> CHUNKS = new Long2ObjectOpenHashMap<>();
    private static final LongOpenHashSet ACTIVE_SECTIONS = new LongOpenHashSet();
    private static long[] activeSectionSnapshot = new long[0];
    private static final LongOpenHashSet NOTIFIED_CHUNKS = new LongOpenHashSet();
    private static final List<PreviewBlock> PREVIEW_BLOCKS = new ArrayList<>();
    private static final int MAX_PREVIEW_BLOCKS = 50_000;
    private static final int VIRTUAL_SECTION_X = 16_384;
    private static final int VIRTUAL_SECTION_Z = 16_384;
    private static final Vec3 BASE_VIRTUAL_BLOCK_OFFSET =
            new Vec3(SectionPos.sectionToBlockCoord(VIRTUAL_SECTION_X), 0.0D,
                    SectionPos.sectionToBlockCoord(VIRTUAL_SECTION_Z));
    private static Vec3 virtualBlockOffset = BASE_VIRTUAL_BLOCK_OFFSET;

    private static CacheIndex index = new CacheIndex();
    private static SceneAssetNetwork.ManifestS2C manifest;
    private static SceneAsset currentAsset;
    private static String currentHash = "";
    private static String downloadingHash = "";
    private static long downloadSize;
    private static int downloadGeneration;
    private static boolean remoteDownloading;
    private static SceneAssetNetwork.ManifestS2C pendingManifest;
    private static String lastCompatibilityErrorHash = "";
    private static String lastLegacyWarningHash = "";
    private static String lastEmbeddedCompatibilityWarningHash = "";
    private static String lastOverlapWarningHash = "";
    private static String lastStaleWarningMap = "";
    private static String failedDownloadHash = "";
    private static int failedDownloadAttempts;
    private static boolean previewEnabled;
    private static boolean previewPaused;
    private static float previewAlpha = 0.35F;
    private static float previewSpeed = 1.0F;
    private static double previewDistance;
    private static boolean sceneWasRunning;
    private static double sceneMotionStartTime;
    private static boolean continuousLoopAvailable = true;

    private SceneAssetClient() {
    }

    public static void initialize() {
        try {
            Files.createDirectories(CACHE);
            Files.createDirectories(IMPORTS);
            Files.createDirectories(EXPORTS);
            Files.createDirectories(QUARANTINE);
            loadIndex();
            enforceLimit();
        } catch (IOException e) {
            SRE.LOGGER.error("Unable to initialize scene asset cache", e);
        }
        registerCommands();
        ScenePreviewRenderer.register();
    }

    public static void handleManifest(SceneAssetNetwork.ManifestS2C next) {
        SceneAssetNetwork.ManifestS2C previous = manifest;
        if (previous != null && previous.transientAsset() && !previous.hash().equals(next.hash())) {
            try {
                Files.deleteIfExists(partPath(previous.hash()));
            } catch (IOException ignored) {
            }
        }
        manifest = next;
        if (!next.hash().equals(failedDownloadHash)) {
            resetDownloadFailures();
        }
        if (next.hash().isBlank()) {
            clearRuntime();
            return;
        }
        if (!SceneAssetCodec.isValidHash(next.hash()) || next.size() < 0L || next.size() > index.singleLimitBytes) {
            showError("服务器提供了无效或过大的场景资产");
            clearRuntime();
            return;
        }
        RegistryAccess registryAccess = localRegistryAccess();
        if (!next.trustedFastPath() && registryAccess == null) {
            pendingManifest = next;
            return;
        }
        pendingManifest = null;
        if (!next.trustedFastPath()
                && !SceneRegistryFingerprint.isCompatible(next.registryFingerprint(), registryAccess)) {
            if (!next.hash().equals(lastCompatibilityErrorHash)) {
                lastCompatibilityErrorHash = next.hash();
                showError("方块/生物群系 ID 与服务器不兼容"
                        + "（服务端=" + shortFingerprint(next.registryFingerprint())
                        + "，客户端=" + shortFingerprint(SceneRegistryFingerprint.compute(registryAccess))
                        + "）。请统一模组与数据包，或由管理员重新发布资产");
            }
            clearRuntime();
            return;
        }
        lastCompatibilityErrorHash = "";

        Path cached = cachePath(next.hash());
        if (Files.isRegularFile(cached)) {
            boolean usable = next.trustedFastPath()
                    ? fileSize(cached) == next.size()
                    : verifyTransportFile(cached, next.hash(), next.size());
            if (usable) {
                touch(next.hash(), fileSize(cached));
                loadAssetAsync(cached, next.hash(), false, next.trustedFastPath());
                return;
            }
        }
        beginDownload(next);
    }

    public static void handleChunk(SceneAssetNetwork.ChunkS2C packet) {
        if (!packet.hash().equals(downloadingHash) || packet.totalSize() != downloadSize) {
            return;
        }
        CRC32 crc = new CRC32();
        crc.update(packet.data());
        if (crc.getValue() != packet.crc32()) {
            requestChunk(packet.offset());
            return;
        }
        Path part = partPath(packet.hash());
        try {
            long expectedOffset = Files.exists(part) ? Files.size(part) : 0L;
            if (packet.offset() != expectedOffset) {
                requestChunk(expectedOffset);
                return;
            }
            try (RandomAccessFile file = new RandomAccessFile(part.toFile(), "rw")) {
                file.seek(packet.offset());
                file.write(packet.data());
            }
            long nextOffset = packet.offset() + packet.data().length;
            if (nextOffset < packet.totalSize()) {
                requestChunk(nextOffset);
                return;
            }
            boolean trusted = manifest != null && manifest.hash().equals(packet.hash())
                    && manifest.trustedFastPath() && !manifest.transientAsset();
            if ((!trusted && !verifyTransportFile(part, packet.hash(), packet.totalSize()))
                    || (trusted && fileSize(part) != packet.totalSize())) {
                quarantine(part, packet.hash() + "-download");
                handleDownloadFailure(packet.hash(), "下载文件的大小或 SHA-256 与服务器清单不一致");
                return;
            }
            resetDownloadFailures();
            downloadingHash = "";
            downloadSize = 0L;
            if (manifest != null && manifest.transientAsset()) {
                loadAssetAsync(part, packet.hash(), true, false);
            } else {
                Path target = cachePath(packet.hash());
                atomicMove(part, target);
                Files.deleteIfExists(remotePartPath(packet.hash()));
                touch(packet.hash(), packet.totalSize());
                enforceLimit();
                loadAssetAsync(target, packet.hash(), false, trusted);
            }
        } catch (IOException e) {
            showError("场景资产写入失败: " + e.getMessage());
            SRE.LOGGER.error("Unable to write scene asset download", e);
        }
    }

    public static void openEditor() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            previewEnabled = true;
            if (currentAsset != null && PREVIEW_BLOCKS.isEmpty()) {
                rebuildPreviewBlocks(currentAsset.sourceArea());
            } else if (currentAsset != null) {
                ScenePreviewRenderer.rebuild(PREVIEW_BLOCKS,
                        SceneGeometry.expandedArea(currentAsset.sourceArea()));
            }
            client.player.connection.sendCommand("sre:scene manager");
        }
    }

    public static void closeEditor() {
        // The projection is a world tool, not a screen overlay. Keep it alive after
        // closing the editor; the explicit preview toggle controls its visibility.
    }

    public static void refreshPreview() {
        previewEnabled = true;
        if (currentAsset != null) {
            rebuildPreviewBlocks(currentAsset.sourceArea());
        }
    }

    public static void clearRuntime() {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            client.execute(SceneAssetClient::clearRuntime);
            return;
        }
        downloadGeneration++;
        downloadingHash = "";
        downloadSize = 0L;
        remoteDownloading = false;
        pendingManifest = null;
        resetDownloadFailures();
        if (manifest != null && manifest.transientAsset() && SceneAssetCodec.isValidHash(manifest.hash())) {
            try {
                Files.deleteIfExists(partPath(manifest.hash()));
            } catch (IOException ignored) {
            }
        }
        clearChunks();
        previewEnabled = false;
        previewDistance = 0.0D;
    }

    private static void clearChunks() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            for (long packed : NOTIFIED_CHUNKS) {
                LevelChunk chunk = CHUNKS.get(packed);
                if (chunk != null) {
                    client.level.unload(chunk);
                    client.level.getChunkSource().getLightEngine().retainData(chunk.getPos(), false);
                }
            }
        }
        CHUNKS.clear();
        ACTIVE_SECTIONS.clear();
        activeSectionSnapshot = new long[0];
        NOTIFIED_CHUNKS.clear();
        PREVIEW_BLOCKS.clear();
        ScenePreviewRenderer.release();
        currentAsset = null;
        currentHash = "";
        virtualBlockOffset = BASE_VIRTUAL_BLOCK_OFFSET;
        continuousLoopAvailable = true;
    }

    public static LevelChunk getRemoteChunk(int chunkX, int chunkZ) {
        return isFormalSceneVisible() ? CHUNKS.get(ChunkPos.asLong(chunkX, chunkZ)) : null;
    }

    public static boolean isActiveSection(int sectionX, int sectionY, int sectionZ) {
        return isFormalSceneVisible()
                && ACTIVE_SECTIONS.contains(SectionPos.asLong(sectionX, sectionY, sectionZ));
    }

    public static long[] activeSections() {
        return isFormalSceneVisible() ? activeSectionSnapshot : new long[0];
    }

    public static boolean hasActiveAsset() {
        return isFormalSceneVisible() && currentAsset != null && !ACTIVE_SECTIONS.isEmpty();
    }

    public static void openSceneManager(List<String> sceneIds, String currentSceneId) {
        Minecraft.getInstance().setScreen(new SceneManagerScreen(sceneIds, currentSceneId));
    }

    public static boolean isMovingSceneEnabled() {
        return SREClientConfig.instance().enableMovingScenes;
    }

    public static void setMovingSceneEnabled(boolean enabled) {
        SREClientConfig.instance().enableMovingScenes = enabled;
        SREClientConfig.HANDLER.save();
        updateSceneLifecycle();
    }

    public static String currentHash() {
        return currentHash;
    }

    public static SceneAsset currentAsset() {
        return currentAsset;
    }

    public static SceneAssetNetwork.ManifestS2C manifest() {
        return manifest;
    }

    public static boolean isRemoteDownloading() {
        return remoteDownloading;
    }

    public static Vec3 renderOffset(float partialTick) {
        AreasWorldComponent areas = SREClient.areaComponent;
        if (areas == null || !isFormalSceneVisible()) {
            return Vec3.ZERO;
        }
        double scroll = 0.0D;
        if (SREClient.trainComponent != null) {
            scroll = (SREClient.trainComponent.getTime() + partialTick - sceneMotionStartTime)
                    * SREClient.getTrainSpeed() / 73.8D;
        }
        AreasWorldComponent.ScrollAxis renderAxis = !continuousLoopAvailable
                && areas.getSceneScroll() == AreasWorldComponent.ScrollAxis.Y
                        ? AreasWorldComponent.ScrollAxis.NONE
                        : areas.getSceneScroll();
        return SceneGeometry.renderOffset(
                areas.getSceneArea(),
                areas.getPlayArea(),
                renderAxis,
                areas.getSceneDisplayOffset(),
                scroll).subtract(virtualBlockOffset);
    }

    public static boolean isPreviewEnabled() {
        return previewEnabled;
    }

    public static void setPreviewEnabled(boolean enabled) {
        previewEnabled = enabled;
    }

    public static boolean isPreviewPaused() {
        return previewPaused;
    }

    public static void setPreviewPaused(boolean paused) {
        previewPaused = paused;
    }

    public static float getPreviewAlpha() {
        return previewAlpha;
    }

    public static void setPreviewAlpha(float alpha) {
        previewAlpha = Math.max(0.05F, Math.min(0.9F, alpha));
        if (currentAsset != null && !PREVIEW_BLOCKS.isEmpty()) {
            ScenePreviewRenderer.rebuild(PREVIEW_BLOCKS,
                    SceneGeometry.expandedArea(currentAsset.sourceArea()));
        }
    }

    public static float getPreviewSpeed() {
        return previewSpeed;
    }

    public static void setPreviewSpeed(float speed) {
        previewSpeed = Math.max(-8.0F, Math.min(8.0F, speed));
    }

    public static void tickPreview() {
        if (pendingManifest != null && localRegistryAccess() != null) {
            SceneAssetNetwork.ManifestS2C retry = pendingManifest;
            pendingManifest = null;
            handleManifest(retry);
        }
        if (previewEnabled && !previewPaused) {
            previewDistance += previewSpeed;
        }
        updateSceneLifecycle();
    }

    public static void adjustPreviewDistance(double delta) {
        previewDistance += delta;
    }

    public static double getPreviewDistance() {
        return previewDistance;
    }

    public static Vec3 previewRenderOffset() {
        AreasWorldComponent areas = SREClient.areaComponent;
        if (areas == null) {
            return Vec3.ZERO;
        }
        return SceneGeometry.renderOffset(
                areas.getSceneArea(),
                areas.getPlayArea(),
                areas.getSceneScroll(),
                areas.getSceneDisplayOffset(),
                previewDistance);
    }

    public static List<PreviewBlock> previewBlocks() {
        return List.copyOf(PREVIEW_BLOCKS);
    }

    public static CacheStatus cacheStatus() {
        long total = index.entries.values().stream().mapToLong(entry -> entry.size).sum();
        return new CacheStatus(index.entries.size(), total, index.limitBytes, downloadingHash, currentHash);
    }

    private static void beginDownload(SceneAssetNetwork.ManifestS2C next) {
        if (next == null) {
            return;
        }
        downloadingHash = next.hash();
        downloadSize = next.size();
        int generation = ++downloadGeneration;
        if (!next.transientAsset() && !next.remoteUrl().isBlank()) {
            beginRemoteDownload(next, generation);
            return;
        }
        beginServerDownload(next, generation);
    }

    private static void beginServerDownload(SceneAssetNetwork.ManifestS2C next, int generation) {
        if (!isCurrentDownload(next, generation)) {
            return;
        }
        remoteDownloading = false;
        Path part = partPath(next.hash());
        try {
            Files.createDirectories(part.getParent());
            long offset = Files.exists(part) ? Files.size(part) : 0L;
            if (offset > next.size() || offset % io.wifi.starrailexpress.scenery.server.SceneAssetServer.CHUNK_BYTES != 0L) {
                Files.deleteIfExists(part);
                offset = 0L;
            }
            requestChunk(offset);
        } catch (IOException e) {
            showError("无法准备场景资产下载: " + e.getMessage());
        }
    }

    private static void beginRemoteDownload(SceneAssetNetwork.ManifestS2C next, int generation) {
        remoteDownloading = true;
        CompletableFuture.runAsync(() -> {
            try {
                downloadRemote(next, generation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            if (!isCurrentDownload(next, generation)) {
                return;
            }
            remoteDownloading = false;
            if (error != null) {
                SRE.LOGGER.warn("Remote scene asset download failed for {}, falling back to game transport",
                        next.remoteUrl(), error);
                showFeedback("远程场景下载失败，已自动切换到服务器传输");
                beginServerDownload(next, generation);
                return;
            }
            finishRemoteDownload(next, generation);
        }));
    }

    private static void downloadRemote(SceneAssetNetwork.ManifestS2C next, int generation)
            throws IOException, InterruptedException {
        Path part = remotePartPath(next.hash());
        Files.createDirectories(part.getParent());
        long offset = Files.isRegularFile(part) ? Files.size(part) : 0L;
        if (offset > next.size()) {
            Files.deleteIfExists(part);
            offset = 0L;
        }
        if (offset == next.size()) {
            return;
        }
        if (!isCurrentDownload(next, generation)) {
            throw new IOException("scene download superseded");
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(next.remoteUrl()))
                .timeout(Duration.ofMinutes(5))
                .header("Accept-Encoding", "identity")
                .GET();
        if (offset > 0L) {
            requestBuilder.header("Range", "bytes=" + offset + "-");
        }
        HttpResponse<InputStream> response = HTTP_CLIENT.send(
                requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        boolean append = offset > 0L && status == 206;
        if (status == 416 && offset == next.size()) {
            response.body().close();
            return;
        }
        if (status != 200 && status != 206) {
            response.body().close();
            throw new IOException("HTTP " + status);
        }
        if (status == 200) {
            offset = 0L;
            append = false;
        }

        long written = offset;
        try (InputStream input = response.body();
                var output = Files.newOutputStream(part,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (!isCurrentDownload(next, generation)) {
                    throw new IOException("scene download superseded");
                }
                written += read;
                if (written > next.size() || written > index.singleLimitBytes) {
                    throw new IOException("remote asset exceeds manifest size");
                }
                output.write(buffer, 0, read);
            }
        }
        if (written != next.size()) {
            throw new IOException("remote asset ended at " + written + " of " + next.size() + " bytes");
        }
    }

    private static void finishRemoteDownload(SceneAssetNetwork.ManifestS2C next, int generation) {
        if (!isCurrentDownload(next, generation)) {
            return;
        }
        Path part = remotePartPath(next.hash());
        boolean usable = next.trustedFastPath()
                ? fileSize(part) == next.size()
                : verifyTransportFile(part, next.hash(), next.size());
        if (!usable) {
            quarantine(part, next.hash() + "-remote");
            showFeedback("远程场景文件不可用，已自动切换到服务器传输");
            beginServerDownload(next, generation);
            return;
        }
        try {
            Path target = cachePath(next.hash());
            atomicMove(part, target);
            downloadingHash = "";
            downloadSize = 0L;
            touch(next.hash(), next.size());
            enforceLimit();
            loadAssetAsync(target, next.hash(), false, next.trustedFastPath());
        } catch (IOException e) {
            showError("无法保存远程场景资产: " + e.getMessage());
            beginServerDownload(next, generation);
        }
    }

    private static boolean isCurrentDownload(SceneAssetNetwork.ManifestS2C next, int generation) {
        return generation == downloadGeneration
                && manifest != null
                && manifest.hash().equals(next.hash())
                && downloadingHash.equals(next.hash());
    }

    private static void requestChunk(long offset) {
        if (!downloadingHash.isBlank()) {
            ClientPlayNetworking.send(new SceneAssetNetwork.RequestChunkC2S(downloadingHash, offset));
        }
    }

    private static void loadAssetAsync(Path path, String hash, boolean deleteAfterRead, boolean trustedFastPath) {
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes = Files.readAllBytes(path);
                if (!trustedFastPath && !SceneAssetCodec.sha256(bytes).equals(hash)) {
                    throw new IOException("hash mismatch");
                }
                SceneAsset asset = SceneAssetCodec.decode(bytes);
                if (!trustedFastPath
                        && !asset.minecraftVersion().equals(SharedConstants.getCurrentVersion().getName())) {
                    SRE.LOGGER.warn("Loading scene asset {} from Minecraft version {} on {}",
                            hash, asset.minecraftVersion(), SharedConstants.getCurrentVersion().getName());
                }
                return asset;
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                if (deleteAfterRead) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                }
            }
        }).whenComplete((asset, error) -> Minecraft.getInstance().execute(() -> {
            if (error != null) {
                if (!deleteAfterRead) {
                    quarantine(path, hash + "-invalid");
                }
                showError("无法读取场景资产: " + rootMessage(error));
                return;
            }
            install(asset, hash);
        }));
    }

    private static void install(SceneAsset asset, String hash) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || manifest == null || !manifest.hash().equals(hash)) {
            return;
        }
        boolean keepPreview = previewEnabled;
        clearChunks();
        if (!isScenePlacementSafe()) {
            currentAsset = asset;
            currentHash = hash;
            previewEnabled = keepPreview;
            if (!hash.equals(lastOverlapWarningHash)) {
                lastOverlapWarningHash = hash;
                showError("sceneArea 与 playArea 重叠且启用了滚动，已停用场景渲染以保护真实地图。"
                        + "请重新选择窗外源区域，或将滚动轴设为 NONE");
            }
            ClientPlayNetworking.send(new SceneAssetNetwork.ReadyC2S(hash));
            return;
        }

        Map<Long, List<VirtualSection>> byChunk = new HashMap<>();
        SceneGeometry.SectionBounds sourceBounds = SceneGeometry.sectionBounds(asset.sourceArea());
        int periodSections = switch (SREClient.areaComponent.getSceneScroll()) {
            case X -> sourceBounds.maxX() - sourceBounds.minX() + 1;
            case Y -> sourceBounds.maxY() - sourceBounds.minY() + 1;
            case Z -> sourceBounds.maxZ() - sourceBounds.minZ() + 1;
            case NONE -> 0;
        };
        boolean yLoopFits = SREClient.areaComponent.getSceneScroll() != AreasWorldComponent.ScrollAxis.Y
                || periodSections * 2 <= client.level.getSectionsCount();
        continuousLoopAvailable = yLoopFits;
        int virtualSectionYOffset = SREClient.areaComponent.getSceneScroll() == AreasWorldComponent.ScrollAxis.Y
                ? client.level.getMinSection() + (yLoopFits ? periodSections : 0) - sourceBounds.minY()
                : 0;
        virtualBlockOffset = BASE_VIRTUAL_BLOCK_OFFSET.add(
                0.0D, SectionPos.sectionToBlockCoord(virtualSectionYOffset), 0.0D);
        int[] copies = periodSections > 0 && yLoopFits ? new int[] { -1, 0 } : new int[] { 0 };
        if (!yLoopFits) {
            showFeedback("Y 轴场景高度超过世界可容纳的双份循环高度，已避免裁切并使用单份显示");
        }
        for (SceneAsset.SectionData section : asset.sections()) {
            for (int copy : copies) {
                int sectionX = section.sectionX() + VIRTUAL_SECTION_X;
                int sectionY = section.sectionY() + virtualSectionYOffset;
                int sectionZ = section.sectionZ() + VIRTUAL_SECTION_Z;
                switch (SREClient.areaComponent.getSceneScroll()) {
                    case X -> sectionX += copy * periodSections;
                    case Y -> sectionY += copy * periodSections;
                    case Z -> sectionZ += copy * periodSections;
                    case NONE -> {
                    }
                }
                int indexInLevel = client.level.getSectionIndexFromSectionY(sectionY);
                if (indexInLevel < 0 || indexInLevel >= client.level.getSectionsCount()) {
                    continue;
                }
                ACTIVE_SECTIONS.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
                byChunk.computeIfAbsent(ChunkPos.asLong(sectionX, sectionZ), key -> new ArrayList<>())
                        .add(new VirtualSection(section, sectionX, sectionY, sectionZ));
            }
        }
        activeSectionSnapshot = ACTIVE_SECTIONS.toLongArray();

        var biomeRegistry = client.level.registryAccess().registryOrThrow(Registries.BIOME);
        var lightEngine = client.level.getChunkSource().getLightEngine();
        for (Map.Entry<Long, List<VirtualSection>> entry : byChunk.entrySet()) {
            ChunkPos pos = new ChunkPos(entry.getKey());
            LevelChunk existing = client.level.getChunkSource()
                    .getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
            boolean vanillaLoaded = existing != null
                    && !(existing instanceof EmptyLevelChunk)
                    && existing.getPos().equals(pos);

            LevelChunk chunk = new LevelChunk(client.level, pos);
            for (VirtualSection virtual : entry.getValue()) {
                SceneAsset.SectionData sectionData = virtual.data();
                int indexInChunk = client.level.getSectionIndexFromSectionY(virtual.sectionY());
                if (indexInChunk < 0 || indexInChunk >= chunk.getSections().length) {
                    continue;
                }
                LevelChunkSection section = new LevelChunkSection(biomeRegistry);
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(sectionData.sectionPayload()));
                try {
                    section.read(buffer);
                } finally {
                    buffer.release();
                }
                chunk.getSections()[indexInChunk] = section;
                SectionPos sectionPos = SectionPos.of(
                        virtual.sectionX(), virtual.sectionY(), virtual.sectionZ());
                if (sectionData.skyLight().length > 0) {
                    lightEngine.queueSectionData(LightLayer.SKY, sectionPos, new DataLayer(sectionData.skyLight()));
                }
                if (sectionData.blockLight().length > 0) {
                    lightEngine.queueSectionData(LightLayer.BLOCK, sectionPos,
                            new DataLayer(sectionData.blockLight()));
                }
                lightEngine.updateSectionStatus(sectionPos, section.hasOnlyAir());
            }
            chunk.setLoaded(true);
            CHUNKS.put(entry.getKey(), chunk);
            if (!vanillaLoaded) {
                lightEngine.retainData(pos, true);
                lightEngine.setLightEnabled(pos, true);
                if (isFormalSceneVisible()) {
                    client.level.onChunkLoaded(pos);
                    NOTIFIED_CHUNKS.add(entry.getKey());
                }
            }
        }
        lightEngine.runLightUpdates();
        currentAsset = asset;
        currentHash = hash;
        SRE.LOGGER.info(
                "Installed scene asset {} with {} source sections, {} virtual sections and {} virtual chunks (running={})",
                hash.substring(0, Math.min(12, hash.length())),
                asset.sections().size(),
                ACTIVE_SECTIONS.size(),
                CHUNKS.size(),
                sceneWasRunning);
        if (!SceneRegistryFingerprint.isCompatible(asset.registryFingerprint(), localRegistryAccess())
                && !hash.equals(lastEmbeddedCompatibilityWarningHash)) {
            lastEmbeddedCompatibilityWarningHash = hash;
            showFeedback("场景资产内部注册表指纹较旧，但当前服务器与客户端注册表一致，已按兼容模式加载");
        }
        if (SceneRegistryFingerprint.isLegacy(asset.registryFingerprint())
                && !hash.equals(lastLegacyWarningHash)) {
            lastLegacyWarningHash = hash;
            showFeedback("当前场景使用旧版注册表指纹，已按兼容模式加载；管理员重新发布后可升级为稳定校验");
        }
        previewEnabled = keepPreview;
        rebuildPreviewBlocks(asset.sourceArea());
        if (manifest == null || !manifest.transientAsset()) {
            touch(hash, fileSize(cachePath(hash)));
        }
        ClientPlayNetworking.send(new SceneAssetNetwork.ReadyC2S(hash));
        if (!manifest.stale()) {
            lastStaleWarningMap = "";
        } else if (!manifest.transientAsset()
                && !manifest.mapName().equals(lastStaleWarningMap)) {
            lastStaleWarningMap = manifest.mapName();
            showFeedback("当前场景资产已过期，管理员应重新发布");
        }
    }

    private static void rebuildPreviewBlocks(AABB sourceArea) {
        PREVIEW_BLOCKS.clear();
        AABB expanded = SceneGeometry.expandedArea(sourceArea);
        int minX = (int) Math.floor(expanded.minX);
        int minY = (int) Math.floor(expanded.minY);
        int minZ = (int) Math.floor(expanded.minZ);
        int maxX = (int) Math.ceil(expanded.maxX);
        int maxY = (int) Math.ceil(expanded.maxY);
        int maxZ = (int) Math.ceil(expanded.maxZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX && PREVIEW_BLOCKS.size() < MAX_PREVIEW_BLOCKS; x++) {
            for (int z = minZ; z < maxZ && PREVIEW_BLOCKS.size() < MAX_PREVIEW_BLOCKS; z++) {
                LevelChunk chunk = CHUNKS.get(ChunkPos.asLong(
                        SectionPos.blockToSectionCoord(x) + VIRTUAL_SECTION_X,
                        SectionPos.blockToSectionCoord(z) + VIRTUAL_SECTION_Z));
                if (chunk == null) {
                    continue;
                }
                for (int y = minY; y < maxY && PREVIEW_BLOCKS.size() < MAX_PREVIEW_BLOCKS; y++) {
                    cursor.set(
                            x + (int) virtualBlockOffset.x,
                            y + (int) virtualBlockOffset.y,
                            z + (int) virtualBlockOffset.z);
                    var state = chunk.getBlockState(cursor);
                    if (!state.isAir()) {
                        PREVIEW_BLOCKS.add(new PreviewBlock(new BlockPos(x, y, z), state));
                    }
                }
            }
        }
        ScenePreviewRenderer.rebuild(PREVIEW_BLOCKS, expanded);
    }

    public record PreviewBlock(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
    }

    private record VirtualSection(SceneAsset.SectionData data, int sectionX, int sectionY, int sectionZ) {
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("sreclient:scene")
                        .then(literal("enable").executes(context -> {
                            setMovingSceneEnabled(true);
                            context.getSource().sendFeedback(Component.literal("已启用客户端移动场景"));
                            return 1;
                        }))
                        .then(literal("disable").executes(context -> {
                            setMovingSceneEnabled(false);
                            context.getSource().sendFeedback(Component.literal("已关闭客户端移动场景"));
                            return 1;
                        }))
                        .then(literal("cache")
                                .then(literal("status").executes(context -> {
                                    CacheStatus status = cacheStatus();
                                    context.getSource().sendFeedback(Component.literal(String.format(
                                            "场景缓存: %d 个, %.1f MiB / %.1f MiB, 当前=%s, 下载=%s",
                                            status.entries(),
                                            status.bytes() / 1048576.0D,
                                            status.limitBytes() / 1048576.0D,
                                            shortHash(status.currentHash()),
                                            shortHash(status.downloadingHash()))));
                                    return 1;
                                }))
                                .then(literal("list").executes(context -> {
                                    index.entries.entrySet().stream()
                                            .sorted(Comparator.comparingLong(
                                                    entry -> -entry.getValue().lastAccess))
                                            .limit(20)
                                            .forEach(entry -> context.getSource().sendFeedback(Component.literal(
                                                    shortHash(entry.getKey()) + " "
                                                            + String.format("%.1f MiB", entry.getValue().size / 1048576.0D)
                                                            + (entry.getValue().pinned ? " [固定]" : ""))));
                                    return 1;
                                }))
                                .then(literal("verify")
                                        .then(argument("asset", StringArgumentType.word()).executes(context -> {
                                            String hash = resolveHash(StringArgumentType.getString(context, "asset"));
                                            boolean valid = SceneAssetCodec.isValidHash(hash)
                                                    && verifyFile(cachePath(hash), hash);
                                            context.getSource().sendFeedback(
                                                    Component.literal(valid ? "场景资产校验通过" : "场景资产校验失败"));
                                            return valid ? 1 : 0;
                                        })))
                                .then(literal("save-current")
                                        .executes(context -> exportAsset(currentHash, currentHash + ".sresc",
                                                context.getSource()))
                                        .then(argument("name", StringArgumentType.word()).executes(context ->
                                                exportAsset(currentHash,
                                                        StringArgumentType.getString(context, "name") + ".sresc",
                                                        context.getSource()))))
                                .then(literal("import")
                                        .then(argument("filename", StringArgumentType.word()).executes(context ->
                                                importAsset(StringArgumentType.getString(context, "filename"),
                                                        context.getSource()))))
                                .then(literal("export")
                                        .then(argument("asset", StringArgumentType.word())
                                                .then(argument("filename", StringArgumentType.word()).executes(context ->
                                                        exportAsset(
                                                                resolveHash(StringArgumentType.getString(context, "asset")),
                                                                StringArgumentType.getString(context, "filename"),
                                                                context.getSource())))))
                                .then(literal("pin")
                                        .then(argument("asset", StringArgumentType.word()).executes(context ->
                                                setPinned(resolveHash(StringArgumentType.getString(context, "asset")),
                                                        true, context.getSource()))))
                                .then(literal("unpin")
                                        .then(argument("asset", StringArgumentType.word()).executes(context ->
                                                setPinned(resolveHash(StringArgumentType.getString(context, "asset")),
                                                        false, context.getSource()))))
                                .then(literal("delete")
                                        .then(argument("asset", StringArgumentType.word()).executes(context ->
                                                deleteAsset(resolveHash(StringArgumentType.getString(context, "asset")),
                                                        context.getSource()))))
                                .then(literal("clear").executes(context -> clearCache(context.getSource())))
                                .then(literal("limit")
                                        .then(argument("mib", IntegerArgumentType.integer(64, 65536))
                                                .executes(context -> {
                                                    index.limitBytes = IntegerArgumentType.getInteger(context, "mib")
                                                            * 1024L * 1024L;
                                                    saveIndex();
                                                    enforceLimit();
                                                    context.getSource().sendFeedback(Component.literal("缓存上限已更新"));
                                                    return 1;
                                                }))))));
    }

    private static int importAsset(String filename,
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        Path path = safeChild(IMPORTS, filename);
        if (path == null || !Files.isRegularFile(path)) {
            source.sendError(Component.literal("导入文件不存在或文件名不安全"));
            return 0;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            SceneAsset asset = SceneAssetCodec.decode(bytes);
            if (!SceneRegistryFingerprint.isCompatible(asset.registryFingerprint(), localRegistryAccess())) {
                source.sendError(Component.literal("导入资产与当前注册表不兼容"));
                return 0;
            }
            String hash = SceneAssetCodec.sha256(bytes);
            Files.copy(path, cachePath(hash), StandardCopyOption.REPLACE_EXISTING);
            touch(hash, bytes.length);
            enforceLimit();
            source.sendFeedback(Component.literal("已导入场景资产 " + hash));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.literal("导入失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int exportAsset(String hash, String filename,
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        Path target = safeChild(EXPORTS, filename.endsWith(".sresc") ? filename : filename + ".sresc");
        if (!SceneAssetCodec.isValidHash(hash) || target == null || !Files.isRegularFile(cachePath(hash))) {
            source.sendError(Component.literal("资产不存在或文件名不安全"));
            return 0;
        }
        try {
            Files.copy(cachePath(hash), target, StandardCopyOption.REPLACE_EXISTING);
            source.sendFeedback(Component.literal("已导出到 " + target));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.literal("导出失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int setPinned(String hash, boolean pinned,
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        CacheEntry entry = index.entries.get(hash);
        if (entry == null) {
            source.sendError(Component.literal("缓存中没有该资产"));
            return 0;
        }
        entry.pinned = pinned;
        saveIndex();
        source.sendFeedback(Component.literal(pinned ? "已固定场景资产" : "已取消固定"));
        return 1;
    }

    private static int deleteAsset(String hash,
            net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        if (hash.equals(currentHash)) {
            source.sendError(Component.literal("不能删除当前正在使用的资产"));
            return 0;
        }
        try {
            Files.deleteIfExists(cachePath(hash));
            Files.deleteIfExists(partPath(hash));
            Files.deleteIfExists(remotePartPath(hash));
            index.entries.remove(hash);
            saveIndex();
            source.sendFeedback(Component.literal("已删除场景资产"));
            return 1;
        } catch (IOException e) {
            source.sendError(Component.literal("删除失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int clearCache(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        List<String> hashes = new ArrayList<>(index.entries.keySet());
        int removed = 0;
        for (String hash : hashes) {
            CacheEntry entry = index.entries.get(hash);
            if (hash.equals(currentHash) || entry == null || entry.pinned) {
                continue;
            }
            try {
                if (Files.deleteIfExists(cachePath(hash))) {
                    removed++;
                }
                Files.deleteIfExists(partPath(hash));
                Files.deleteIfExists(remotePartPath(hash));
                index.entries.remove(hash);
            } catch (IOException ignored) {
            }
        }
        saveIndex();
        source.sendFeedback(Component.literal("已清理 " + removed + " 个场景资产"));
        return removed;
    }

    private static void loadIndex() throws IOException {
        if (Files.isRegularFile(INDEX_FILE)) {
            CacheIndex loaded = GSON.fromJson(Files.readString(INDEX_FILE), CacheIndex.class);
            if (loaded != null) {
                index = loaded;
            }
        }
        if (index.entries == null) {
            index.entries = new HashMap<>();
        }
        if (index.limitBytes <= 0L) {
            index.limitBytes = DEFAULT_LIMIT;
        }
        if (index.singleLimitBytes <= 0L) {
            index.singleLimitBytes = DEFAULT_SINGLE_LIMIT;
        }
    }

    private static void saveIndex() {
        try {
            Files.createDirectories(ROOT);
            Path temp = INDEX_FILE.resolveSibling(INDEX_FILE.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(index));
            atomicMove(temp, INDEX_FILE);
        } catch (IOException e) {
            SRE.LOGGER.warn("Unable to save scene cache index", e);
        }
    }

    private static void touch(String hash, long size) {
        CacheEntry entry = index.entries.computeIfAbsent(hash, key -> new CacheEntry());
        entry.size = size;
        entry.lastAccess = System.currentTimeMillis();
        saveIndex();
    }

    private static void enforceLimit() {
        long total = index.entries.values().stream().mapToLong(entry -> entry.size).sum();
        if (total <= index.limitBytes) {
            return;
        }
        List<Map.Entry<String, CacheEntry>> candidates = index.entries.entrySet().stream()
                .filter(entry -> !entry.getValue().pinned && !entry.getKey().equals(currentHash))
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccess))
                .toList();
        for (Map.Entry<String, CacheEntry> entry : candidates) {
            if (total <= index.limitBytes) {
                break;
            }
            try {
                Files.deleteIfExists(cachePath(entry.getKey()));
                total -= entry.getValue().size;
                index.entries.remove(entry.getKey());
            } catch (IOException ignored) {
            }
        }
        saveIndex();
    }

    private static boolean verifyFile(Path path, String hash) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > index.singleLimitBytes) {
                return false;
            }
            byte[] bytes = Files.readAllBytes(path);
            if (!SceneAssetCodec.sha256(bytes).equals(hash)) {
                return false;
            }
            SceneAsset asset = SceneAssetCodec.decode(bytes);
            return asset.minecraftVersion().equals(SharedConstants.getCurrentVersion().getName())
                    && SceneRegistryFingerprint.isCompatible(
                            asset.registryFingerprint(), localRegistryAccess());
        } catch (IOException e) {
            quarantine(path, hash + "-corrupt");
            return false;
        }
    }

    private static boolean verifyTransportFile(Path path, String hash, long expectedSize) {
        try {
            if (!Files.isRegularFile(path)
                    || Files.size(path) != expectedSize
                    || expectedSize < 0L
                    || expectedSize > index.singleLimitBytes) {
                return false;
            }
            return SceneAssetCodec.sha256(Files.readAllBytes(path)).equals(hash);
        } catch (IOException e) {
            SRE.LOGGER.warn("Unable to verify scene asset transport file {}", path, e);
            return false;
        }
    }

    private static void handleDownloadFailure(String hash, String reason) {
        if (!hash.equals(failedDownloadHash)) {
            failedDownloadHash = hash;
            failedDownloadAttempts = 0;
        }
        failedDownloadAttempts++;
        if (failedDownloadAttempts <= 2 && manifest != null && manifest.hash().equals(hash)) {
            showError(reason + "，正在重试（" + failedDownloadAttempts + "/2）");
            beginDownload(manifest);
            return;
        }
        downloadingHash = "";
        downloadSize = 0L;
        showError(reason + "，已停止自动重试。请让管理员重新发布场景资产");
    }

    private static void resetDownloadFailures() {
        failedDownloadHash = "";
        failedDownloadAttempts = 0;
    }

    private static RegistryAccess localRegistryAccess() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            return client.level.registryAccess();
        }
        if (client.getConnection() != null) {
            return client.getConnection().registryAccess();
        }
        return null;
    }

    private static boolean isScenePlacementSafe() {
        AreasWorldComponent areas = SREClient.areaComponent;
        return areas != null && areas.isSceneAreaConfigured();
    }

    private static boolean isFormalSceneVisible() {
        return isMovingSceneEnabled() && sceneWasRunning && isScenePlacementSafe();
    }

    private static void updateSceneLifecycle() {
        boolean running = isMovingSceneEnabled()
                && SREClient.gameComponent != null
                && SREClient.gameComponent.isRunning();
        boolean changed = running != sceneWasRunning;
        if (running && !sceneWasRunning) {
            sceneMotionStartTime = SREClient.trainComponent == null
                    ? 0.0D
                    : SREClient.trainComponent.getTime();
        } else if (!running) {
            sceneMotionStartTime = 0.0D;
        }
        sceneWasRunning = running;
        if (changed) {
            Minecraft client = Minecraft.getInstance();
            if (running) {
                refreshSceneChunkMeshes();
            } else {
                unregisterSceneChunkMeshes();
                if (client.levelRenderer != null) {
                    client.levelRenderer.allChanged();
                }
            }
        }
    }

    private static void refreshSceneChunkMeshes() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        var lightEngine = client.level.getChunkSource().getLightEngine();
        for (long packed : CHUNKS.keySet()) {
            ChunkPos pos = new ChunkPos(packed);
            LevelChunk chunk = CHUNKS.get(packed);
            if (chunk == null) {
                continue;
            }
            if (NOTIFIED_CHUNKS.remove(packed)) {
                client.level.unload(chunk);
            }
            lightEngine.retainData(pos, true);
            lightEngine.setLightEnabled(pos, true);
            client.level.onChunkLoaded(pos);
            NOTIFIED_CHUNKS.add(packed);
        }
        lightEngine.runLightUpdates();
        SRE.LOGGER.info("Registered {} virtual scene chunks for rendering", NOTIFIED_CHUNKS.size());
    }

    private static void unregisterSceneChunkMeshes() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            NOTIFIED_CHUNKS.clear();
            return;
        }
        for (long packed : NOTIFIED_CHUNKS) {
            LevelChunk chunk = CHUNKS.get(packed);
            if (chunk != null) {
                client.level.unload(chunk);
            }
        }
        NOTIFIED_CHUNKS.clear();
    }

    private static String shortFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return "-";
        }
        int separator = fingerprint.lastIndexOf(':');
        String value = separator >= 0 ? fingerprint.substring(separator + 1) : fingerprint;
        return value.substring(0, Math.min(12, value.length()));
    }

    private static String resolveHash(String value) {
        return "current".equalsIgnoreCase(value) ? currentHash : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static Path safeChild(Path parent, String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        Path name = Path.of(filename).getFileName();
        if (!name.toString().equals(filename) || filename.contains("..")) {
            return null;
        }
        return parent.resolve(name);
    }

    private static Path cachePath(String hash) {
        return CACHE.resolve(hash + ".sresc");
    }

    private static Path partPath(String hash) {
        return CACHE.resolve(hash + ".part");
    }

    private static Path remotePartPath(String hash) {
        return CACHE.resolve(hash + ".remote.part");
    }

    private static long fileSize(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static void quarantine(Path path, String name) {
        try {
            if (Files.isRegularFile(path)) {
                Files.createDirectories(QUARANTINE);
                atomicMove(path, QUARANTINE.resolve(name + "-" + System.currentTimeMillis() + ".sresc"));
            }
        } catch (IOException ignored) {
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void showFeedback(String text) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(text), false);
        }
    }

    private static void showError(String text) {
        showFeedback("场景: " + text);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String shortHash(String hash) {
        return hash == null || hash.isBlank() ? "-" : hash.substring(0, Math.min(12, hash.length()));
    }

    private static final class CacheIndex {
        private long limitBytes = DEFAULT_LIMIT;
        private long singleLimitBytes = DEFAULT_SINGLE_LIMIT;
        private Map<String, CacheEntry> entries = new HashMap<>();
    }

    private static final class CacheEntry {
        private long size;
        private long lastAccess;
        private boolean pinned;
    }

    public record CacheStatus(int entries, long bytes, long limitBytes, String downloadingHash, String currentHash) {
    }
}
