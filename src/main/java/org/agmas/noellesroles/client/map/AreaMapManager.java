/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.screen.AreaMapScreen;
import org.agmas.noellesroles.content.item.AreaMapItem;
import org.agmas.noellesroles.game.modes.echo.client.EchoFacilityMapClient;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;

/**
 * 区域地图的客户端切片渲染器。
 * <p>按玩家附近优先扫描 16×16 瓦片，直接读取已加载区块，跳过空区段。
 * 材质来自当前资源包顶面并叠加群系染色、透明层和柔和高差明暗。
 * 完成后只由区块到达、方块变化、楼层切换或资源重载触发增量更新；不请求区块。
 * 2D/3D 与小地图共用同一份数据，墙层显存只在 3D 视图使用时上传。
 */
public final class AreaMapManager {
    public static final int WALL_LAYERS = 4;
    private static final int MAX_CELLS = 512 * 512;
    private static final int SCAN_BUDGET_PER_TICK = 4096;
    private static final long SCREEN_BUDGET_NS = 3_000_000L;
    private static final long HUD_BUDGET_NS = 1_500_000L;
    private static final int UNKNOWN_HEIGHT = Integer.MIN_VALUE;
    private static final ResourceLocation BASE_TEX = Noellesroles.id("area_map/base");
    private static final ResourceLocation[] WALL_TEX = new ResourceLocation[WALL_LAYERS];
    private static final DynamicTexture[] textures = new DynamicTexture[WALL_LAYERS + 1];
    private static final AreaMapDirtyRect[] dirty = new AreaMapDirtyRect[WALL_LAYERS + 1];
    private static final AreaMapPalette palette = new AreaMapPalette();

    static {
        for (int i = 0; i < WALL_LAYERS; i++) WALL_TEX[i] = Noellesroles.id("area_map/wall_" + i);
        Arrays.setAll(dirty, i -> new AreaMapDirtyRect());
    }

    /** 全屏界面与小地图共用的模式和分类，不新增按键绑定。 */
    public static boolean mode3d = false;
    public static final EnumSet<AreaMapPointCategory> visibleCategories = EnumSet.allOf(AreaMapPointCategory.class);

    private static AABB scannedArea;
    private static String scannedMap;
    private static ClientLevel scannedWorld;
    private static UUID scannedMatch;
    private static int originX, originZ, minY, maxY, sizeX, sizeZ, step = 1;
    private static int sliceY = UNKNOWN_HEIGHT;
    private static int[] colors = new int[0], elevations = new int[0];
    private static byte[] wallHeights = new byte[0];
    private static final BitSet sampled = new BitSet();
    private static int sampledCount;
    private static AreaMapTileQueue queue;
    private static int activeTile = -1, tileCursor, focusTileX, focusTileZ;
    private static volatile boolean resourcesInvalidated;

    private AreaMapManager() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AreaMapManager::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> invalidateChunk(world, chunk.getPos().x, chunk.getPos().z));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override public ResourceLocation getFabricId() { return Noellesroles.id("area_map_palette"); }
            @Override public void onResourceManagerReload(ResourceManager manager) { resourcesInvalidated = true; }
        });
    }

    public static boolean isHoldingMap(Player player) {
        return player.getMainHandItem().getItem() instanceof AreaMapItem
                || player.getOffhandItem().getItem() instanceof AreaMapItem;
    }

    public static boolean hasData() { return textures[0] != null && sampledCount > 0; }
    public static boolean isFirstPassDone() { return colors.length > 0 && sampledCount == colors.length; }
    public static float scanProgress() { return colors.length == 0 ? 0 : (float) sampledCount / colors.length; }
    public static int getSizeX() { return sizeX; }
    public static int getSizeZ() { return sizeZ; }
    public static int getStep() { return step; }
    public static double worldToCellX(double worldX) { return (worldX - originX) / step; }
    public static double worldToCellZ(double worldZ) { return (worldZ - originZ) / step; }

    public static ResourceLocation getBaseTexture() {
        uploadIfDirty(0);
        return BASE_TEX;
    }

    public static ResourceLocation getWallTexture(int layer) {
        uploadIfDirty(layer + 1);
        return WALL_TEX[layer];
    }

    /** 仅让实际变化处附近的瓦片重扫；客户端与服务端线程不会共享该队列。 */
    public static void invalidateColumn(ClientLevel level, BlockPos pos) {
        if (level != scannedWorld || queue == null || pos.getY() < minY || sliceY == UNKNOWN_HEIGHT
                || pos.getY() > sliceY + WALL_LAYERS) return;
        int x = Math.floorDiv(pos.getX() - originX, step), z = Math.floorDiv(pos.getZ() - originZ, step);
        queue.markRegion(x - 1, z - 1, x + 2, z + 2);
    }

    private static void invalidateChunk(ClientLevel level, int cx, int cz) {
        if (level != scannedWorld || queue == null) return;
        int x0 = Math.floorDiv((cx << 4) - originX, step);
        int z0 = Math.floorDiv((cz << 4) - originZ, step);
        int x1 = Math.floorDiv((cx << 4) + 15 - originX, step) + 1;
        int z1 = Math.floorDiv((cz << 4) + 15 - originZ, step) + 1;
        queue.markRegion(x0 - 1, z0 - 1, x1 + 1, z1 + 1);
    }

    private static void tick(Minecraft mc) {
        if (resourcesInvalidated) {
            resourcesInvalidated = false;
            reset();
        }
        if (mc.level == null || mc.player == null) {
            if (scannedWorld != null) reset();
            return;
        }
        if (!needsData(mc)) return;
        var areas = SREClient.areaComponent;
        AABB area = areas == null ? null : areas.getPlayArea();
        if (area == null || area.getXsize() < 2 || area.getZsize() < 2) {
            if (scannedWorld != null) reset();
            return;
        }
        if (scannedWorld != mc.level || !area.equals(scannedArea) || !Objects.equals(areas.mapName, scannedMap)
                || !Objects.equals(EchoFacilityMapClient.match(), scannedMatch)) reinit(mc, area, areas.mapName);

        int slice = Mth.clamp(mc.player.blockPosition().getY(), minY + 1, Math.max(minY + 1, maxY - 2));
        int focusX = Mth.floor(worldToCellX(mc.player.getX())), focusZ = Mth.floor(worldToCellZ(mc.player.getZ()));
        if (sliceY == UNKNOWN_HEIGHT || Math.abs(slice - sliceY) >= 2) {
            sliceY = slice;
            clearSlice(focusX, focusZ);
        }
        int fx = Math.floorDiv(focusX, AreaMapTileQueue.TILE_SIZE), fz = Math.floorDiv(focusZ, AreaMapTileQueue.TILE_SIZE);
        if (fx != focusTileX || fz != focusTileZ) {
            focusTileX = fx;
            focusTileZ = fz;
            queue.prioritize(focusX, focusZ);
        }
        boolean screen = mc.screen instanceof AreaMapScreen;
        scanSome(mc, screen ? SCAN_BUDGET_PER_TICK : 1024, screen ? SCREEN_BUDGET_NS : HUD_BUDGET_NS);
    }

    private static boolean needsData(Minecraft mc) {
        return mc.screen instanceof AreaMapScreen || isHoldingMap(mc.player) || EchoFacilityMapClient.inMatch();
    }

    private static void reinit(Minecraft mc, AABB area, String mapName) {
        releaseTextures(mc);
        originX = Mth.floor(area.minX);
        originZ = Mth.floor(area.minZ);
        minY = Math.max(mc.level.getMinBuildHeight(), Mth.floor(area.minY));
        maxY = Math.min(mc.level.getMaxBuildHeight(), Mth.ceil(area.maxY));
        int wx = Math.max(1, Mth.ceil(area.maxX) - originX), wz = Math.max(1, Mth.ceil(area.maxZ) - originZ);
        step = 1;
        while ((long) Mth.positiveCeilDiv(wx, step) * Mth.positiveCeilDiv(wz, step) > MAX_CELLS) step++;
        sizeX = Mth.positiveCeilDiv(wx, step);
        sizeZ = Mth.positiveCeilDiv(wz, step);
        colors = new int[sizeX * sizeZ];
        elevations = new int[colors.length];
        wallHeights = new byte[colors.length];
        queue = new AreaMapTileQueue(sizeX, sizeZ);
        for (int i = 0; i < textures.length; i++) {
            textures[i] = new DynamicTexture(sizeX, sizeZ, true);
            mc.getTextureManager().register(i == 0 ? BASE_TEX : WALL_TEX[i - 1], textures[i]);
            // 初始化显存为透明色，局部上传前不能留下未初始化像素。
            textures[i].upload();
            dirty[i].clear();
        }
        scannedArea = area;
        scannedMap = mapName;
        scannedWorld = mc.level;
        scannedMatch = EchoFacilityMapClient.match();
        sliceY = UNKNOWN_HEIGHT;
    }

    private static void clearSlice(int focusX, int focusZ) {
        Arrays.fill(colors, 0);
        Arrays.fill(elevations, UNKNOWN_HEIGHT);
        Arrays.fill(wallHeights, (byte) 0);
        sampled.clear();
        sampledCount = 0;
        activeTile = -1;
        queue.reset(focusX, focusZ);
        focusTileX = focusTileZ = Integer.MIN_VALUE;
        for (int i = 0; i < textures.length; i++) {
            textures[i].getPixels().fillRect(0, 0, sizeX, sizeZ, 0);
            dirty[i].include(0, 0);
            dirty[i].include(sizeX - 1, sizeZ - 1);
        }
    }

    private static void reset() {
        releaseTextures(Minecraft.getInstance());
        palette.clear();
        scannedArea = null;
        scannedMap = null;
        scannedWorld = null;
        scannedMatch = null;
        colors = elevations = new int[0];
        wallHeights = new byte[0];
        sampled.clear();
        sampledCount = 0;
        sizeX = sizeZ = 0;
        sliceY = UNKNOWN_HEIGHT;
        queue = null;
        activeTile = -1;
    }

    private static void releaseTextures(Minecraft mc) {
        for (int i = 0; i < textures.length; i++) {
            if (textures[i] != null) mc.getTextureManager().release(i == 0 ? BASE_TEX : WALL_TEX[i - 1]);
            textures[i] = null;
            dirty[i].clear();
        }
    }

    private static void scanSome(Minecraft mc, int budget, long timeBudget) {
        long deadline = System.nanoTime() + timeBudget;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LevelChunk chunk = null;
        int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
        for (int scanned = 0; scanned < budget; scanned++) {
            if ((scanned & 15) == 0 && System.nanoTime() >= deadline) break;
            if (activeTile < 0) {
                activeTile = queue.next();
                tileCursor = 0;
                if (activeTile < 0) break;
            }
            int tileWidth = queue.x1(activeTile) - queue.x0(activeTile);
            int x = queue.x0(activeTile) + tileCursor % tileWidth;
            int z = queue.z0(activeTile) + tileCursor / tileWidth;
            int wx = originX + x * step, wz = originZ + z * step;
            if (chunkX != wx >> 4 || chunkZ != wz >> 4) {
                chunkX = wx >> 4;
                chunkZ = wz >> 4;
                chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            }
            if (chunk != null) scanColumn(mc, chunk, x, z, pos.set(wx, sliceY, wz));
            tileCursor++;
            if (tileCursor >= tileWidth * (queue.z1(activeTile) - queue.z0(activeTile))) activeTile = -1;
        }
    }

    private static void scanColumn(Minecraft mc, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos) {
        boolean feet = isSolid(mc.level, chunk.getBlockState(pos), pos);
        boolean head = isSolid(mc.level, chunk.getBlockState(pos.setY(sliceY + 1)), pos);
        int wallHeight = 0;
        if (feet || head) {
            for (int k = 0; k < WALL_LAYERS; k++) {
                pos.setY(sliceY + k);
                if (!isSolid(mc.level, chunk.getBlockState(pos), pos)) break;
                wallHeight++;
            }
            wallHeight = Math.max(1, wallHeight);
        }
        int startY = feet ? sliceY : head ? sliceY + 1 : sliceY - 1;
        int color = 0, elevation = minY - 1, layers = 0;
        Fluid previousFluid = null;
        for (int y = startY; y >= minY; y--) {
            int section = chunk.getSectionIndex(y);
            if (section < 0 || section >= chunk.getSections().length) continue;
            if (chunk.getSection(section).hasOnlyAir()) {
                y = (y >> 4) << 4;
                continue;
            }
            pos.setY(y);
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir()) continue;
            var fluid = state.getFluidState();
            if (!fluid.isEmpty() && state.getRenderShape() != RenderShape.MODEL) {
                Fluid type = fluid.getType();
                if (previousFluid != null && previousFluid.isSame(type)) continue;
                previousFluid = type;
            }
            int surface = palette.color(mc, mc.level, pos, state);
            if ((surface >>> 24) == 0) continue;
            if (layers++ == 0) elevation = y;
            color = AreaMapColor.over(color, surface);
            if ((color >>> 24) >= 250 || layers >= 8) break;
        }
        if (color != 0) color |= 0xFF000000;
        int index = z * sizeX + x;
        if (!sampled.get(index)) {
            sampled.set(index);
            sampledCount++;
        }
        if (colors[index] == color && elevations[index] == elevation && wallHeights[index] == wallHeight) return;
        colors[index] = color;
        elevations[index] = elevation;
        wallHeights[index] = (byte) wallHeight;
        repaint(x, z);
        repaint(x - 1, z);
        repaint(x + 1, z);
        repaint(x, z - 1);
        repaint(x, z + 1);
    }

    private static boolean isSolid(ClientLevel level, BlockState state, BlockPos pos) {
        return !state.isAir() && !state.getCollisionShape(level, pos).isEmpty();
    }

    private static int relativeHeight(int x, int z, int center) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ) return center;
        int index = z * sizeX + x;
        return colors[index] == 0 || elevations[index] == UNKNOWN_HEIGHT ? center : elevations[index];
    }

    private static void repaint(int x, int z) {
        if (x < 0 || z < 0 || x >= sizeX || z >= sizeZ) return;
        int index = z * sizeX + x;
        if (!sampled.get(index)) return;
        int elevation = elevations[index];
        float slope = (Mth.clamp(relativeHeight(x - 1, z, elevation) - relativeHeight(x + 1, z, elevation), -3, 3)
                + Mth.clamp(relativeHeight(x, z - 1, elevation) - relativeHeight(x, z + 1, elevation), -3, 3)) * 0.025F;
        float depth = Math.min(0.12F, Math.max(0, sliceY - elevation - 2) * 0.006F);
        float brightness = Mth.clamp(1F + slope - depth - (wallHeights[index] > 0 ? 0.08F : 0), 0.78F, 1.12F);
        writePixel(0, x, z, AreaMapColor.shade(colors[index], brightness));
        for (int layer = 0; layer < WALL_LAYERS; layer++) {
            int pixel = layer < wallHeights[index] ? AreaMapColor.shade(colors[index], 0.84F + 0.05F * layer) : 0;
            writePixel(layer + 1, x, z, pixel);
        }
    }

    private static void writePixel(int layer, int x, int z, int color) {
        NativeImage pixels = textures[layer].getPixels();
        if (pixels.getPixelRGBA(x, z) == color) return;
        pixels.setPixelRGBA(x, z, color);
        dirty[layer].include(x, z);
    }

    /** 直接上传脏矩形，2D 不触碰四张墙层；没有变化时不绑定、不上传。 */
    private static void uploadIfDirty(int layer) {
        if (textures[layer] == null || dirty[layer].isEmpty()) return;
        AreaMapDirtyRect rect = dirty[layer];
        textures[layer].bind();
        textures[layer].getPixels().upload(0, rect.minX, rect.minZ, rect.minX, rect.minZ,
                rect.maxX - rect.minX, rect.maxZ - rect.minZ, false, false, false, false);
        rect.clear();
    }
}
