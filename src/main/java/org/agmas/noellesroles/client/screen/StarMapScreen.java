package org.agmas.noellesroles.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import org.agmas.noellesroles.client.map.AreaMapManager;
import org.agmas.noellesroles.client.map.StarMapManager;
import org.agmas.noellesroles.client.map.StarRegion;

import java.util.List;

/**
 * 星级地图全屏界面。
 *
 * <p>左侧为地图画布：左键拖动平移、滚轮缩放；3D 模式下右键拖动旋转视角。
 * 已探索区域以 {@link AreaMapManager} 的地形纹理渲染，未探索区域覆盖深色迷雾
 * 纹理（来自 {@link StarMapManager}）。星级区域以彩色边框和标签标注。
 * 右侧面板：2D/3D 视图切换 + 星级图例 + 探索统计。
 * </p>
 */
public class StarMapScreen extends Screen {

    // 色板
    private static final int BG_TOP = 0xF00A1220;
    private static final int BG_BOTTOM = 0xF0121A30;
    private static final int PANEL_BG = 0xD80A1220;
    private static final int PANEL_BORDER = 0xFFD4AF37;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int ACCENT = 0xFF4AB8C0;

    /** 3D 视图纵向压扁系数。 */
    private static final float ISO_SQUASH = 0.55f;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 14f;

    private static final int SIDE_W = 140;
    private static final int PAD = 8;

    // 视图状态
    private boolean mode3d = false;
    private float zoom = 3f;
    private double panCX, panCZ;
    private float rotYaw = 0f;
    private boolean viewInited = false;

    private boolean panning = false;
    private boolean rotating = false;

    // 画布区域
    private int canvasX0, canvasY0, canvasX1, canvasY1;
    // 右侧面板
    private int sideX0, sideY0, sideX1, sideY1;

    public StarMapScreen() {
        super(Component.translatable("gui.noellesroles.star_map.title"));
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
        if (!viewInited && AreaMapManager.hasData()) {
            recenter();
            zoom = fitZoom();
            viewInited = true;
        }
    }

    private float fitZoom() {
        int nx = Math.max(1, AreaMapManager.getSizeX());
        int nz = Math.max(1, AreaMapManager.getSizeZ());
        return Mth.clamp(
                Math.min((canvasX1 - canvasX0 - 20) / (float) nx,
                        (canvasY1 - canvasY0 - 20) / (float) nz),
                MIN_ZOOM, MAX_ZOOM);
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
        g.fill(canvasX0, canvasY0, canvasX1, canvasY1, PANEL_BG);
        g.renderOutline(canvasX0, canvasY0, canvasX1 - canvasX0, canvasY1 - canvasY0, PANEL_BORDER);
        g.fill(canvasX0 + 1, canvasY0 + 1, canvasX1 - 1, canvasY0 + 2, 0x33FFE8C0);
        // 右侧面板
        g.fill(sideX0, sideY0, sideX1, sideY1, PANEL_BG);
        g.renderOutline(sideX0, sideY0, sideX1 - sideX0, sideY1 - sideY0, PANEL_BORDER);
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
        g.drawString(font,
                title.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD),
                PAD + 2, 12, GOLD);

        renderCanvas(g, mouseX, mouseY);
        renderSidePanel(g, mouseX, mouseY);

        // 底部操作提示
        Component hint = mode3d
                ? Component.translatable("gui.noellesroles.star_map.hint3d")
                : Component.translatable("gui.noellesroles.star_map.hint");
        g.drawString(font, hint, canvasX0 + 4, height - PAD - 12, MUTED, false);
    }

    // ==================== 地图画布 ====================

    private void renderCanvas(GuiGraphics g, int mouseX, int mouseY) {
        if (!AreaMapManager.hasData()) {
            Component text = Component.translatable("gui.noellesroles.star_map.scanning");
            g.drawCenteredString(font, text, (canvasX0 + canvasX1) / 2,
                    (canvasY0 + canvasY1) / 2 - 4, MUTED);
            return;
        }

        int ccx = (canvasX0 + canvasX1) / 2;
        int ccy = (canvasY0 + canvasY1) / 2;
        int texW = AreaMapManager.getSizeX();
        int texH = AreaMapManager.getSizeZ();
        float layerPx = Math.max(2f, zoom * 0.6f);
        FakeGuiGraphics fake = new FakeGuiGraphics(g, true);

        g.enableScissor(canvasX0 + 1, canvasY0 + 1, canvasX1 - 1, canvasY1 - 1);

        // ===== 1. 底图地形 =====
        blitMapLayer(g, AreaMapManager.getBaseTexture(), ccx, ccy, texW, texH, 0f);

        // 3D 墙体层
        if (mode3d) {
            for (int layer = 0; layer < AreaMapManager.WALL_LAYERS; layer++) {
                blitMapLayer(g, AreaMapManager.getWallTexture(layer),
                        ccx, ccy, texW, texH, (layer + 1) * layerPx);
            }
        }

        // ===== 2. 迷雾覆盖 =====
        StarMapManager.syncDimensions(AreaMapManager.getOriginX(), AreaMapManager.getOriginZ(),
                texW, texH, AreaMapManager.getStep());
        if (StarMapManager.hasFogTexture()) {
            blitMapLayer(g, StarMapManager.getFogTexture(), ccx, ccy, texW, texH, 0f);
        }

        float lift = mode3d ? (AreaMapManager.WALL_LAYERS + 1) * layerPx : 0f;

        // ===== 3. 星级区域边框 =====
        renderStarRegionBorders(g, ccx, ccy, lift, texW, texH);

        // ===== 4. 家居标记 =====
        if (StarMapManager.homePos != null) {
            renderHomeMarker(g, ccx, ccy, lift);
        }

        // ===== 5. 玩家标记 =====
        renderPlayerMarker(g, ccx, ccy, lift);

        g.disableScissor();

        // 扫描进度
        if (!AreaMapManager.isFirstPassDone()) {
            Component text = Component.translatable("gui.noellesroles.star_map.scan_progress",
                    (int) (AreaMapManager.scanProgress() * 100));
            g.drawString(font, text, canvasX0 + 4, canvasY0 + 4, MUTED, false);
        }

        // 鼠标悬浮的星级区域提示
        Player player = Minecraft.getInstance().player;
        if (player != null && isInRect(mouseX, mouseY, canvasX0, canvasY0,
                canvasX1 - canvasX0, canvasY1 - canvasY0)) {
            double worldX = AreaMapManager.cellToWorldX(
                    panCX + (mouseX - ccx) / zoom);
            double worldZ = AreaMapManager.cellToWorldZ(
                    panCZ + (mouseY - ccy) / zoom);
            StarRegion hovered = StarMapManager.getRegionAt(worldX, worldZ);
            if (hovered != null) {
                Component tip = Component.literal(hovered.starSymbol() + " " + hovered.name)
                        .withStyle(chatFormattingForStar(hovered.star));
                g.renderTooltip(font, tip, mouseX, mouseY);
            }
        }
    }

    /** 绘制一层地图纹理。
     * <p>PoseStack 变换顺序（作用于顶点反序）：
     * <ol>
     *   <li>{@code translate(-panCX, -panCZ)} —— 纹理格坐标原点对齐到当前视图中心；</li>
     *   <li>{@code scale(zoom)} —— 纹理随缩放等级放大/缩小（<b>原实现漏了这一步，导致纹理永远 1:1 显示、zoom 失效、地形与星级边框错位</b>）；</li>
     *   <li>[3D] {@code rotate(Z, rotYaw)} + {@code scale(1, ISO_SQUASH)} —— 等距视角旋转与纵向压扁；</li>
     *   <li>{@code translate(ccx, ccy - liftPx)} —— 移到画布中心。</li>
     * </ol>
     * 与 {@link #cellToScreen} 的计算完全一致，保证纹理像素与星级边框/标记对齐。
     */
    private void blitMapLayer(GuiGraphics g,
            net.minecraft.resources.ResourceLocation tex,
            int ccx, int ccy, int mapW, int mapH, float liftPx) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(ccx, ccy - liftPx, 0);
        if (mode3d) {
            pose.scale(1f, ISO_SQUASH, 1f);
            pose.mulPose(Axis.ZP.rotationDegrees(rotYaw));
        }
        pose.scale(zoom, zoom, 1f);
        pose.translate((float) (-panCX), (float) (-panCZ), 0);
        g.innerBlit(tex, 0, Math.max(1, mapW), 0, Math.max(1, mapH), 0,
                0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f);
        pose.popPose();
    }

    /** 地图格坐标 → 画布屏幕坐标。 */
    private double[] cellToScreen(double cellX, double cellZ, int ccx, int ccy, float liftPx) {
        double dx = (cellX - panCX) * zoom;
        double dz = (cellZ - panCZ) * zoom;
        if (!mode3d)
            return new double[] { ccx + dx, ccy + dz };
        double rad = Math.toRadians(rotYaw);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double rx = dx * cos - dz * sin;
        double ry = (dx * sin + dz * cos) * ISO_SQUASH;
        return new double[] { ccx + rx, ccy + ry - liftPx };
    }

    /** 屏幕位移 → 地图格位移。 */
    private double[] screenDeltaToCell(double dx, double dy) {
        if (!mode3d)
            return new double[] { dx / zoom, dy / zoom };
        double rad = Math.toRadians(rotYaw);
        double cos = Math.cos(rad), sin = Math.sin(rad);
        double uy = dy / ISO_SQUASH;
        return new double[] { (dx * cos + uy * sin) / zoom,
                (-dx * sin + uy * cos) / zoom };
    }

    // ==================== 星级区域边框渲染 ====================

    private void renderStarRegionBorders(GuiGraphics g, int ccx, int ccy,
            float lift, int texW, int texH) {
        List<StarRegion> regions = StarMapManager.getStarRegions();
        for (StarRegion region : regions) {
            // 将区域边界转为纹理格坐标
            double cx0 = AreaMapManager.worldToCellX(region.bounds.minX);
            double cz0 = AreaMapManager.worldToCellZ(region.bounds.minZ);
            double cx1 = AreaMapManager.worldToCellX(region.bounds.maxX);
            double cz1 = AreaMapManager.worldToCellZ(region.bounds.maxZ);

            // 区域角点
            double[][] corners = {
                    cellToScreen(cx0, cz0, ccx, ccy, lift),
                    cellToScreen(cx1, cz0, ccx, ccy, lift),
                    cellToScreen(cx1, cz1, ccx, ccy, lift),
                    cellToScreen(cx0, cz1, ccx, ccy, lift)
            };

            // 裁剪到画布范围（粗略）
            boolean anyVisible = false;
            for (double[] c : corners) {
                if (c[0] >= canvasX0 - 40 && c[0] <= canvasX1 + 40
                        && c[1] >= canvasY0 - 40 && c[1] <= canvasY1 + 40) {
                    anyVisible = true;
                    break;
                }
            }
            if (!anyVisible)
                continue;

            int color = region.color;
            int bgColor = (color & 0x00FFFFFF) | 0x44000000;

            // 填充区域半透明底色
            int minSx = Integer.MAX_VALUE, maxSx = Integer.MIN_VALUE;
            int minSy = Integer.MAX_VALUE, maxSy = Integer.MIN_VALUE;
            for (double[] c : corners) {
                minSx = Math.min(minSx, (int) c[0]);
                maxSx = Math.max(maxSx, (int) c[0]);
                minSy = Math.min(minSy, (int) c[1]);
                maxSy = Math.max(maxSy, (int) c[1]);
            }
            // 填充区域半透明底色（仅 2D：3D 旋转后角点非轴对齐，fill 矩形会超出实际区域）
            if (!mode3d && maxSx > minSx && maxSy > minSy) {
                g.fill(minSx, minSy, maxSx, maxSy, bgColor);
            }

            // 绘制四条边（粗线）
            drawThickLine(g, (int) corners[0][0], (int) corners[0][1],
                    (int) corners[1][0], (int) corners[1][1], color);
            drawThickLine(g, (int) corners[1][0], (int) corners[1][1],
                    (int) corners[2][0], (int) corners[2][1], color);
            drawThickLine(g, (int) corners[2][0], (int) corners[2][1],
                    (int) corners[3][0], (int) corners[3][1], color);
            drawThickLine(g, (int) corners[3][0], (int) corners[3][1],
                    (int) corners[0][0], (int) corners[0][1], color);

            // 标签：星级符号 + 名称（区域中心偏上）
            int labelX = (int) ((corners[0][0] + corners[2][0]) / 2);
            int labelY = (int) ((corners[0][1] + corners[2][1]) / 2) - 2;
            String label = region.starSymbol() + " " + region.name;
            // 标签背景
            int labelW = font.width(label);
            g.fill(labelX - labelW / 2 - 2, labelY - 5, labelX + labelW / 2 + 2,
                    labelY + 5, 0xCC000000);
            g.drawCenteredString(font, label, labelX, labelY - 4, color);
        }
    }

    /** 绘制粗细为 2px 的实线（从 A 到 B）。 */
    private void drawThickLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int cx = x0, cy = y0;
        // Bresenham 画线，每个点画 2x2
        while (true) {
            g.fill(cx, cy, cx + 2, cy + 2, color);
            if (cx == x1 && cy == y1)
                break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    // ==================== 标记渲染 ====================

    private void renderHomeMarker(GuiGraphics g, int ccx, int ccy, float lift) {
        BlockPos home = StarMapManager.homePos;
        if (home == null)
            return;
        double cellX = AreaMapManager.worldToCellX(home.getX() + 0.5);
        double cellZ = AreaMapManager.worldToCellZ(home.getZ() + 0.5);
        double[] s = cellToScreen(cellX, cellZ, ccx, ccy, lift);
        int sx = (int) Math.round(s[0]);
        int sy = (int) Math.round(s[1]);
        // 房形
        g.fill(sx - 5, sy - 4, sx + 5, sy + 5, 0xAA000000);
        g.fill(sx - 4, sy - 3, sx + 4, sy + 4, GOLD);
        g.fill(sx - 2, sy - 6, sx + 2, sy - 3, GOLD);
    }

    private void renderPlayerMarker(GuiGraphics g, int ccx, int ccy, float lift) {
        Player player = Minecraft.getInstance().player;
        if (player == null)
            return;
        double cellX = AreaMapManager.worldToCellX(player.getX());
        double cellZ = AreaMapManager.worldToCellZ(player.getZ());
        double[] s = cellToScreen(cellX, cellZ, ccx, ccy, lift);
        int sx = (int) Math.round(s[0]);
        int sy = (int) Math.round(s[1]);
        g.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFF000000);
        g.fill(sx - 2, sy - 2, sx + 2, sy + 2, 0xFFFFFFFF);

        // 朝向
        double yawRad = Math.toRadians(player.getYRot());
        double dirX = -Math.sin(yawRad), dirZ = Math.cos(yawRad);
        double[] tip = cellToScreen(
                cellX + dirX * 5.0 / zoom, cellZ + dirZ * 5.0 / zoom,
                ccx, ccy, lift);
        int tx = (int) Math.round(tip[0]);
        int ty = (int) Math.round(tip[1]);
        g.fill(tx - 1, ty - 1, tx + 1, ty + 1, GOLD);
    }

    // ==================== 右侧面板 ====================

    private void renderSidePanel(GuiGraphics g, int mouseX, int mouseY) {
        int x = sideX0 + 6;
        int y = sideY0 + 6;

        // 视图模式
        g.drawString(font, Component.translatable("gui.noellesroles.star_map.mode")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, GOLD, false);
        y += 12;
        int btnW = (sideX1 - sideX0 - 12 - 4) / 2;
        drawToggleButton(g, x, y, btnW, 16,
                Component.literal("2D"), !mode3d, mouseX, mouseY);
        drawToggleButton(g, x + btnW + 4, y, btnW, 16,
                Component.literal("3D"), mode3d, mouseX, mouseY);
        y += 20;

        // 回到玩家
        drawToggleButton(g, x, y, sideX1 - sideX0 - 12, 14,
                Component.translatable("gui.noellesroles.star_map.recenter"),
                false, mouseX, mouseY);
        y += 18;

        // 分隔线
        g.fill(x, y, sideX1 - 6, y + 1, 0x33FFE8C0);
        y += 6;

        // 探索统计
        g.drawString(font, Component.translatable("gui.noellesroles.star_map.exploration")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, GOLD, false);
        y += 12;
        int explored = StarMapManager.exploredChunkCount();
        g.drawString(font, Component.translatable("gui.noellesroles.star_map.chunks_explored", explored)
                .withStyle(ChatFormatting.AQUA), x, y, ACCENT, false);
        y += 10;

        if (!AreaMapManager.isFirstPassDone()) {
            g.drawString(font, Component.translatable("gui.noellesroles.star_map.scanning_terrain")
                    .withStyle(ChatFormatting.GRAY), x, y, MUTED, false);
            y += 10;
        }

        // 分隔线
        y += 2;
        g.fill(x, y, sideX1 - 6, y + 1, 0x33FFE8C0);
        y += 6;

        // 星级图例
        g.drawString(font, Component.translatable("gui.noellesroles.star_map.legend")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, GOLD, false);
        y += 12;
        for (int star = 5; star >= 1; star--) {
            int color = StarRegion.STAR_COLORS[star - 1];
            g.fill(x, y + 2, x + 10, y + 10, color);
            String label = "\u2605".repeat(star);
            g.drawString(font, label, x + 14, y + 2, color, false);
            y += 12;
        }

        // 星区列表
        List<StarRegion> regions = StarMapManager.getStarRegions();
        if (!regions.isEmpty()) {
            y += 4;
            g.fill(x, y, sideX1 - 6, y + 1, 0x33FFE8C0);
            y += 6;
            g.drawString(font, Component.translatable("gui.noellesroles.star_map.regions")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, GOLD, false);
            y += 12;
            int maxRegions = Math.min(regions.size(), 8);
            for (int i = 0; i < maxRegions; i++) {
                StarRegion r = regions.get(i);
                g.fill(x, y + 2, x + 8, y + 8, r.color);
                String entry = r.starSymbol() + " " + r.name;
                // 截断过长的名称
                if (font.width(entry) > sideX1 - sideX0 - 28) {
                    entry = font.plainSubstrByWidth(entry, sideX1 - sideX0 - 32) + "..";
                }
                g.drawString(font, entry, x + 12, y + 1, TEXT, false);
                y += 12;
            }
        }
    }

    private void drawToggleButton(GuiGraphics g, int x, int y, int w, int h,
            Component label, boolean active, int mouseX, int mouseY) {
        boolean hover = isInRect(mouseX, mouseY, x, y, w, h);
        int bg = active ? blendColors(PANEL_BG, 0xFF4AB8C0, 0.25f)
                : hover ? 0x22FFFFFF : 0x11000000;
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, active || hover ? ACCENT : 0xFF5A4530);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2,
                active ? TEXT : MUTED);
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = sideX0 + 6;
        int y = sideY0 + 18;
        int btnW = (sideX1 - sideX0 - 12 - 4) / 2;

        // 2D 按钮
        if (button == 0 && isInRect((int) mouseX, (int) mouseY, x, y, btnW, 16)) {
            mode3d = false;
            playClick();
            return true;
        }
        // 3D 按钮
        if (button == 0 && isInRect((int) mouseX, (int) mouseY, x + btnW + 4, y, btnW, 16)) {
            mode3d = true;
            playClick();
            return true;
        }
        // 回到玩家
        if (button == 0 && isInRect((int) mouseX, (int) mouseY, x, y + 20,
                sideX1 - sideX0 - 12, 14)) {
            recenter();
            rotYaw = 0f;
            playClick();
            return true;
        }

        // 画布交互
        if (isInRect((int) mouseX, (int) mouseY, canvasX0, canvasY0,
                canvasX1 - canvasX0, canvasY1 - canvasY0)) {
            if (button == 0) {
                panning = true;
                return true;
            }
            if (button == 1 && mode3d) {
                rotating = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
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
        if (button == 0)
            panning = false;
        if (button == 1)
            rotating = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
            double horizontalAmount, double verticalAmount) {
        if (isInRect((int) mouseX, (int) mouseY, canvasX0, canvasY0,
                canvasX1 - canvasX0, canvasY1 - canvasY0)) {
            float oldZoom = zoom;
            zoom = Mth.clamp(zoom * (float) Math.pow(1.2, verticalAmount),
                    MIN_ZOOM, MAX_ZOOM);
            if (!mode3d && zoom != oldZoom) {
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // R 键：重置视图
        if (keyCode == 82) {
            recenter();
            rotYaw = 0f;
            zoom = fitZoom();
            return true;
        }
        // P 键：回到玩家
        if (keyCode == 80) {
            recenter();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 工具 ====================

    private static boolean isInRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24)
                | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8)
                | (int) (b1 + (b2 - b1) * t);
    }

    private static ChatFormatting chatFormattingForStar(int star) {
        return star >= 5 ? ChatFormatting.RED
                : star >= 4 ? ChatFormatting.GOLD
                        : star >= 3 ? ChatFormatting.YELLOW
                                : star >= 2 ? ChatFormatting.AQUA
                                        : ChatFormatting.GREEN;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
    }
}
