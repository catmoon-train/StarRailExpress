package org.agmas.noellesroles.client.map;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.client.screen.StarMapScreen;

/**
 * 手持星级地图物品时，在屏幕右侧显示的 HUD 小地图。
 *
 * <p>北向朝上、以玩家为中心的固定视野小地图。已探索区域显示真实地形（来自
 * {@link AreaMapManager}），未探索区域覆盖深色迷雾（来自 {@link StarMapManager}）。
 * 视野内的星级区域以彩色边框标注（参考海图岛屿描边），玩家所在区域在顶部显示
 * 星级符号。叠加玩家朝向标记与家居位置。
 *
 * <p>与 {@link StarMapScreen} 全屏界面共享 {@link StarMapManager} 的星级区域数据，
 * 该数据由服务端通过 {@code SixtySecondsStarMapS2CPacket} 同步。
 */
public final class StarMapHud {

    /** 小地图边长（px）。 */
    private static final int SIZE = 108;
    /** 小地图可视范围（方块，横向）。 */
    private static final int VIEW_BLOCKS = 80;

    private StarMapHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null)
            return;
        if (mc.screen instanceof StarMapScreen)
            return;
        if (!StarMapManager.isHoldingStarMap(player))
            return;

        int x = g.guiWidth() - SIZE - 8;
        int y = (g.guiHeight() - SIZE) / 2;

        // 面板
        g.fillGradient(x - 3, y - 3, x + SIZE + 3, y + SIZE + 3, 0xD80A1220, 0xD8121A30);
        g.renderOutline(x - 3, y - 3, SIZE + 6, SIZE + 6, 0xFFD4AF37);
        g.fill(x - 2, y - 2, x + SIZE + 2, y - 1, 0x33FFE8C0);

        if (!AreaMapManager.hasData()) {
            Component text = Component.translatable("gui.noellesroles.star_map.scanning");
            g.drawCenteredString(mc.font, text, x + SIZE / 2, y + SIZE / 2 - 4, 0xFF9E8B6E);
            return;
        }

        int step = AreaMapManager.getStep();
        int texW = AreaMapManager.getSizeX();
        int texH = AreaMapManager.getSizeZ();
        double pcx = AreaMapManager.worldToCellX(player.getX());
        double pcz = AreaMapManager.worldToCellZ(player.getZ());
        double halfCells = VIEW_BLOCKS / 2.0 / step;
        double pxPerCell = SIZE / (halfCells * 2);
        int cx = x + SIZE / 2, cy = y + SIZE / 2;

        // 先绘制地形（与 AreaMapHud 相同）
        double u0 = pcx - halfCells, v0 = pcz - halfCells;
        double cu0 = Math.max(u0, 0), cv0 = Math.max(v0, 0);
        double cu1 = Math.min(pcx + halfCells, texW), cv1 = Math.min(pcz + halfCells, texH);
        if (cu1 > cu0 && cv1 > cv0) {
            int sx0 = x + (int) Math.round((cu0 - u0) * pxPerCell);
            int sy0 = y + (int) Math.round((cv0 - v0) * pxPerCell);
            int sx1 = x + (int) Math.round((cu1 - u0) * pxPerCell);
            int sy1 = y + (int) Math.round((cv1 - v0) * pxPerCell);
            g.innerBlit(AreaMapManager.getBaseTexture(), sx0, sx1, sy0, sy1, 0,
                    (float) (cu0 / texW), (float) (cu1 / texW),
                    (float) (cv0 / texH), (float) (cv1 / texH),
                    1f, 1f, 1f, 1f);

            // 迷雾覆盖层
            // 原点必须与地形纹理一致（AreaMapManager 的世界坐标原点），
            // 否则迷雾按世界坐标 (0,0) 起算，与玩家附近的地形窗口错位，导致位置看不准。
            if (StarMapManager.hasFogTexture()) {
                StarMapManager.syncDimensions(
                        AreaMapManager.getOriginX(), AreaMapManager.getOriginZ(),
                        texW, texH, step);
                g.innerBlit(StarMapManager.getFogTexture(), sx0, sx1, sy0, sy1, 0,
                        (float) (cu0 / texW), (float) (cu1 / texW),
                        (float) (cv0 / texH), (float) (cv1 / texH),
                        0.75f, 0.75f, 0.75f, 1f);
            }
        }

        // 视野内星级区域边框（参考海图岛屿描边）
        drawStarRegions(g, x, y, pcx, pcz, pxPerCell);

        // 家居位置标记
        if (StarMapManager.homePos != null) {
            drawHomeMarker(g, x, y, pcx, pcz, halfCells, pxPerCell, StarMapManager.homePos);
        }

        // 玩家在星级区域时，上方显示星级
        StarRegion region = StarMapManager.getRegionAt(player.getX(), player.getZ());
        if (region != null) {
            String starStr = region.starSymbol();
            g.drawCenteredString(mc.font, starStr, cx, y + 1, region.color);
        } else {
            // 北向指示
            g.drawCenteredString(mc.font, "N", cx, y + 1, 0xFFD4AF37);
        }

        // 玩家标记
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF000000);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        double yawRad = Math.toRadians(player.getYRot());
        int nx = cx + (int) Math.round(-Math.sin(yawRad) * 5);
        int ny = cy + (int) Math.round(Math.cos(yawRad) * 5);
        g.fill(nx - 1, ny - 1, nx + 1, ny + 1, 0xFFFFD700);

        // 探索进度
        if (StarMapManager.exploredChunkCount() > 0) {
            String progress = StarMapManager.exploredChunkCount() + " chunks";
            g.drawCenteredString(mc.font, progress, cx, y + SIZE - 2, 0x449E8B6E);
        }
    }

    /**
     * 在 HUD 小地图上绘制视野内的星级区域边框。
     * <p>将区域的世界 AABB 转成格坐标再投影到屏幕，裁剪到 HUD 矩形后画 1px 边框。
     * 玩家当前所在区域用 2px 高亮边框。区域过大或完全在视野外则跳过。
     */
    private static void drawStarRegions(FakeGuiGraphics g, int x, int y,
            double pcx, double pcz, double pxPerCell) {
        for (StarRegion region : StarMapManager.getStarRegions()) {
            double minCx = AreaMapManager.worldToCellX(region.bounds.minX);
            double maxCx = AreaMapManager.worldToCellX(region.bounds.maxX);
            double minCz = AreaMapManager.worldToCellZ(region.bounds.minZ);
            double maxCz = AreaMapManager.worldToCellZ(region.bounds.maxZ);

            // 格坐标 → 相对玩家的屏幕偏移
            int sx0 = x + (int) Math.round((minCx - pcx) * pxPerCell) + SIZE / 2;
            int sy0 = y + (int) Math.round((minCz - pcz) * pxPerCell) + SIZE / 2;
            int sx1 = x + (int) Math.round((maxCx - pcx) * pxPerCell) + SIZE / 2;
            int sy1 = y + (int) Math.round((maxCz - pcz) * pxPerCell) + SIZE / 2;

            // 视锥剔除：完全在 HUD 外则跳过
            if (sx1 < x || sx0 > x + SIZE || sy1 < y || sy0 > y + SIZE) {
                continue;
            }

            // 裁剪到 HUD 矩形
            int csx0 = Math.max(sx0, x);
            int csy0 = Math.max(sy0, y);
            int csx1 = Math.min(sx1, x + SIZE);
            int csy1 = Math.min(sy1, y + SIZE);

            // 半透明填充（裁剪后）
            int bg = (region.color & 0x00FFFFFF) | 0x33000000;
            if (csx1 > csx0 && csy1 > csy0) {
                g.fill(csx0, csy0, csx1, csy1, bg);
            }

            // 边框：只画在 HUD 内的部分（用裁剪后的角点判断每条边是否可见）
            // 上边
            if (sy0 >= y && sy0 <= y + SIZE) {
                g.fill(Math.max(sx0, x), sy0, Math.min(sx1, x + SIZE), sy0 + 1, region.color);
            }
            // 下边
            if (sy1 >= y && sy1 <= y + SIZE) {
                g.fill(Math.max(sx0, x), sy1, Math.min(sx1, x + SIZE), sy1 + 1, region.color);
            }
            // 左边
            if (sx0 >= x && sx0 <= x + SIZE) {
                g.fill(sx0, Math.max(sy0, y), sx0 + 1, Math.min(sy1, y + SIZE), region.color);
            }
            // 右边
            if (sx1 >= x && sx1 <= x + SIZE) {
                g.fill(sx1, Math.max(sy0, y), sx1 + 1, Math.min(sy1, y + SIZE), region.color);
            }
        }
    }

    private static void drawHomeMarker(FakeGuiGraphics g, int x, int y, double pcx, double pcz,
            double halfCells, double pxPerCell, BlockPos home) {
        double cellX = AreaMapManager.worldToCellX(home.getX() + 0.5);
        double cellZ = AreaMapManager.worldToCellZ(home.getZ() + 0.5);
        double dx = cellX - (pcx - halfCells);
        double dz = cellZ - (pcz - halfCells);
        if (dx < 0 || dz < 0 || dx > halfCells * 2 || dz > halfCells * 2)
            return;
        int sx = x + (int) Math.round(dx * pxPerCell);
        int sy = y + (int) Math.round(dz * pxPerCell);
        // 房形标记
        g.fill(sx - 4, sy - 3, sx + 4, sy + 4, 0xAA000000);
        g.fill(sx - 3, sy - 2, sx + 3, sy + 3, 0xFFD4AF37);
        g.fill(sx - 1, sy - 4, sx + 1, sy - 2, 0xFFD4AF37);
    }
}
