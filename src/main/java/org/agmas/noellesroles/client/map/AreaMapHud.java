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

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.client.screen.AreaMapScreen;
import org.agmas.noellesroles.game.modes.echo.client.EchoFacilityMapClient;
import org.agmas.noellesroles.game.modes.echo.client.EchoHudLayout;

import java.util.Map;

/**
 * 回声对局常驻右侧；其它模式仍需手持区域地图物品。
 * 北向朝上，以玩家为中心；回声叠加本局公开设施，普通地图保留任务点分类。
 * 地形来自 {@link AreaMapManager} 的有界本地扫描。
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
        if (player == null || mc.level == null || mc.options.hideGui) return;
        boolean echo = EchoFacilityMapClient.inMatch();
        if (mc.screen instanceof AreaMapScreen || echo && mc.screen != null) return;
        if (!echo && !AreaMapManager.isHoldingMap(player)) return;

        var rect = new EchoHudLayout(g.guiWidth(), g.guiHeight()).minimap();
        int size = echo ? rect.width() : SIZE;
        int x = g.guiWidth() - size - 8;
        int y = echo ? rect.y() : (g.guiHeight() - size) / 2;
        g.fillGradient(x - 3, y - 3, x + size + 3, y + size + 3, 0xD81A1008, 0xD820140A);
        g.renderOutline(x - 3, y - 3, size + 6, size + 6, 0xFF8B6914);
        g.fill(x - 2, y - 2, x + size + 2, y - 1, 0x33FFE8C0);
        if (echo) {
            var hint = EchoFacilityMapClient.tr("open_hint", io.wifi.starrailexpress.client.InputHandler.getOpenVotingScreenKeybind().getTranslatedKeyMessage());
            g.enableScissor(x - 3, y + size + 4, x + size + 4, y + size + 15);
            g.drawCenteredString(mc.font, hint, x + size / 2, y + size + 5, 0xFFD4AF37);
            g.disableScissor();
        }
        if (!AreaMapManager.hasData()) {
            Component text = Component.translatable("gui.noellesroles.area_map.scanning");
            g.drawCenteredString(mc.font, text, x + size / 2, y + size / 2 - 4, 0xFF9E8B6E);
            return;
        }
        int step = AreaMapManager.getStep();
        int texW = AreaMapManager.getSizeX(), texH = AreaMapManager.getSizeZ();
        double pcx = AreaMapManager.worldToCellX(player.getX()), pcz = AreaMapManager.worldToCellZ(player.getZ());
        double halfCells = VIEW_BLOCKS / 2.0 / step, pxPerCell = size / (halfCells * 2);
        double u0 = pcx - halfCells, v0 = pcz - halfCells;
        double cu0 = Math.max(u0, 0), cv0 = Math.max(v0, 0);
        double cu1 = Math.min(pcx + halfCells, texW), cv1 = Math.min(pcz + halfCells, texH);
        g.enableScissor(x, y, x + size, y + size);
        if (cu1 > cu0 && cv1 > cv0) {
            int sx0 = x + (int) Math.round((cu0 - u0) * pxPerCell), sy0 = y + (int) Math.round((cv0 - v0) * pxPerCell);
            int sx1 = x + (int) Math.round((cu1 - u0) * pxPerCell), sy1 = y + (int) Math.round((cv1 - v0) * pxPerCell);
            g.innerBlit(AreaMapManager.getBaseTexture(), sx0, sx1, sy0, sy1, 0,
                    (float) (cu0 / texW), (float) (cu1 / texW), (float) (cv0 / texH), (float) (cv1 / texH), 1f, 1f, 1f, 1f);
        }
        if (echo) {
            for (var facility : EchoFacilityMapClient.entries()) {
                if (!EchoFacilityMapClient.visible(facility.type())) continue;
                double dx = AreaMapManager.worldToCellX(facility.x()) - u0;
                double dz = AreaMapManager.worldToCellZ(facility.z()) - v0;
                if (dx < 0 || dz < 0 || dx > halfCells * 2 || dz > halfCells * 2) continue;
                EchoFacilityMapClient.drawMarker(g, facility.type(), x + (int) Math.round(dx * pxPerCell), y + (int) Math.round(dz * pxPerCell), true);
            }
        } else {
            for (Map.Entry<BlockPos, Integer> entry : NoellesrolesClient.taskBlocks.entrySet()) {
                AreaMapPointCategory cat = AreaMapPointCategory.byTypeId(entry.getValue());
                if (cat == null || !AreaMapManager.visibleCategories.contains(cat)) continue;
                drawPoint(g, x, y, pcx, pcz, halfCells, pxPerCell, entry.getKey(), cat.color);
            }
            if (AreaMapManager.visibleCategories.contains(AreaMapPointCategory.DOOR))
                for (BlockPos door : TaskBlockOverlayRenderer.RoomDoorPositions)
                    drawPoint(g, x, y, pcx, pcz, halfCells, pxPerCell, door, AreaMapPointCategory.DOOR.color);
        }
        int cx = x + size / 2, cy = y + size / 2;
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF000000);
        g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
        double yawRad = Math.toRadians(player.getYRot());
        int nx = cx + (int) Math.round(-Math.sin(yawRad) * 4), ny = cy + (int) Math.round(Math.cos(yawRad) * 4);
        g.fill(nx - 1, ny - 1, nx + 1, ny + 1, 0xFFFFD700);
        g.disableScissor();
        g.drawCenteredString(mc.font, echo ? EchoFacilityMapClient.tr("north") : Component.literal("N"), x + size / 2, y + 1, 0xFFD4AF37);
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
