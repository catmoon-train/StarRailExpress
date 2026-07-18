package org.agmas.noellesroles.client.map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.screen.StarMapScreen;
import org.agmas.noellesroles.content.item.StarMapItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星级地图数据管理器（纯客户端）。
 *
 * <p>在现有 {@link AreaMapManager} 的地形扫描之上叠加：
 * <ul>
 *   <li><b>迷雾探索</b>——以区块为单位追踪已探索区域，未探索区域显示为深色迷雾；</li>
 *   <li><b>星级区域</b>——维护星级区域列表，在地图上绘制星级边框与标签；</li>
 *   <li><b>探索缓存</b>——退出时自动保存已探索区块到 JSON 文件，下次进入恢复。</li>
 * </ul>
 *
 * <p>地形数据复用 {@code AreaMapManager} 的扫描纹理，本类只负责迷雾层
 * 的生成、星级区域管理和探索持久化。
 */
public final class StarMapManager {

    // ── 常量 ──────────────────────────────────────────────────────
    /** 迷雾纹理注册名。 */
    public static final String FOG_TEX_KEY = "star_map/fog";
    /** 迷雾像素：未探索（不透明深色）。 */
    public static final int FOG_PIXEL = 0xFF0A1220;
    /** 迷雾像素：已探索（完全透明）。 */
    public static final int CLEAR_PIXEL = 0x00000000;
    /** 星级区域边框线宽（纹理格）。 */
    private static final int BORDER_CELL_WIDTH = 2;
    /** 区块边长（方块）。 */
    private static final int CHUNK_SIZE = 16;
    /** 自动保存间隔 ms。 */
    private static final long AUTO_SAVE_INTERVAL_MS = 10_000;

    private static final Gson GSON = new Gson();
    private static final ResourceLocation FOG_TEX = Noellesroles.id(FOG_TEX_KEY);

    // ── 探索状态 ──────────────────────────────────────────────────
    /** 已探索区块键集合（线程安全）。键 = {@code (long)chunkX << 32 | (chunkZ & 0xFFFFFFFFL)}。 */
    private static final Set<Long> exploredChunks = ConcurrentHashMap.newKeySet();
    /** 当前跟踪的区块坐标（避免重复标记）。 */
    private static int lastChunkX = Integer.MAX_VALUE, lastChunkZ = Integer.MAX_VALUE;

    // ── 星级区域 ──────────────────────────────────────────────────
    /** 当前地图的星级区域列表。 */
    private static final List<StarRegion> starRegions = Collections.synchronizedList(new ArrayList<>());

    // ── 迷雾纹理 ──────────────────────────────────────────────────
    private static DynamicTexture fogTexture;
    private static boolean fogTextureDirty = false;
    private static long lastFogUploadMs = 0;
    private static long lastAutoSaveMs = 0;

    // ── 坐标映射（与 AreaMapManager 对齐） ────────────────────────
    private static int originX, originZ, sizeX, sizeZ, step;

    // ── 持久化路径 ────────────────────────────────────────────────
    private static Path savePath = null;

    // ── 家居位置 ──────────────────────────────────────────────────
    /** 家居位置（世界坐标），由服务端同步或客户端在登岛时设置。 */
    public static BlockPos homePos = null;
    /** 是否已向服务端请求过星级区域数据（断线重连后重置，避免反复发包）。 */
    private static boolean serverDataRequested = false;

    private StarMapManager() {
    }

    // ==================== 注册 ====================

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StarMapManager::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
    }

    // ==================== 每 tick 更新 ====================

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null)
            return;

        // 仅在手持星图或打开星图界面时跟踪探索
        if (!needsData(mc))
            return;

        // 首次需要数据时向服务端请求星级区域配置（HUD 小地图也要显示星级指示）
        if (!serverDataRequested) {
            serverDataRequested = true;
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
                    .send(new net.exmo.sre.sixtyseconds.network.SixtySecondsStarMapRequestC2SPacket());
        }

        // 跟踪玩家所在区块
        int cx = mc.player.blockPosition().getX() >> 4;
        int cz = mc.player.blockPosition().getZ() >> 4;
        if (cx != lastChunkX || cz != lastChunkZ) {
            lastChunkX = cx;
            lastChunkZ = cz;
            markChunkExplored(cx, cz);
        }

        // 定时更新迷雾纹理
        updateFogTexture(mc);

        // 定时自动保存
        long now = System.currentTimeMillis();
        if (now - lastAutoSaveMs > AUTO_SAVE_INTERVAL_MS) {
            lastAutoSaveMs = now;
            saveExploredChunks();
        }
    }

    private static boolean needsData(Minecraft mc) {
        return mc.screen instanceof StarMapScreen || isHoldingStarMap(mc.player);
    }

    // ==================== 探索查询 ====================

    /** 玩家是否正手持星图物品。 */
    public static boolean isHoldingStarMap(Player player) {
        return player.getMainHandItem().getItem() instanceof StarMapItem
                || player.getOffhandItem().getItem() instanceof StarMapItem;
    }

    /** 区块是否已探索。 */
    public static boolean isChunkExplored(int chunkX, int chunkZ) {
        return exploredChunks.contains(packChunk(chunkX, chunkZ));
    }

    /** 世界坐标所在的列（对应地图纹理格子）是否已探索。 */
    public static boolean isWorldColumnExplored(int worldX, int worldZ) {
        return isChunkExplored(worldX >> 4, worldZ >> 4);
    }

    /** 标记一个区块为已探索。 */
    public static void markChunkExplored(int chunkX, int chunkZ) {
        if (exploredChunks.add(packChunk(chunkX, chunkZ))) {
            fogTextureDirty = true;
        }
    }

    /** 是否还有未探索的纹理列（首遍扫完后用）。 */
    public static boolean hasUnexploredColumns() {
        return !exploredChunks.isEmpty(); // 任何时候都有迷雾的概念
    }

    /** 已探索区块数。 */
    public static int exploredChunkCount() {
        return exploredChunks.size();
    }

    // ==================== 星级区域 ====================

    /** 设置当前地图的星级区域列表（通常由服务端同步或从地图配置加载）。 */
    public static void setStarRegions(List<StarRegion> regions) {
        starRegions.clear();
        if (regions != null) {
            starRegions.addAll(regions);
        }
    }

    /** 获取当前所有星级区域。 */
    public static List<StarRegion> getStarRegions() {
        return List.copyOf(starRegions);
    }

    /** 获取世界坐标所处位置的星级区域（取最高星级）。 */
    public static StarRegion getRegionAt(double worldX, double worldZ) {
        StarRegion best = null;
        for (StarRegion r : starRegions) {
            if (r.bounds.contains(worldX, 0, worldZ)) {
                if (best == null || r.star > best.star) {
                    best = r;
                }
            }
        }
        return best;
    }

    // ==================== 迷雾纹理 ====================

    /** 获取迷雾纹理的 ResourceLocation。 */
    public static ResourceLocation getFogTexture() {
        return FOG_TEX;
    }

    /** 刷新迷雾纹理尺寸以匹配 AreaMapManager。 */
    public static void syncDimensions(int ox, int oz, int sx, int sz, int s) {
        if (ox == originX && oz == originZ && sx == sizeX && sz == sizeZ && s == step
                && fogTexture != null)
            return;
        originX = ox;
        originZ = oz;
        sizeX = sx;
        sizeZ = sz;
        step = s;
        rebuildFogTexture();
    }

    /** 检查迷雾纹理是否可用。 */
    public static boolean hasFogTexture() {
        return fogTexture != null;
    }

    private static void rebuildFogTexture() {
        Minecraft mc = Minecraft.getInstance();
        if (fogTexture != null) {
            mc.getTextureManager().release(FOG_TEX);
        }
        fogTexture = new DynamicTexture(Math.max(1, sizeX), Math.max(1, sizeZ), true);
        mc.getTextureManager().register(FOG_TEX, fogTexture);
        fogTextureDirty = true;
    }

    private static void updateFogTexture(Minecraft mc) {
        if (fogTexture == null || !fogTextureDirty)
            return;

        long now = System.currentTimeMillis();
        if (now - lastFogUploadMs < 500)
            return; // 限流
        lastFogUploadMs = now;

        NativeImage img = fogTexture.getPixels();
        if (img == null)
            return;

        int w = img.getWidth();
        int h = img.getHeight();
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                int wx = originX + i * step;
                int wz = originZ + j * step;
                boolean explored = isWorldColumnExplored(wx, wz);
                img.setPixelRGBA(i, j, explored ? CLEAR_PIXEL : FOG_PIXEL);
            }
        }
        fogTexture.upload();
        fogTextureDirty = false;
    }

    /** 标记迷雾纹理需要重绘。 */
    public static void markFogDirty() {
        fogTextureDirty = true;
    }

    // ==================== 持久化 ====================

    /** 获取保存路径（每个世界/地图独立）。 */
    private static Path getSavePath() {
        if (savePath != null)
            return savePath;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return null;
        // 保存在世界目录下的 star_map_explored.json
        String worldKey = mc.getCurrentServer() != null
                ? mc.getCurrentServer().ip.replace(':', '_')
                : "singleplayer";
        Path gameDir = mc.gameDirectory.toPath();
        savePath = gameDir.resolve("star_map_explored_" + worldKey + ".json");
        return savePath;
    }

    /** 从文件加载已探索区块。 */
    public static void loadExploredChunks() {
        Path path = getSavePath();
        if (path == null || !Files.exists(path))
            return;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            List<long[]> packed = GSON.fromJson(json,
                    new TypeToken<List<long[]>>() {
                    }.getType());
            exploredChunks.clear();
            if (packed != null) {
                for (long[] arr : packed) {
                    for (long key : arr) {
                        exploredChunks.add(key);
                    }
                }
            }
            if (!exploredChunks.isEmpty()) {
                fogTextureDirty = true;
            }
        } catch (IOException e) {
            Noellesroles.LOGGER.warn("[StarMap] Failed to load explored chunks", e);
        }
    }

    /** 保存已探索区块到文件。 */
    public static void saveExploredChunks() {
        if (exploredChunks.isEmpty())
            return;
        Path path = getSavePath();
        if (path == null)
            return;
        try {
            long[] arr = new long[exploredChunks.size()];
            int idx = 0;
            for (long key : exploredChunks) {
                arr[idx++] = key;
            }
            String json = GSON.toJson(new long[][] { arr });
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Noellesroles.LOGGER.warn("[StarMap] Failed to save explored chunks", e);
        }
    }

    // ==================== 连接生命周期 ====================

    private static void onDisconnect() {
        saveExploredChunks();
        reset();
    }

    /** 重置所有状态（清空迷雾和区域，但不删文件）。 */
    public static void reset() {
        exploredChunks.clear();
        starRegions.clear();
        lastChunkX = Integer.MAX_VALUE;
        lastChunkZ = Integer.MAX_VALUE;
        homePos = null;
        serverDataRequested = false;
        savePath = null;
        if (fogTexture != null) {
            Minecraft.getInstance().getTextureManager().release(FOG_TEX);
            fogTexture = null;
        }
        fogTextureDirty = false;
    }

    // ==================== 工具 ====================

    private static long packChunk(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }
}
