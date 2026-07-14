package org.agmas.noellesroles.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.screen.AreaMapScreen;
import org.agmas.noellesroles.content.item.AreaMapItem;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 区域地图数据管理器（纯客户端）。
 *
 * <p>按需（手持地图物品或打开地图界面时）以 {@code AreasWorldComponent.playArea}
 * 为边界，在客户端本地逐列扫描方块生成俯视地图纹理，无任何网络同步。
 * 扫描以玩家所在高度为切片：切片处有碰撞体的列视为「墙」，否则向下找地板取
 * {@link MapColor}。每 tick 限量扫描避免卡顿，扫完一遍后循环重扫保持地图鲜活
 * （门开关、方块变化会逐步反映）。
 *
 * <p>产出 1 张底图纹理 + {@value #WALL_LAYERS} 张墙体层纹理（3D 视图逐层抬升
 * 绘制形成体素效果）。纹理尺寸 = 区域尺寸/step，超过上限时自动增大采样步长。
 */
public final class AreaMapManager {

    /** 3D 视图的墙体层数（也是墙高采样上限，单位：方块）。 */
    public static final int WALL_LAYERS = 4;
    private static final int MAX_CELLS = 512 * 512;
    private static final int SCAN_BUDGET_PER_TICK = 800;
    private static final long UPLOAD_INTERVAL_MS = 500;

    private static final ResourceLocation BASE_TEX = Noellesroles.id("area_map/base");
    private static final ResourceLocation[] WALL_TEX = new ResourceLocation[WALL_LAYERS];

    static {
        for (int i = 0; i < WALL_LAYERS; i++) {
            WALL_TEX[i] = Noellesroles.id("area_map/wall_" + i);
        }
    }

    // ==================== HUD 与全屏界面共享的显示状态 ====================
    /** 是否使用 3D 视图（全屏界面右侧切换，默认 2D）。 */
    public static boolean mode3d = false;
    /** 当前勾选显示的任务点分类。 */
    public static final EnumSet<AreaMapPointCategory> visibleCategories =
            EnumSet.allOf(AreaMapPointCategory.class);

    // ==================== 扫描状态 ====================
    private static DynamicTexture baseTexture;
    private static final DynamicTexture[] wallTextures = new DynamicTexture[WALL_LAYERS];
    private static boolean texturesRegistered = false;
    private static boolean dirty = false;
    private static long lastUploadMs = 0;

    private static AABB scannedArea = null;
    private static String scannedMap = null;
    private static int originX, originZ, minY, maxY;
    private static int sizeX, sizeZ, step = 1;
    private static int totalCells = 0;
    private static int cursor = 0;
    private static int sliceY = Integer.MIN_VALUE;
    private static boolean firstPassDone = false;
    private static boolean anyColumnScanned = false;

    /** 区域记忆：节省过的 zone 纹理状态，切回老区域时复用（不用重新扫描）。 */
    private static final Map<String, ZoneState> ZONE_CACHE = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ZoneState> eldest) {
            return size() > 16; // 最多缓存 16 个 zone
        }
    };

    private record ZoneState(DynamicTexture baseTexture, DynamicTexture[] wallTextures,
            boolean anyColumnScanned, boolean firstPassDone, int cursor, int sliceY) {
    }

    private AreaMapManager() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AreaMapManager::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    // ==================== 查询 ====================

    public static boolean isHoldingMap(Player player) {
        return player.getMainHandItem().getItem() instanceof AreaMapItem
                || player.getOffhandItem().getItem() instanceof AreaMapItem;
    }

    /** 是否已有可绘制的数据（至少扫描过一列）。 */
    public static boolean hasData() {
        return baseTexture != null && anyColumnScanned;
    }

    public static boolean isFirstPassDone() {
        return firstPassDone;
    }

    /** 首遍扫描进度 0~1。 */
    public static float scanProgress() {
        if (firstPassDone) return 1f;
        return totalCells <= 0 ? 0f : (float) cursor / totalCells;
    }

    public static int getSizeX() {
        return sizeX;
    }

    public static int getSizeZ() {
        return sizeZ;
    }

    public static int getStep() {
        return step;
    }

    /** 世界 X 坐标 → 纹理格坐标（浮点）。 */
    public static double worldToCellX(double worldX) {
        return (worldX - originX) / step;
    }

    /** 世界 Z 坐标 → 纹理格坐标（浮点）。 */
    public static double worldToCellZ(double worldZ) {
        return (worldZ - originZ) / step;
    }

    /** 纹理格坐标 → 世界 X 坐标（{@link #worldToCellX} 的逆变换，自定义标注用）。 */
    public static double cellToWorldX(double cellX) {
        return originX + cellX * step;
    }

    /** 纹理格坐标 → 世界 Z 坐标。 */
    public static double cellToWorldZ(double cellZ) {
        return originZ + cellZ * step;
    }

    /** 底图纹理（自动按需上传显存）。 */
    public static ResourceLocation getBaseTexture() {
        uploadIfDirty();
        return BASE_TEX;
    }

    /** 第 layer 层墙体纹理（0 起，自动按需上传显存）。 */
    public static ResourceLocation getWallTexture(int layer) {
        uploadIfDirty();
        return WALL_TEX[layer];
    }

    // ==================== 扫描 ====================

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (scannedArea != null) reset();
            return;
        }
        if (!needsData(mc)) return;
        var areas = SREClient.areaComponent;
        if (areas == null) return;
        AABB area = areas.getPlayArea();
        String mapKey = areas.mapName;
        // 末日60秒模式：改扫服务端推送的当前区域（住宅/避难所/探索区），而非全图 playArea
        AABB zone = net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.activeZone();
        if (zone != null) {
            area = zone;
            mapKey = "60s:" + (int) zone.minX + "," + (int) zone.minZ + "," + (int) zone.maxX + "," + (int) zone.maxZ;
        }
        if (area == null || area.getXsize() < 2 || area.getZsize() < 2) return;

        if (!area.equals(scannedArea) || !Objects.equals(mapKey, scannedMap)) {
            // 先存当前 zone 的扫描进度（未完成时下次回来可以续扫）
            if (scannedArea != null && scannedMap != null && anyColumnScanned) {
                saveCurrentZone();
            }
            // 再切到目标 zone：有缓存则恢复，无则新扫
            if (!restoreZone(mc, area, mapKey)) {
                reinit(mc, area, mapKey);
            }
        }
        if (baseTexture == null) return;

        int slice = Mth.clamp(mc.player.blockPosition().getY(), minY + 1, Math.max(minY + 1, maxY - 1));
        if (sliceY == Integer.MIN_VALUE || Math.abs(slice - sliceY) >= 2) {
            sliceY = slice;
            cursor = 0;
            firstPassDone = false;
        }
        scanSome(mc.level, SCAN_BUDGET_PER_TICK);
    }

    private static boolean needsData(Minecraft mc) {
        return mc.screen instanceof AreaMapScreen || isHoldingMap(mc.player);
    }

    private static void reinit(Minecraft mc, AABB area, String mapName) {
        releaseTextures(mc);
        originX = Mth.floor(area.minX);
        originZ = Mth.floor(area.minZ);
        minY = Mth.floor(area.minY);
        maxY = Mth.ceil(area.maxY);
        int wx = Math.max(1, Mth.ceil(area.maxX) - originX);
        int wz = Math.max(1, Mth.ceil(area.maxZ) - originZ);
        step = 1;
        while ((long) Mth.positiveCeilDiv(wx, step) * Mth.positiveCeilDiv(wz, step) > MAX_CELLS) {
            step++;
        }
        sizeX = Mth.positiveCeilDiv(wx, step);
        sizeZ = Mth.positiveCeilDiv(wz, step);
        totalCells = sizeX * sizeZ;

        baseTexture = new DynamicTexture(sizeX, sizeZ, true);
        mc.getTextureManager().register(BASE_TEX, baseTexture);
        for (int i = 0; i < WALL_LAYERS; i++) {
            wallTextures[i] = new DynamicTexture(sizeX, sizeZ, true);
            mc.getTextureManager().register(WALL_TEX[i], wallTextures[i]);
        }
        texturesRegistered = true;

        scannedArea = area;
        scannedMap = mapName;
        cursor = 0;
        sliceY = Integer.MIN_VALUE;
        firstPassDone = false;
        anyColumnScanned = false;
        dirty = false;
    }

    private static void reset() {
        releaseTextures(Minecraft.getInstance());
        scannedArea = null;
        scannedMap = null;
        totalCells = 0;
        cursor = 0;
        sliceY = Integer.MIN_VALUE;
        firstPassDone = false;
        anyColumnScanned = false;
        dirty = false;
    }

    private static void releaseTextures(Minecraft mc) {
        if (!texturesRegistered) return;
        // TextureManager.release 会 close 对应纹理并释放显存
        mc.getTextureManager().release(BASE_TEX);
        for (ResourceLocation loc : WALL_TEX) {
            mc.getTextureManager().release(loc);
        }
        baseTexture = null;
        java.util.Arrays.fill(wallTextures, null);
        texturesRegistered = false;
    }

    private static void scanSome(ClientLevel level, int budget) {
        if (totalCells <= 0 || baseTexture == null) return;
        NativeImage base = baseTexture.getPixels();
        if (base == null) return;
        NativeImage[] walls = new NativeImage[WALL_LAYERS];
        for (int i = 0; i < WALL_LAYERS; i++) {
            walls[i] = wallTextures[i].getPixels();
            if (walls[i] == null) return;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int scanned = 0;
        while (scanned < budget) {
            int i = cursor % sizeX;
            int j = cursor / sizeX;
            if (scanColumn(level, i, j, pos, base, walls)) {
                anyColumnScanned = true;
                dirty = true;
            }
            scanned++;
            cursor++;
            if (cursor >= totalCells) {
                cursor = 0;
                firstPassDone = true;
                break;
            }
        }
    }

    /** 扫描一列，返回该列是否成功写入（区块未加载返回 false，稍后重试）。 */
    private static boolean scanColumn(ClientLevel level, int i, int j, BlockPos.MutableBlockPos pos,
            NativeImage base, NativeImage[] walls) {
        int wx = originX + i * step;
        int wz = originZ + j * step;
        if (!level.hasChunk(wx >> 4, wz >> 4)) return false;

        boolean solidFeet = isSolid(level, pos.set(wx, sliceY, wz));
        boolean solidHead = isSolid(level, pos.set(wx, sliceY + 1, wz));

        if (solidFeet || solidHead) {
            // 墙体：取切片处方块的地图色，逐层记录墙高
            pos.set(wx, solidFeet ? sliceY : sliceY + 1, wz);
            BlockState wallState = level.getBlockState(pos);
            MapColor color = wallState.getMapColor(level, pos);
            int baseColor = color == MapColor.NONE
                    ? 0xFF707070 // ABGR：无地图色的墙（如玻璃）用灰色
                    : color.calculateRGBColor(MapColor.Brightness.NORMAL);
            int height = 0;
            for (int k = 0; k < WALL_LAYERS; k++) {
                if (isSolid(level, pos.set(wx, sliceY + k, wz))) height++;
                else break;
            }
            height = Math.max(1, height);
            base.setPixelRGBA(i, j, scaleABGR(baseColor, 0.45f));
            for (int layer = 0; layer < WALL_LAYERS; layer++) {
                walls[layer].setPixelRGBA(i, j,
                        layer < height ? scaleABGR(baseColor, 0.62f + 0.13f * layer) : 0);
            }
        } else {
            // 地板：从切片向下找第一个有地图色的方块
            int foundY = Integer.MIN_VALUE;
            MapColor color = null;
            for (int y = sliceY - 1; y >= minY; y--) {
                pos.set(wx, y, wz);
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                MapColor c = state.getMapColor(level, pos);
                if (c != MapColor.NONE) {
                    foundY = y;
                    color = c;
                    break;
                }
            }
            int pixel = 0;
            if (foundY != Integer.MIN_VALUE) {
                int depth = sliceY - foundY;
                MapColor.Brightness brightness;
                if (depth <= 2) brightness = MapColor.Brightness.HIGH;
                else if (depth <= 6) brightness = MapColor.Brightness.NORMAL;
                else if (depth <= 14) brightness = MapColor.Brightness.LOW;
                else brightness = MapColor.Brightness.LOWEST;
                pixel = color.calculateRGBColor(brightness);
            }
            base.setPixelRGBA(i, j, pixel);
            for (int layer = 0; layer < WALL_LAYERS; layer++) {
                walls[layer].setPixelRGBA(i, j, 0);
            }
        }
        return true;
    }

    private static boolean isSolid(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    /** 缩放 ABGR 颜色的 RGB 分量（NativeImage 像素格式）。 */
    private static int scaleABGR(int abgr, float factor) {
        int a = abgr >>> 24;
        int b = Math.min(255, (int) (((abgr >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((abgr >> 8) & 0xFF) * factor));
        int r = Math.min(255, (int) ((abgr & 0xFF) * factor));
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static void uploadIfDirty() {
        if (!dirty || baseTexture == null) return;
        long now = System.currentTimeMillis();
        if (now - lastUploadMs < UPLOAD_INTERVAL_MS) return;
        lastUploadMs = now;
        dirty = false;
        baseTexture.upload();
        for (DynamicTexture tex : wallTextures) {
            if (tex != null) tex.upload();
        }
    }

    /** 保存当前 zone 的纹理和扫描进度到缓存（换 zone 前调用）。 */
    private static void saveCurrentZone() {
        if (scannedMap == null || baseTexture == null) return;
        DynamicTexture[] wallsCopy = new DynamicTexture[WALL_LAYERS];
        System.arraycopy(wallTextures, 0, wallsCopy, 0, WALL_LAYERS);
        ZONE_CACHE.put(scannedMap, new ZoneState(
                baseTexture, wallsCopy, anyColumnScanned, firstPassDone, cursor, sliceY));
    }

    /**
     * 从缓存恢复 zone。成功返回 true。
     * <p>调用前本 zone 的纹理/扫描状态已经通过 {@link #saveCurrentZone()} 存入缓存，
     * 此时可以安全地替换 {@link #baseTexture} 等引用（旧纹理由缓存持有，不会被 GC）。</p>
     */
    private static boolean restoreZone(Minecraft mc, AABB area, String mapKey) {
        ZoneState cached = ZONE_CACHE.get(mapKey);
        if (cached == null) return false;
        // 校验缓存中的纹理尺寸与新 zone 尺寸一致（地图模板没变）
        int newOriginX = Mth.floor(area.minX);
        int newOriginZ = Mth.floor(area.minZ);
        int wx = Math.max(1, Mth.ceil(area.maxX) - newOriginX);
        int wz = Math.max(1, Mth.ceil(area.maxZ) - newOriginZ);
        int s = 1;
        while ((long) Mth.positiveCeilDiv(wx, s) * Mth.positiveCeilDiv(wz, s) > MAX_CELLS) s++;
        int sx = Mth.positiveCeilDiv(wx, s);
        int sz = Mth.positiveCeilDiv(wz, s);
        NativeImage cachedPixels = cached.baseTexture.getPixels();
        if (cachedPixels == null || cachedPixels.getWidth() != sx || cachedPixels.getHeight() != sz) {
            ZONE_CACHE.remove(mapKey);
            return false; // 尺寸变了，重新扫
        }

        // 释放当前纹理（如果已经注册过），用缓存中的替换
        releaseTexturesLight(mc);
        baseTexture = cached.baseTexture;
        System.arraycopy(cached.wallTextures, 0, wallTextures, 0, WALL_LAYERS);
        // 重新注册到 TextureManager
        mc.getTextureManager().register(BASE_TEX, baseTexture);
        for (int i = 0; i < WALL_LAYERS; i++) {
            if (wallTextures[i] != null) {
                mc.getTextureManager().register(WALL_TEX[i], wallTextures[i]);
            }
        }
        texturesRegistered = true;

        originX = newOriginX;
        originZ = newOriginZ;
        minY = Mth.floor(area.minY);
        maxY = Mth.ceil(area.maxY);
        step = s;
        sizeX = sx;
        sizeZ = sz;
        totalCells = sizeX * sizeZ;

        scannedArea = area;
        scannedMap = mapKey;
        cursor = Math.min(cached.cursor, totalCells - 1);
        sliceY = cached.sliceY;
        firstPassDone = cached.firstPassDone;
        anyColumnScanned = cached.anyColumnScanned;
        dirty = true; // 提醒上传（纹理内容可能在另一帧被 GPU 回收过）
        return true;
    }

    /** 轻量释放：仅注销 TextureManager 注册，不 close 纹理（缓存持有引用）。 */
    private static void releaseTexturesLight(Minecraft mc) {
        if (!texturesRegistered) return;
        mc.getTextureManager().release(BASE_TEX);
        for (ResourceLocation loc : WALL_TEX) {
            mc.getTextureManager().release(loc);
        }
        baseTexture = null;
        java.util.Arrays.fill(wallTextures, null);
        texturesRegistered = false;
    }
}
