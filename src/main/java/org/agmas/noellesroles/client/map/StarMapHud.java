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
 * <p>北向朝上、以玩家为中心的固定视野小地图。已探索区域显示地形，
 * 未探索区域覆盖深色迷雾。叠加玩家朝向标记、家居位置和星级区域指示。
 * 数据来自 {@link AreaMapManager}（地形）和 {@link StarMapManager}（迷雾/星级）。
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
            if (StarMapManager.hasFogTexture()) {
                StarMapManager.syncDimensions(0, 0, texW, texH, step);
                g.innerBlit(StarMapManager.getFogTexture(), sx0, sx1, sy0, sy1, 0,
                        (float) (cu0 / texW), (float) (cu1 / texW),
                        (float) (cv0 / texH), (float) (cv1 / texH),
                        0.75f, 0.75f, 0.75f, 1f);
            }
        }

        // 家居位置标记
        if (StarMapManager.homePos != null) {
            drawHomeMarker(g, x, y, pcx, pcz, halfCells, pxPerCell, StarMapManager.homePos);
        }

        // 玩家在星级区域时，上方显示星级
        StarRegion region = StarMapManager.getRegionAt(player.getX(), player.getZ());
        if (region != null) {
            int cx = x + SIZE / 2;
            String starStr = region.starSymbol();
            g.drawCenteredString(mc.font, starStr, cx, y + 1, region.color);
        } else {
            // 北向指示
            g.drawCenteredString(mc.font, "N", x + SIZE / 2, y + 1, 0xFFD4AF37);
        }

        // 玩家标记
        int cx = x + SIZE / 2, cy = y + SIZE / 2;
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF000000);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        double yawRad = Math.toRadians(player.getYRot());
        int nx = cx + (int) Math.round(-Math.sin(yawRad) * 5);
        int ny = cy + (int) Math.round(Math.cos(yawRad) * 5);
        g.fill(nx - 1, ny - 1, nx + 1, ny + 1, 0xFFFFD700);

        // 探索进度
        if (StarMapManager.exploredChunkCount() > 0) {
            String progress = StarMapManager.exploredChunkCount() + " chunks";
            g.drawCenteredString(mc.font, progress, x + SIZE / 2, y + SIZE - 2, 0x449E8B6E);
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
