package org.agmas.noellesroles.client.map;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.client.screen.AreaMapScreen;

import java.util.Map;

/**
 * 手持区域地图物品时，在屏幕右侧显示的 HUD 小地图。
 *
 * <p>北向朝上、以玩家为中心的固定视野小地图，叠加已勾选分类的任务点与
 * 玩家朝向标记。数据来自 {@link AreaMapManager}（客户端本地扫描）。
 */
public final class AreaMapHud {

    /** 小地图边长（px）。 */
    private static final int SIZE = 100;
    /** 小地图可视范围（方块，横向）。 */
    private static final int VIEW_BLOCKS = 72;

    private AreaMapHud() {
    }

    public static void register() {
        CommonHudRenderCallback.EVENT.register((graphics, deltaTracker) -> render(graphics));
    }

    private static void render(FakeGuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        if (mc.screen instanceof AreaMapScreen) return;
        if (!AreaMapManager.isHoldingMap(player)) return;

        int x = g.guiWidth() - SIZE - 8;
        int y = (g.guiHeight() - SIZE) / 2;

        // 面板：渐变背景 + 描边 + 顶部装饰线（ui_style）
        g.fillGradient(x - 3, y - 3, x + SIZE + 3, y + SIZE + 3, 0xD81A1008, 0xD820140A);
        g.renderOutline(x - 3, y - 3, SIZE + 6, SIZE + 6, 0xFF8B6914);
        g.fill(x - 2, y - 2, x + SIZE + 2, y - 1, 0x33FFE8C0);

        if (!AreaMapManager.hasData()) {
            Component text = Component.translatable("gui.noellesroles.area_map.scanning");
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

        // 视野窗口与纹理求交后 blit（避免越界采样重复平铺）
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
        }

        // 任务点（仅画已勾选分类、且在视野内的）；60s 模式改画家点位与自定义标注
        boolean sixtySeconds = net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.isActive();
        if (!sixtySeconds) {
            // 绘制出生点（家，金色）
            BlockPos spawn = getSpawnPos();
            if (spawn != null) {
                drawPoint(g, x, y, pcx, pcz, halfCells, pxPerCell, spawn, 0xFFD4AF37);
            }
            for (Map.Entry<BlockPos, Integer> entry : NoellesrolesClient.taskBlocks.entrySet()) {
                AreaMapPointCategory cat = AreaMapPointCategory.byTypeId(entry.getValue());
                if (cat == null || !AreaMapManager.visibleCategories.contains(cat)) continue;
                drawPoint(g, x, y, pcx, pcz, halfCells, pxPerCell, entry.getKey(), cat.color);
            }
            if (AreaMapManager.visibleCategories.contains(AreaMapPointCategory.DOOR)) {
                for (BlockPos door : TaskBlockOverlayRenderer.RoomDoorPositions) {
                    drawPoint(g, x, y, pcx, pcz, halfCells, pxPerCell, door, AreaMapPointCategory.DOOR.color);
                }
            }
        } else {
            BlockPos home = net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.homePos();
            if (home != null) {
                drawPoint(g, x, y, pcx, pcz, halfCells, pxPerCell, home, 0xFFD4AF37);
            }
            // 所有避难所门（创造模式可见，青色小点）
            for (BlockPos door : net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.shelterDoors()) {
                drawPoint(g, x, y, pcx, pcz, halfCells, pxPerCell, door, 0xFF4AB8C0);
            }
            for (net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.Marker marker
                    : net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.markers()) {
                drawPoint(g, x, y, pcx, pcz, halfCells, pxPerCell,
                        BlockPos.containing(marker.worldX(), 0, marker.worldZ()), marker.color());
            }
        }

        // 玩家标记：中心方点 + 朝向点
        int cx = x + SIZE / 2, cy = y + SIZE / 2;
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF000000);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        double yawRad = Math.toRadians(player.getYRot());
        int nx = cx + (int) Math.round(-Math.sin(yawRad) * 4);
        int ny = cy + (int) Math.round(Math.cos(yawRad) * 4);
        g.fill(nx - 1, ny - 1, nx + 1, ny + 1, 0xFFFFD700);

        // 北向指示
        g.drawCenteredString(mc.font, "N", x + SIZE / 2, y + 1, 0xFFD4AF37);
    }

    /** 获取非 60s 模式的出生点（家）位置，无可返回 null。 */
    private static BlockPos getSpawnPos() {
        var area = SREClient.areaComponent;
        if (area == null) return null;
        var spawn = area.getSpawnPos();
        return spawn != null ? BlockPos.containing(spawn.pos) : null;
    }

    private static void drawPoint(FakeGuiGraphics g, int x, int y, double pcx, double pcz,
            double halfCells, double pxPerCell, BlockPos pos, int color) {
        double cellX = AreaMapManager.worldToCellX(pos.getX() + 0.5);
        double cellZ = AreaMapManager.worldToCellZ(pos.getZ() + 0.5);
        double dx = cellX - (pcx - halfCells);
        double dz = cellZ - (pcz - halfCells);
        if (dx < 0 || dz < 0 || dx > halfCells * 2 || dz > halfCells * 2) return;
        int sx = x + (int) Math.round(dx * pxPerCell);
        int sy = y + (int) Math.round(dz * pxPerCell);
        g.fill(sx - 2, sy - 2, sx + 2, sy + 2, 0xAA000000);
        g.fill(sx - 1, sy - 1, sx + 1, sy + 1, color);
    }
}
