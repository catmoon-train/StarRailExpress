package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;
import org.agmas.noellesroles.client.map.AreaMapManager;
import org.agmas.noellesroles.client.map.AreaMapPointCategory;

import java.util.Map;

/**
 * 区域地图全屏界面。
 *
 * <p>左侧为地图画布：左键拖动平移、滚轮缩放；3D 模式下右键拖动旋转视角。
 * 右侧面板：2D/3D 视图切换（默认 2D）+ 任务点分类筛选列表。
 * 显示状态（视图模式、勾选分类）存于 {@link AreaMapManager}，与 HUD 小地图共享。
 */
public class AreaMapScreen extends Screen {

    // ui_style 色板
    private static final int BG_TOP = 0xF018120A;
    private static final int BG_BOTTOM = 0xF0061018;
    private static final int PANEL_TOP = 0xD81A1008;
    private static final int PANEL_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;

    /** 3D 视图的纵向压扁系数（斜二测）。 */
    private static final float ISO_SQUASH = 0.55f;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 14f;

    private static final int SIDE_W = 132;
    private static final int PAD = 8;

    // 视图状态（本次打开有效；模式与筛选存 AreaMapManager 跨界面保留）
    private float zoom = 3f;
    private double panCX, panCZ; // 视图中心（纹理格坐标）
    private float rotYaw = 0f;   // 3D 旋转角（度）
    private boolean viewInited = false;

    private boolean panning = false;
    private boolean rotating = false;
    private double catScroll = 0;

    // 画布区域
    private int canvasX0, canvasY0, canvasX1, canvasY1;
    // 右侧面板区域
    private int sideX0, sideY0, sideX1, sideY1;
    // 分类列表区域（在右侧面板内）
    private int catListY0, catListY1;

    public AreaMapScreen() {
        super(Component.translatable("gui.noellesroles.area_map.title"));
    }

    @Override
    protected void init() {
        super.init();
        sideX1 = width - PAD;
        sideX0 = sideX1 - SIDE_W;
        sideY0 = 28;
        sideY1 = height - PAD;
        canvasX0 = PAD;
        canvasY0 = 28;
        canvasX1 = sideX0 - PAD;
        canvasY1 = height - PAD;
        catListY0 = sideY0 + 66;
        catListY1 = sideY1 - 6;
        if (!viewInited && AreaMapManager.hasData()) {
            recenter();
            zoom = fitZoom();
            viewInited = true;
        }
    }

    private float fitZoom() {
        int nx = Math.max(1, AreaMapManager.getSizeX());
        int nz = Math.max(1, AreaMapManager.getSizeZ());
        float fit = Math.min((canvasX1 - canvasX0 - 20) / (float) nx, (canvasY1 - canvasY0 - 20) / (float) nz);
        return Mth.clamp(fit, MIN_ZOOM, MAX_ZOOM);
    }

    private void recenter() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            panCX = AreaMapManager.worldToCellX(player.getX());
            panCZ = AreaMapManager.worldToCellZ(player.getZ());
        } else {
            panCX = AreaMapManager.getSizeX() / 2.0;
            panCZ = AreaMapManager.getSizeZ() / 2.0;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, BG_TOP, BG_BOTTOM);
        // 画布面板
        g.fillGradient(canvasX0, canvasY0, canvasX1, canvasY1, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(canvasX0, canvasY0, canvasX1 - canvasX0, canvasY1 - canvasY0, BORDER);
        g.fill(canvasX0 + 1, canvasY0 + 1, canvasX1 - 1, canvasY0 + 2, 0x33FFE8C0);
        // 右侧面板
        g.fillGradient(sideX0, sideY0, sideX1, sideY1, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(sideX0, sideY0, sideX1 - sideX0, sideY1 - sideY0, BORDER);
        g.fill(sideX0 + 1, sideY0 + 1, sideX1 - 1, sideY0 + 2, 0x33FFE8C0);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        if (!viewInited && AreaMapManager.hasData()) {
            recenter();
            zoom = fitZoom();
            viewInited = true;
        }

        // 标题
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), PAD + 2, 12, GOLD);

        renderCanvas(g, mouseX, mouseY);
        renderSidePanel(g, mouseX, mouseY);

        // 底部操作提示（60s 模式追加标注操作说明）
        Component hint = Component.translatable(
                net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.isActive() && !AreaMapManager.mode3d
                        ? "gui.noellesroles.area_map.hint_marker"
                        : AreaMapManager.mode3d ? "gui.noellesroles.area_map.hint3d"
                                : "gui.noellesroles.area_map.hint");
        g.drawString(font, hint, canvasX0 + 4, height - PAD - 12, MUTED, false);
    }

    // ==================== 地图画布 ====================

    private void renderCanvas(GuiGraphics g, int mouseX, int mouseY) {
        if (!AreaMapManager.hasData()) {
            Component text = Component.translatable("gui.noellesroles.area_map.scanning");
            g.drawCenteredString(font, text, (canvasX0 + canvasX1) / 2, (canvasY0 + canvasY1) / 2 - 4, MUTED);
            return;
        }

        int ccx = (canvasX0 + canvasX1) / 2;
        int ccy = (canvasY0 + canvasY1) / 2;
        int texW = AreaMapManager.getSizeX();
        int texH = AreaMapManager.getSizeZ();
        int mapW = Math.max(1, Math.round(texW * zoom));
        int mapH = Math.max(1, Math.round(texH * zoom));
        float layerPx = Math.max(2f, zoom * 0.6f);
        FakeGuiGraphics fake = new FakeGuiGraphics(g, true);

        g.enableScissor(canvasX0 + 1, canvasY0 + 1, canvasX1 - 1, canvasY1 - 1);

        // 底图（3D 模式下旋转 + 纵向压扁）
        blitMapLayer(g, fake, AreaMapManager.getBaseTexture(), ccx, ccy, mapW, mapH, 0f);
        if (AreaMapManager.mode3d) {
            for (int layer = 0; layer < AreaMapManager.WALL_LAYERS; layer++) {
                blitMapLayer(g, fake, AreaMapManager.getWallTexture(layer),
                        ccx, ccy, mapW, mapH, (layer + 1) * layerPx);
            }
        }

        float lift = AreaMapManager.mode3d ? (AreaMapManager.WALL_LAYERS + 1) * layerPx : 0f;

        // 任务点（60s 模式不显示任务点，改为家点位 + 自定义标注）
        boolean sixtySeconds = net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.isActive();
        BlockPos hovered = null;
        AreaMapPointCategory hoveredCat = null;
        if (!sixtySeconds) {
            // 绘制出生点（家的金色房形）
            BlockPos spawn = getSpawnPos();
            if (spawn != null) {
                double[] s = cellToScreen(AreaMapManager.worldToCellX(spawn.getX() + 0.5),
                        AreaMapManager.worldToCellZ(spawn.getZ() + 0.5), ccx, ccy, lift);
                int sx = (int) Math.round(s[0]);
                int sy = (int) Math.round(s[1]);
                g.fill(sx - 4, sy - 3, sx + 4, sy + 4, 0xAA000000);
                g.fill(sx - 3, sy - 2, sx + 3, sy + 3, GOLD);
                g.fill(sx - 1, sy - 4, sx + 1, sy - 2, GOLD);
                if (Math.abs(mouseX - sx) <= 4 && Math.abs(mouseY - sy) <= 4) {
                    g.renderTooltip(font,
                            Component.translatable("gui.noellesroles.area_map.home_point").withStyle(ChatFormatting.GOLD),
                            mouseX, mouseY);
                }
            }
            for (Map.Entry<BlockPos, Integer> entry : NoellesrolesClient.taskBlocks.entrySet()) {
                AreaMapPointCategory cat = AreaMapPointCategory.byTypeId(entry.getValue());
                if (cat == null || !AreaMapManager.visibleCategories.contains(cat)) continue;
                if (drawPoint(g, ccx, ccy, entry.getKey(), cat.color, lift, mouseX, mouseY)) {
                    hovered = entry.getKey();
                    hoveredCat = cat;
                }
            }
            if (AreaMapManager.visibleCategories.contains(AreaMapPointCategory.DOOR)) {
                for (BlockPos door : TaskBlockOverlayRenderer.RoomDoorPositions) {
                    if (drawPoint(g, ccx, ccy, door, AreaMapPointCategory.DOOR.color, lift, mouseX, mouseY)) {
                        hovered = door;
                        hoveredCat = AreaMapPointCategory.DOOR;
                    }
                }
            }
        } else {
            renderSixtySecondsPoints(g, ccx, ccy, lift, mouseX, mouseY);
        }

        renderPlayerMarker(g, ccx, ccy, lift);

        g.disableScissor();

        // 首遍扫描进度
        if (!AreaMapManager.isFirstPassDone()) {
            Component text = Component.translatable("gui.noellesroles.area_map.scan_progress",
                    (int) (AreaMapManager.scanProgress() * 100));
            g.drawString(font, text, canvasX0 + 4, canvasY0 + 4, MUTED, false);
        }

        // 任务点悬浮提示
        if (hovered != null && isInRect(mouseX, mouseY, canvasX0, canvasY0,
                canvasX1 - canvasX0, canvasY1 - canvasY0)) {
            Component tip = hoveredCat.getName().copy().append(
                    Component.literal(" (" + hovered.getX() + ", " + hovered.getY() + ", " + hovered.getZ() + ")")
                            .withStyle(ChatFormatting.GRAY));
            g.renderTooltip(font, tip, mouseX, mouseY);
        }
    }

    /** 以画布中心为原点绘制一层地图纹理（2D 直绘，3D 加旋转与压扁）。 */
    private void blitMapLayer(GuiGraphics g, FakeGuiGraphics fake, net.minecraft.resources.ResourceLocation tex,
            int ccx, int ccy, int mapW, int mapH, float liftPx) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(ccx, ccy - liftPx, 0);
        if (AreaMapManager.mode3d) {
            pose.scale(1f, ISO_SQUASH, 1f);
            pose.mulPose(Axis.ZP.rotationDegrees(rotYaw));
        }
        pose.translate((float) (-panCX * zoom), (float) (-panCZ * zoom), 0);
        fake.innerBlit(tex, 0, mapW, 0, mapH, 0, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f);
        pose.popPose();
    }

    /** 地图格坐标 → 画布屏幕坐标（与 {@link #blitMapLayer} 的变换一致）。 */
    private double[] cellToScreen(double cellX, double cellZ, int ccx, int ccy, float liftPx) {
        double dx = (cellX - panCX) * zoom;
        double dz = (cellZ - panCZ) * zoom;
        if (!AreaMapManager.mode3d) {
            return new double[] { ccx + dx, ccy + dz };
        }
        double rad = Math.toRadians(rotYaw);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double rx = dx * cos - dz * sin;
        double ry = (dx * sin + dz * cos) * ISO_SQUASH;
        return new double[] { ccx + rx, ccy + ry - liftPx };
    }

    /** 屏幕位移 → 地图格位移（拖动平移用，{@link #cellToScreen} 的逆变换）。 */
    private double[] screenDeltaToCell(double dx, double dy) {
        if (!AreaMapManager.mode3d) {
            return new double[] { dx / zoom, dy / zoom };
        }
        double rad = Math.toRadians(rotYaw);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double uy = dy / ISO_SQUASH;
        return new double[] { (dx * cos + uy * sin) / zoom, (-dx * sin + uy * cos) / zoom };
    }

    /** 60s 模式：绘制「家」点位（金色房形）+ 所有避难所门（青色方块）+ 玩家自定义标注（彩色菱形）。 */
    private void renderSixtySecondsPoints(GuiGraphics g, int ccx, int ccy, float lift, int mouseX, int mouseY) {
        // 家点位
        BlockPos home = net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.homePos();
        Component tooltip = null;
        if (home != null) {
            double[] s = cellToScreen(AreaMapManager.worldToCellX(home.getX() + 0.5),
                    AreaMapManager.worldToCellZ(home.getZ() + 0.5), ccx, ccy, lift);
            int sx = (int) Math.round(s[0]);
            int sy = (int) Math.round(s[1]);
            // 房形：金色方块 + 深色描边 + 顶部小三角
            g.fill(sx - 4, sy - 3, sx + 4, sy + 4, 0xAA000000);
            g.fill(sx - 3, sy - 2, sx + 3, sy + 3, GOLD);
            g.fill(sx - 1, sy - 4, sx + 1, sy - 2, GOLD);
            if (Math.abs(mouseX - sx) <= 4 && Math.abs(mouseY - sy) <= 4) {
                tooltip = Component.translatable("gui.noellesroles.area_map.home_point").withStyle(ChatFormatting.GOLD);
            }
        }
        // 所有避难所门（创造模式可见，青色方块）
        for (BlockPos door : net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.shelterDoors()) {
            double[] s = cellToScreen(AreaMapManager.worldToCellX(door.getX() + 0.5),
                    AreaMapManager.worldToCellZ(door.getZ() + 0.5), ccx, ccy, lift);
            int sx = (int) Math.round(s[0]);
            int sy = (int) Math.round(s[1]);
            if (sx < canvasX0 - 5 || sx > canvasX1 + 5 || sy < canvasY0 - 5 || sy > canvasY1 + 5) continue;
            // 青色门图标：小方块 + 竖线（模拟门）
            g.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xAA000000);
            g.fill(sx - 2, sy - 2, sx + 2, sy + 2, 0xFF4AB8C0);
            g.fill(sx - 2, sy - 2, sx - 1, sy + 2, 0xFF287880);
            if (Math.abs(mouseX - sx) <= 4 && Math.abs(mouseY - sy) <= 4) {
                tooltip = Component.literal("Door " + door.getX() + ", " + door.getY() + ", " + door.getZ())
                        .withStyle(ChatFormatting.AQUA);
            }
        }
        // 自定义标注
        for (net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.Marker marker
                : net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.markers()) {
            double[] s = cellToScreen(AreaMapManager.worldToCellX(marker.worldX()),
                    AreaMapManager.worldToCellZ(marker.worldZ()), ccx, ccy, lift);
            int sx = (int) Math.round(s[0]);
            int sy = (int) Math.round(s[1]);
            if (sx < canvasX0 - 6 || sx > canvasX1 + 6 || sy < canvasY0 - 6 || sy > canvasY1 + 6) continue;
            // 菱形标注
            g.fill(sx - 1, sy - 3, sx + 1, sy + 3, 0xAA000000);
            g.fill(sx - 3, sy - 1, sx + 3, sy + 1, 0xAA000000);
            g.fill(sx - 1, sy - 2, sx + 1, sy + 2, marker.color());
            g.fill(sx - 2, sy - 1, sx + 2, sy + 1, marker.color());
            if (Math.abs(mouseX - sx) <= 4 && Math.abs(mouseY - sy) <= 4) {
                tooltip = Component.translatable("gui.noellesroles.area_map.marker_tip",
                        (int) marker.worldX(), (int) marker.worldZ()).withStyle(ChatFormatting.GRAY);
            }
        }
        if (tooltip != null && isInRect(mouseX, mouseY, canvasX0, canvasY0,
                canvasX1 - canvasX0, canvasY1 - canvasY0)) {
            g.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    /** 绘制一个任务点，返回鼠标是否悬浮其上。 */
    private boolean drawPoint(GuiGraphics g, int ccx, int ccy, BlockPos pos, int color, float lift,
            int mouseX, int mouseY) {
        double cellX = AreaMapManager.worldToCellX(pos.getX() + 0.5);
        double cellZ = AreaMapManager.worldToCellZ(pos.getZ() + 0.5);
        double[] s = cellToScreen(cellX, cellZ, ccx, ccy, lift);
        int sx = (int) Math.round(s[0]);
        int sy = (int) Math.round(s[1]);
        if (sx < canvasX0 - 4 || sx > canvasX1 + 4 || sy < canvasY0 - 4 || sy > canvasY1 + 4) return false;
        g.fill(sx - 2, sy - 2, sx + 2, sy + 2, 0xAA000000);
        g.fill(sx - 1, sy - 1, sx + 1, sy + 1, color);
        return Math.abs(mouseX - sx) <= 3 && Math.abs(mouseY - sy) <= 3;
    }

    private void renderPlayerMarker(GuiGraphics g, int ccx, int ccy, float lift) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        double cellX = AreaMapManager.worldToCellX(player.getX());
        double cellZ = AreaMapManager.worldToCellZ(player.getZ());
        double[] s = cellToScreen(cellX, cellZ, ccx, ccy, lift);
        int sx = (int) Math.round(s[0]);
        int sy = (int) Math.round(s[1]);
        g.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFF000000);
        g.fill(sx - 2, sy - 2, sx + 2, sy + 2, 0xFFFFFFFF);
        // 朝向点：沿玩家朝向偏移约 5px（格偏移 = 5/zoom，经同一变换投到屏幕）
        double yawRad = Math.toRadians(player.getYRot());
        double dirX = -Math.sin(yawRad), dirZ = Math.cos(yawRad);
        double[] tip = cellToScreen(cellX + dirX * 5.0 / zoom, cellZ + dirZ * 5.0 / zoom, ccx, ccy, lift);
        int tx = (int) Math.round(tip[0]);
        int ty = (int) Math.round(tip[1]);
        g.fill(tx - 1, ty - 1, tx + 1, ty + 1, GOLD);
    }

    // ==================== 右侧面板 ====================

    private void renderSidePanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = sideX0 + 6;
        int y = sideY0 + 6;

        // 视图模式
        g.drawString(font, Component.translatable("gui.noellesroles.area_map.mode")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, GOLD, false);
        y += 12;
        int btnW = (sideX1 - sideX0 - 12 - 4) / 2;
        drawToggleButton(g, x, y, btnW, 16,
                Component.translatable("gui.noellesroles.area_map.mode.2d"),
                !AreaMapManager.mode3d, mouseX, mouseY);
        drawToggleButton(g, x + btnW + 4, y, btnW, 16,
                Component.translatable("gui.noellesroles.area_map.mode.3d"),
                AreaMapManager.mode3d, mouseX, mouseY);
        y += 20;

        // 回到玩家
        drawToggleButton(g, x, y, sideX1 - sideX0 - 12, 14,
                Component.translatable("gui.noellesroles.area_map.recenter"), false, mouseX, mouseY);
        y += 18;

        // 60s 模式：任务点区域改为标注说明（不显示任务点分类）
        if (net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.isActive()) {
            g.drawString(font, Component.translatable("gui.noellesroles.area_map.markers_title")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, GOLD, false);
            int helpY = y + 14;
            for (int i = 1; i <= 3; i++) {
                for (var seq : font.split(Component.translatable(
                        "gui.noellesroles.area_map.markers_help" + i), sideX1 - sideX0 - 12)) {
                    g.drawString(font, seq, x, helpY, MUTED, false);
                    helpY += 10;
                }
                helpY += 2;
            }
            g.drawString(font, Component.translatable("gui.noellesroles.area_map.markers_count",
                    net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.markers().size()),
                    x, helpY + 2, TEXT, false);
            return;
        }

        // 任务点标题 + 全选/清空
        g.drawString(font, Component.translatable("gui.noellesroles.area_map.points")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, GOLD, false);
        Component all = Component.translatable("gui.noellesroles.area_map.all");
        Component none = Component.translatable("gui.noellesroles.area_map.none");
        int noneX = sideX1 - 6 - font.width(none);
        int allX = noneX - 6 - font.width(all);
        boolean hoverAll = isInRect(mouseX, mouseY, allX - 1, y - 1, font.width(all) + 2, 10);
        boolean hoverNone = isInRect(mouseX, mouseY, noneX - 1, y - 1, font.width(none) + 2, 10);
        g.drawString(font, all, allX, y, hoverAll ? GOLD : MUTED, false);
        g.drawString(font, none, noneX, y, hoverNone ? GOLD : MUTED, false);

        // 分类列表（可滚动）
        AreaMapPointCategory[] cats = AreaMapPointCategory.values();
        int rowH = 13;
        int listH = catListY1 - catListY0;
        int contentH = cats.length * rowH;
        double maxScroll = Math.max(0, contentH - listH);
        catScroll = Mth.clamp(catScroll, 0, maxScroll);

        g.enableScissor(sideX0 + 1, catListY0, sideX1 - 1, catListY1);
        int rowY = catListY0 - (int) catScroll;
        for (AreaMapPointCategory cat : cats) {
            boolean enabled = AreaMapManager.visibleCategories.contains(cat);
            boolean hover = isInRect(mouseX, mouseY, sideX0 + 1, rowY, sideX1 - sideX0 - 2, rowH)
                    && mouseY >= catListY0 && mouseY < catListY1;
            if (hover) {
                g.fill(sideX0 + 1, rowY, sideX1 - 1, rowY + rowH, 0x22FFFFFF);
            }
            // 勾选框
            int boxX = x;
            int boxY = rowY + 2;
            g.renderOutline(boxX, boxY, 9, 9, enabled ? GOLD : 0xFF5A4530);
            if (enabled) {
                g.fill(boxX + 2, boxY + 2, boxX + 7, boxY + 7, cat.color);
            }
            // 色点 + 名称
            g.fill(x + 13, rowY + 4, x + 18, rowY + 9, cat.color);
            g.drawString(font, cat.getName(), x + 22, rowY + 3, enabled ? TEXT : MUTED, false);
            rowY += rowH;
        }
        g.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            int trackH = listH;
            int thumbH = Math.max(18, (int) (trackH * (listH / (double) contentH)));
            int thumbY = catListY0 + (int) ((trackH - thumbH) * (catScroll / maxScroll));
            g.fill(sideX1 - 4, catListY0, sideX1 - 2, catListY1, 0x22FFE8C0);
            g.fill(sideX1 - 4, thumbY, sideX1 - 2, thumbY + thumbH, GOLD);
        }
    }

    private void drawToggleButton(GuiGraphics g, int x, int y, int w, int h, Component label,
            boolean active, int mouseX, int mouseY) {
        boolean hover = isInRect(mouseX, mouseY, x, y, w, h);
        int bg = active ? blendColors(0xFF1A1008, 0xFFC9A84C, 0.32f)
                : hover ? 0x33FFFFFF : 0x22000000;
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, active || hover ? GOLD : 0xFF5A4530);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, active ? TEXT : MUTED);
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 视图模式按钮
        int x = sideX0 + 6;
        int y = sideY0 + 18;
        int btnW = (sideX1 - sideX0 - 12 - 4) / 2;
        if (button == 0 && isInRect((int) mouseX, (int) mouseY, x, y, btnW, 16)) {
            AreaMapManager.mode3d = false;
            playClick();
            return true;
        }
        if (button == 0 && isInRect((int) mouseX, (int) mouseY, x + btnW + 4, y, btnW, 16)) {
            AreaMapManager.mode3d = true;
            playClick();
            return true;
        }
        // 回到玩家
        if (button == 0 && isInRect((int) mouseX, (int) mouseY, x, y + 20, sideX1 - sideX0 - 12, 14)) {
            recenter();
            rotYaw = 0f;
            playClick();
            return true;
        }
        // 60s 模式：无任务点分类交互（右侧是标注说明）
        boolean sixtySecondsPanel = net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.isActive();
        // 全选/清空
        int headY = y + 38;
        Component all = Component.translatable("gui.noellesroles.area_map.all");
        Component none = Component.translatable("gui.noellesroles.area_map.none");
        int noneX = sideX1 - 6 - font.width(none);
        int allX = noneX - 6 - font.width(all);
        if (!sixtySecondsPanel && button == 0
                && isInRect((int) mouseX, (int) mouseY, allX - 1, headY - 1, font.width(all) + 2, 10)) {
            AreaMapManager.visibleCategories.addAll(java.util.List.of(AreaMapPointCategory.values()));
            playClick();
            return true;
        }
        if (!sixtySecondsPanel && button == 0
                && isInRect((int) mouseX, (int) mouseY, noneX - 1, headY - 1, font.width(none) + 2, 10)) {
            AreaMapManager.visibleCategories.clear();
            playClick();
            return true;
        }
        // 分类勾选
        if (!sixtySecondsPanel && button == 0 && mouseX >= sideX0 + 1 && mouseX <= sideX1 - 1
                && mouseY >= catListY0 && mouseY < catListY1) {
            int idx = (int) ((mouseY - catListY0 + catScroll) / 13);
            AreaMapPointCategory[] cats = AreaMapPointCategory.values();
            if (idx >= 0 && idx < cats.length) {
                AreaMapPointCategory cat = cats[idx];
                if (!AreaMapManager.visibleCategories.remove(cat)) {
                    AreaMapManager.visibleCategories.add(cat);
                }
                playClick();
                return true;
            }
        }
        // 画布拖动
        if (isInRect((int) mouseX, (int) mouseY, canvasX0, canvasY0, canvasX1 - canvasX0, canvasY1 - canvasY0)) {
            if (button == 0) {
                // 创造模式 2D 视图：左键点中 家点位/避难所门/自定义标注 → 传送过去（须在 panning 之前拦截）
                if (!AreaMapManager.mode3d
                        && net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.isActive()
                        && AreaMapManager.hasData()
                        && minecraft != null && minecraft.player != null && minecraft.player.isCreative()
                        && tryCreativeTeleport(mouseX, mouseY)) {
                    playClick();
                    return true;
                }
                panning = true;
                return true;
            }
            if (button == 1 && AreaMapManager.mode3d) {
                rotating = true;
                return true;
            }
            // 60s 模式 2D 视图：右键添加标注，Shift+右键删除最近标注
            if (button == 1 && !AreaMapManager.mode3d
                    && net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.isActive()
                    && AreaMapManager.hasData()) {
                int ccx = (canvasX0 + canvasX1) / 2;
                int ccy = (canvasY0 + canvasY1) / 2;
                double cellX = panCX + (mouseX - ccx) / zoom;
                double cellZ = panCZ + (mouseY - ccy) / zoom;
                double worldX = AreaMapManager.cellToWorldX(cellX);
                double worldZ = AreaMapManager.cellToWorldZ(cellZ);
                if (hasShiftDown()) {
                    // 删除半径按当前缩放折算 ~6px
                    if (net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.removeMarkerNear(
                            worldX, worldZ, 6.0 / zoom * AreaMapManager.getStep())) {
                        playClick();
                    }
                } else {
                    net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.addMarker(worldX, worldZ);
                    playClick();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 创造模式点击传送：按与 {@link #renderSixtySecondsPoints} 相同的 ±4px 命中测试
     * 依次检查 家点位 / 避难所门（带精确 Y，直接传）/ 自定义标注（只有 XZ，Y 由服务端解析），
     * 命中则发 {@code MapTeleportC2SPacket}（服务端复核创造/OP）。@return 是否命中。
     */
    private boolean tryCreativeTeleport(double mouseX, double mouseY) {
        int ccx = (canvasX0 + canvasX1) / 2;
        int ccy = (canvasY0 + canvasY1) / 2;
        BlockPos home = net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.homePos();
        if (home != null && hitsMapPoint(mouseX, mouseY, home.getX() + 0.5, home.getZ() + 0.5, ccx, ccy)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new net.exmo.sre.sixtyseconds.network.MapTeleportC2SPacket(
                            home.getX(), home.getY(), home.getZ(), true));
            return true;
        }
        for (BlockPos door : net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.shelterDoors()) {
            if (hitsMapPoint(mouseX, mouseY, door.getX() + 0.5, door.getZ() + 0.5, ccx, ccy)) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new net.exmo.sre.sixtyseconds.network.MapTeleportC2SPacket(
                                door.getX(), door.getY(), door.getZ(), true));
                return true;
            }
        }
        for (net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.Marker marker
                : net.exmo.sre.sixtyseconds.client.SixtySecondsClientMapZone.markers()) {
            if (hitsMapPoint(mouseX, mouseY, marker.worldX(), marker.worldZ(), ccx, ccy)) {
                // 标注只有 XZ：以玩家当前 Y 为参考，服务端上下扫可站立点
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new net.exmo.sre.sixtyseconds.network.MapTeleportC2SPacket(
                                (int) Math.floor(marker.worldX()), (int) Math.floor(minecraft.player.getY()),
                                (int) Math.floor(marker.worldZ()), false));
                return true;
            }
        }
        return false;
    }

    /** 地图点位命中测试（与渲染同款 ±4px；2D 视图，lift=0）。 */
    private boolean hitsMapPoint(double mouseX, double mouseY, double worldX, double worldZ, int ccx, int ccy) {
        double[] s = cellToScreen(AreaMapManager.worldToCellX(worldX),
                AreaMapManager.worldToCellZ(worldZ), ccx, ccy, 0f);
        return Math.abs(mouseX - s[0]) <= 4 && Math.abs(mouseY - s[1]) <= 4;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning && button == 0) {
            double[] d = screenDeltaToCell(dragX, dragY);
            panCX -= d[0];
            panCZ -= d[1];
            return true;
        }
        if (rotating && button == 1) {
            rotYaw += (float) dragX * 0.5f;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) panning = false;
        if (button == 1) rotating = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 分类列表滚动
        if (mouseX >= sideX0 && mouseX <= sideX1 && mouseY >= catListY0 && mouseY <= catListY1) {
            catScroll -= verticalAmount * 13;
            return true;
        }
        // 画布缩放（2D 下以鼠标位置为锚点）
        if (isInRect((int) mouseX, (int) mouseY, canvasX0, canvasY0, canvasX1 - canvasX0, canvasY1 - canvasY0)) {
            float oldZoom = zoom;
            zoom = Mth.clamp(zoom * (float) Math.pow(1.2, verticalAmount), MIN_ZOOM, MAX_ZOOM);
            if (!AreaMapManager.mode3d && zoom != oldZoom) {
                int ccx = (canvasX0 + canvasX1) / 2;
                int ccy = (canvasY0 + canvasY1) / 2;
                double cellUnderX = panCX + (mouseX - ccx) / oldZoom;
                double cellUnderZ = panCZ + (mouseY - ccy) / oldZoom;
                panCX = cellUnderX - (mouseX - ccx) / zoom;
                panCZ = cellUnderZ - (mouseY - ccy) / zoom;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 工具 ====================

    /** 获取非 60s 模式的出生点（家）位置，无可返回 null。 */
    private static BlockPos getSpawnPos() {
        var area = SREClient.areaComponent;
        if (area == null) return null;
        var spawn = area.getSpawnPos();
        return spawn != null ? BlockPos.containing(spawn.pos) : null;
    }

    private static boolean isInRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
    }
}
