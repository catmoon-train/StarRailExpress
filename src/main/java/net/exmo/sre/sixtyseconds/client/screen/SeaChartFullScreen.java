package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.client.SixtySecondsClientSeaChart;
import net.exmo.sre.sixtyseconds.island.SixtySecondsIsland;
import net.exmo.sre.sixtyseconds.island.SixtySecondsIslandGenerator;
import net.exmo.sre.sixtyseconds.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏海图——可拖动、缩放的末日60秒海岛远征海图。
 * <p>
 * 功能：
 * <ul>
 *   <li>全屏渲染深海背景 + 岛屿轮廓（同服务端形状函数重画）</li>
 *   <li>鼠标左键拖动平移、滚轮缩放</li>
 *   <li>显示玩家位置、登岛落点（来时区域）</li>
 *   <li>点击已解锁岛屿 → 扬帆（发服务端命令）</li>
 *   <li>「返回住所」按钮：仅脱战 + 站在登岛点附近时可用；
 *       点击后发 C2S 包请求返回，服务端校验后启动 10s 划船动画</li>
 * </ul>
 * </p>
 */
public class SeaChartFullScreen extends Screen {

    private static final String LANG = SixtySecondsIsland.LANG;

    /** 等级 1..5 的岛屿主色。 */
    private static final int[] LEVEL_COLORS = {0xFF59C24A, 0xFF3EC7A0, 0xFFE0C34A, 0xFFE07B39, 0xFFD94040};
    private static final int COLOR_BEACH = 0xFFD8CC9A;
    private static final int COLOR_OCEAN = 0xFF0B2740;
    private static final int COLOR_OCEAN_DEEP = 0xFF081C30;
    private static final int COLOR_FOG = 0xFF25313D;
    private static final int COLOR_ARRIVAL = 0x4400CCFF;
    private static final int COLOR_ARRIVAL_BORDER = 0x8800AAFF;
    private static final int COLOR_RETURN_AREA = 0x22FFD700;
    private static final int COLOR_RETURN_AREA_BORDER = 0x88FFD700;
    /** 庇护所 / 队友点位配色。 */
    private static final int COLOR_SHELTER = 0xFF5CD65C;
    private static final int COLOR_SHELTER_ROOF = 0xFF3A9E3A;
    private static final int COLOR_MATE = 0xFF4FD4E0;
    private static final int COLOR_MATE_DOWNED = 0xFFE05555;

    /** 最大渲染像素预算/岛（超过则提步长）。 */
    private static final int MAX_PIXELS_PER_ISLAND = 80_000;
    /** 岛屿栅格最小步长（放大到极致也不逐像素算，否则显卡冒烟）。 */
    private static final int MIN_RASTER_STEP = 2;
    /** 圆圈最大屏幕半径（再大就降采样）。 */
    private static final int MAX_CIRCLE_RADIUS = 160;

    private final SixtySecondsSeaChartS2CPacket data;

    /** 地图视口：世界坐标范围映射 */
    private double scale;
    private double viewCenterX;
    private double viewCenterZ;
    private double worldMinX;
    private double worldMinZ;
    private double worldMaxX;
    private double worldMaxZ;

    /** 拖拽状态 */
    private boolean dragging = false;
    private double dragStartX;
    private double dragStartZ;
    private int dragMouseX;
    private int dragMouseY;

    /** 悬浮的岛屿 id */
    private int hoveredIsland = -1;

    /** 登岛落点（客户端缓存，服务端同步） */
    public static BlockPos cachedArrivalPos = null;

    private Button returnButton;

    public SeaChartFullScreen(SixtySecondsSeaChartS2CPacket data) {
        super(Component.translatable(LANG + "chart_title"));
        this.data = data;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        // 不绘制默认背景，我们画全屏
    }

    @Override
    protected void init() {
        // 计算世界范围：所有岛单元格的外包
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (SixtySecondsSeaChartS2CPacket.Entry entry : data.islands()) {
            int r = entry.radius() + SixtySecondsIslandGenerator.WATER_SKIRT;
            minX = Math.min(minX, entry.centerX() - r);
            minZ = Math.min(minZ, entry.centerZ() - r);
            maxX = Math.max(maxX, entry.centerX() + r);
            maxZ = Math.max(maxZ, entry.centerZ() + r);
        }
        int spanX = Math.max(256, maxX - minX + 128);
        int spanZ = Math.max(256, maxZ - minZ + 128);
        worldMinX = minX - 64;
        worldMinZ = minZ - 64;
        worldMaxX = worldMinX + spanX;
        worldMaxZ = worldMinZ + spanZ;

        // 初始视图居中
        viewCenterX = (worldMinX + worldMaxX) / 2.0;
        viewCenterZ = (worldMinZ + worldMaxZ) / 2.0;
        scale = Math.min((double) width / spanX, (double) height / spanZ);

        // 按钮
        int btnW = 100;
        int btnH = 20;
        // sea_teleport 关闭时完全不显示"返回住所"按钮
        if (data.teleportAllowed()) {
            returnButton = Button.builder(Component.translatable(LANG + "chart_return_home"), button -> {
                if (canReturn()) {
                    // 发送 C2S 返回请求
                    ClientPlayNetworking.send(new SixtySecondsSeaChartReturnC2SPacket());
                }
            }).bounds(width - btnW - 12, 10, btnW, btnH).build();
            addRenderableWidget(returnButton);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.close"),
                button -> onClose()).bounds(width - btnW - 12, height - 32, btnW, btnH).build());

        // 订阅庇护所/队友点位：只在海图开着时服务端才每秒推（关屏见 removed()）
        ClientPlayNetworking.send(new SixtySecondsSeaChartWatchC2SPacket(true));
    }

    @Override
    public void removed() {
        // 退订点位推送。走 removed() 而非 onClose()——被别的界面顶掉时 onClose 不一定触发，
        // 漏退订会让服务端一直对着没在看海图的人推包
        if (Minecraft.getInstance().getConnection() != null) {
            ClientPlayNetworking.send(new SixtySecondsSeaChartWatchC2SPacket(false));
        }
        super.removed();
    }

    // ── 坐标映射 ──────────────────────────────────────────────────────

    private int toScreenX(double worldX) {
        return (int) ((worldX - viewCenterX) * scale + width / 2.0);
    }

    private int toScreenY(double worldZ) {
        return (int) ((worldZ - viewCenterZ) * scale + height / 2.0);
    }

    private double toWorldX(int screenX) {
        return (screenX - width / 2.0) / scale + viewCenterX;
    }

    private double toWorldZ(int screenY) {
        return (screenY - height / 2.0) / scale + viewCenterZ;
    }

    // ── 渲染 ──────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 全屏深海背景
        renderOceanBackground(graphics);
        // 岛屿
        hoveredIsland = -1;
        List<Runnable> labels = new ArrayList<>();
        for (SixtySecondsSeaChartS2CPacket.Entry entry : data.islands()) {
            renderIsland(graphics, entry, mouseX, mouseY, labels);
        }
        for (Runnable label : labels) {
            label.run();
        }
        // 庇护所与队友点位（服务端每秒推；开着海图才有数据）
        renderShelterMarker(graphics);
        renderMateMarkers(graphics);
        // 玩家标记
        renderPlayerMarker(graphics);
        // 登岛落点（来时区域）
        renderArrivalMarker(graphics);
        // 标题
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFE8D9A8);

        // 悬浮提示
        if (hoveredIsland >= 0) {
            SixtySecondsSeaChartS2CPacket.Entry entry = byId(hoveredIsland);
            if (entry != null) {
                renderTooltip(graphics, entry, mouseX, mouseY);
            }
        }

        // 底部提示文字
        graphics.drawCenteredString(font,
                Component.translatable(LANG + "chart_drag_hint").withStyle(ChatFormatting.DARK_GRAY),
                width / 2, height - 14, 0xFF666666);

        // 返回按钮状态更新
        updateReturnButton();

        // 渲染按钮
        super.render(graphics, mouseX, mouseY, partialTick);

        // 渲染返回冷却/战斗状态提示
        renderReturnStatus(graphics);
    }

    private void renderOceanBackground(GuiGraphics graphics) {
        // 深色背景铺满全屏
        graphics.fill(0, 0, width, height, 0xFF050D18);
        // 星星点（深海波纹）
        long seed = data.islands().isEmpty() ? 0 : data.islands().get(0).seed();
        for (int i = 0; i < 600; i++) {
            int px = (int) ((i * 97L + (long) i * i * 13 % 51 + seed * 3) % width);
            int py = (int) ((i * 61L + (long) i * i * 7 % 37 + seed * 7) % height);
            if (px >= 0 && px < width && py >= 0 && py < height) {
                graphics.fill(px, py, px + 1, py + 1, COLOR_OCEAN_DEEP);
            }
        }
    }

    private void renderIsland(GuiGraphics graphics, SixtySecondsSeaChartS2CPacket.Entry entry,
            int mouseX, int mouseY, List<Runnable> labels) {
        int cx = toScreenX(entry.centerX());
        int cy = toScreenY(entry.centerZ());
        int screenR = Math.max(8, (int) (entry.radius() * scale));

        // 视锥剔除：岛完全在屏幕外就跳过，省掉整个 rasterize
        if (cx + screenR < -32 || cx - screenR > width + 32
                || cy + screenR < -32 || cy - screenR > height + 32) {
            return;
        }

        int clickR = Math.max(Math.min(screenR, 48), 16); // 点击判定不超过48px
        boolean hovered = (mouseX - cx) * (mouseX - cx) + (mouseY - cy) * (mouseY - cy)
                <= (clickR + 4) * (clickR + 4);
        if (hovered) {
            hoveredIsland = entry.id();
        }

        if (!entry.unlocked()) {
            SixtySecondsIsland island = toIsland(entry);
            rasterize(graphics, island, entry, cx, cy, screenR, true);
            labels.add(() -> graphics.drawCenteredString(font, "?", cx, cy - 5, 0xFF6C7A88));
            return;
        }

        SixtySecondsIsland island = toIsland(entry);
        rasterize(graphics, island, entry, cx, cy, screenR, false);

        // 登岛落点区域圈（仅 sea_teleport 开启时显示，表示可在此返航）
        if (data.teleportAllowed() && cachedArrivalPos != null) {
            int distSqr = (cachedArrivalPos.getX() - entry.centerX()) * (cachedArrivalPos.getX() - entry.centerX())
                    + (cachedArrivalPos.getZ() - entry.centerZ()) * (cachedArrivalPos.getZ() - entry.centerZ());
            int r2 = (entry.radius() + 8) * (entry.radius() + 8);
            if (distSqr <= r2) {
                int arrivalSx = toScreenX(cachedArrivalPos.getX());
                int arrivalSy = toScreenY(cachedArrivalPos.getZ());
                final int returnR = Math.max(10, Math.min((int) (16 * scale), MAX_CIRCLE_RADIUS));
                drawCircle(graphics, arrivalSx, arrivalSy, returnR, COLOR_RETURN_AREA);
                drawCircleBorder(graphics, arrivalSx, arrivalSy, returnR, COLOR_RETURN_AREA_BORDER);
                labels.add(() -> graphics.drawCenteredString(font,
                        Component.translatable(LANG + "chart_arrival_point").withStyle(ChatFormatting.YELLOW),
                        arrivalSx, arrivalSy - returnR - 10, 0xFFFFD700));
            }
        }

        int color = LEVEL_COLORS[Mth.clamp(entry.level(), 1, 5) - 1];
        String label = islandName(entry).getString() + " Lv." + entry.level()
                + (entry.visited() ? " " + Component.translatable(LANG + "chart_visited").getString() : "");
        int labelR = Math.min(screenR, MAX_CIRCLE_RADIUS);
        labels.add(() -> {
            graphics.drawCenteredString(font, label, cx, cy + labelR + 3, color);
            if (hoveredIsland == entry.id()) {
                int hr = Math.min(screenR, MAX_CIRCLE_RADIUS);
                graphics.fill(cx - hr - 3, cy - hr - 3, cx + hr + 3, cy - hr - 2, 0xFFFFFFFF);
                graphics.fill(cx - hr - 3, cy + hr + 2, cx + hr + 3, cy + hr + 3, 0xFFFFFFFF);
                graphics.fill(cx - hr - 3, cy - hr - 2, cx - hr - 2, cy + hr + 2, 0xFFFFFFFF);
                graphics.fill(cx + hr + 2, cy - hr - 2, cx + hr + 3, cy + hr + 2, 0xFFFFFFFF);
            }
        });
    }

    private void rasterize(GuiGraphics graphics, SixtySecondsIsland island,
            SixtySecondsSeaChartS2CPacket.Entry entry, int cx, int cy, int screenR, boolean fog) {
        // 计算步长：保证每个岛像素总量不超过预算
        int diameter = screenR * 2;
        double rawStep = Math.max(1.0, 1.0 / Math.max(0.25, scale));
        int step = (int) rawStep;
        // 像素预算校准：如果按当前步长算出来的像素数超过上限，加大步长
        int roughPixels = (diameter / step) * (diameter / step);
        while (roughPixels > MAX_PIXELS_PER_ISLAND && step < diameter / 4) {
            step = Math.min(step * 2, diameter / 4);
            roughPixels = (diameter / step) * (diameter / step);
        }
        step = Math.max(step, MIN_RASTER_STEP);

        // 裁剪到屏幕可视区域再渲染
        int startX = Math.max(cx - screenR, -step);
        int endX = Math.min(cx + screenR, width + step);
        int startY = Math.max(cy - screenR, -step);
        int endY = Math.min(cy + screenR, height + step);

        int mainColor = fog ? COLOR_FOG : LEVEL_COLORS[Mth.clamp(entry.level(), 1, 5) - 1];
        int size = Math.max(1, step);

        for (int px = startX; px <= endX; px += step) {
            for (int py = startY; py <= endY; py += step) {
                if (px < 0 || px >= width || py < 0 || py >= height) {
                    continue;
                }
                double worldX = toWorldX(px);
                double worldZ = toWorldZ(py);
                float landVal = SixtySecondsIslandGenerator.landValue(island, worldX, worldZ);
                if (landVal <= SixtySecondsIslandGenerator.LAND_THRESHOLD) {
                    continue;
                }
                int color;
                if (fog) {
                    color = COLOR_FOG;
                } else if (landVal < SixtySecondsIslandGenerator.LAND_THRESHOLD + 0.08F) {
                    color = COLOR_BEACH;
                } else {
                    color = landVal > 0.55F ? darken(mainColor) : mainColor;
                }
                graphics.fill(px, py, px + size, py + size, color);
            }
        }
    }

    private void renderPlayerMarker(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int px = toScreenX(minecraft.player.getX());
        int py = toScreenY(minecraft.player.getZ());
        // 自身位置：小点
        graphics.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFFFFFF);
        // 方向箭头：白底红心的大点，沿朝向偏移
        float yaw = minecraft.player.getYRot();
        double rad = Math.toRadians(yaw);
        int ax = px + (int) (Math.sin(rad) * 10);
        int ay = py - (int) (Math.cos(rad) * 10);
        graphics.fill(ax - 3, ay - 3, ax + 3, ay + 3, 0xFFFFFFFF);
        graphics.fill(ax - 2, ay - 2, ax + 2, ay + 2, 0xFFCC3333);
    }

    /**
     * 本队庇护所点位：绿色房子标 + 名牌。落在视野外时贴到屏幕边缘画成指向箭头，
     * 并标出距离——不然玩家自己开船（sea_teleport 关）时根本不知道家在哪个方向。
     */
    private void renderShelterMarker(GuiGraphics graphics) {
        var positions = SixtySecondsClientSeaChart.positions();
        if (positions == null || !positions.hasShelter()) {
            return;
        }
        int sx = toScreenX(positions.shelterX());
        int sy = toScreenY(positions.shelterZ());
        if (sx >= 0 && sx < width && sy >= 0 && sy < height) {
            // 屋顶三角 + 屋身
            graphics.fill(sx - 4, sy - 1, sx + 4, sy + 5, COLOR_SHELTER);
            graphics.fill(sx - 5, sy - 2, sx + 5, sy - 1, COLOR_SHELTER_ROOF);
            graphics.fill(sx - 3, sy - 5, sx + 3, sy - 2, COLOR_SHELTER_ROOF);
            graphics.fill(sx - 1, sy + 1, sx + 1, sy + 5, 0xFF20301C);
            graphics.drawCenteredString(font, Component.translatable(LANG + "chart_shelter")
                    .withStyle(ChatFormatting.GREEN), sx, sy + 7, COLOR_SHELTER);
        } else {
            drawEdgeArrow(graphics, positions.shelterX(), positions.shelterZ(), COLOR_SHELTER,
                    Component.translatable(LANG + "chart_shelter").getString());
        }
    }

    /** 在线队友点位：青色方点 + 名字（倒地的画红色）。旁观/离线的队友服务端不会下发。 */
    private void renderMateMarkers(GuiGraphics graphics) {
        var positions = SixtySecondsClientSeaChart.positions();
        if (positions == null) {
            return;
        }
        for (var mate : positions.mates()) {
            int mx = toScreenX(mate.x());
            int my = toScreenY(mate.z());
            if (mx < 0 || mx >= width || my < 0 || my >= height) {
                continue;
            }
            int color = mate.downed() ? COLOR_MATE_DOWNED : COLOR_MATE;
            graphics.fill(mx - 3, my - 3, mx + 3, my + 3, 0xFF102030);
            graphics.fill(mx - 2, my - 2, mx + 2, my + 2, color);
            graphics.drawCenteredString(font, mate.name(), mx, my - 13, color);
        }
    }

    /**
     * 目标在视野外时，把标记压到屏幕边缘（留 12px 内边距）画成小方块 + 「名字 距离m」，
     * 位置沿「屏幕中心 → 目标」的方向求与边框的交点。
     */
    private void drawEdgeArrow(GuiGraphics graphics, int worldX, int worldZ, int color, String label) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        double dirX = toScreenX(worldX) - width / 2.0;
        double dirY = toScreenY(worldZ) - height / 2.0;
        double len = Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 1e-3) {
            return;
        }
        int pad = 12;
        // 沿方向缩放到最先撞上的那条边
        double scaleX = (width / 2.0 - pad) / Math.abs(dirX);
        double scaleY = (height / 2.0 - pad) / Math.abs(dirY);
        double t = Math.min(Math.abs(dirX) < 1e-3 ? Double.MAX_VALUE : scaleX,
                Math.abs(dirY) < 1e-3 ? Double.MAX_VALUE : scaleY);
        int ex = (int) (width / 2.0 + dirX * t);
        int ey = (int) (height / 2.0 + dirY * t);
        graphics.fill(ex - 4, ey - 4, ex + 4, ey + 4, 0xFF102030);
        graphics.fill(ex - 3, ey - 3, ex + 3, ey + 3, color);
        double wx = worldX - minecraft.player.getX();
        double wz = worldZ - minecraft.player.getZ();
        graphics.drawCenteredString(font, label + " " + (int) Math.sqrt(wx * wx + wz * wz) + "m",
                Mth.clamp(ex, 40, width - 40), Mth.clamp(ey + 6, 0, height - 10), color);
    }

    private void renderArrivalMarker(GuiGraphics graphics) {
        if (cachedArrivalPos == null) {
            return;
        }
        int ax = toScreenX(cachedArrivalPos.getX());
        int ay = toScreenY(cachedArrivalPos.getZ());
        if (ax < -32 || ax > width + 32 || ay < -32 || ay > height + 32) {
            return;
        }
        int r = Math.min((int) (16 * scale), MAX_CIRCLE_RADIUS);
        r = Math.max(8, r);
        drawCircle(graphics, ax, ay, r, COLOR_ARRIVAL);
        drawCircleBorder(graphics, ax, ay, r, COLOR_ARRIVAL_BORDER);
        graphics.drawCenteredString(font,
                Component.translatable(LANG + "chart_arrival_marker").withStyle(ChatFormatting.AQUA),
                ax, ay - r - 12, 0xFF00CCFF);
    }

    private void renderTooltip(GuiGraphics graphics, SixtySecondsSeaChartS2CPacket.Entry entry,
            int mouseX, int mouseY) {
        List<Component> tooltip = new ArrayList<>();
        if (entry.unlocked()) {
            tooltip.add(islandName(entry).copy().withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable(LANG + "level", entry.level())
                    .withStyle(levelFormatting(entry.level())));
            if (entry.visited()) {
                tooltip.add(Component.translatable(LANG + "chart_visited")
                        .withStyle(ChatFormatting.GREEN));
            }
            // 距离提示：关掉扬帆传送后，玩家要靠这个估自己开船要开多远
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                double dx = entry.centerX() - minecraft.player.getX();
                double dz = entry.centerZ() - minecraft.player.getZ();
                tooltip.add(Component.translatable(LANG + "chart_distance",
                        (int) Math.sqrt(dx * dx + dz * dz)).withStyle(ChatFormatting.DARK_AQUA));
            }
            tooltip.add(Component.translatable(data.teleportAllowed()
                    ? LANG + "chart_click_sail"
                    : LANG + "chart_sail_yourself").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(LANG + "chart_locked")
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable(LANG + "chart_locked_hint")
                    .withStyle(ChatFormatting.GRAY));
        }
        graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private void renderReturnStatus(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // 按钮没有渲染（如 sea_teleport 关闭）时不显示状态文字，避免信息冗余
        if (returnButton == null) {
            return;
        }
        // 返回状态文字
        if (!canReturn()) {
            String reason = getReturnBlockReason();
            if (!reason.isEmpty()) {
                graphics.drawCenteredString(font,
                        Component.literal(reason).withStyle(ChatFormatting.RED),
                        width / 2, height - 50, 0xFFFF5555);
            }
        }
    }

    private void updateReturnButton() {
        boolean canRet = canReturn();
        if (returnButton != null) {
            returnButton.active = canRet;
        }
    }

    private boolean canReturn() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || cachedArrivalPos == null) {
            return false;
        }
        // sea_teleport 关闭：海图不提供返航（服务端亦会拒绝，这里只是别让按钮亮着骗人）
        if (!data.teleportAllowed()) {
            return false;
        }
        // 必须在搜索区（出门探索状态）
        if (!SixtySecondsSearchZonesClient.isInSearchZone()) {
            return false;
        }
        // 必须在登岛点附近（16 格内）
        double dx = minecraft.player.getX() - cachedArrivalPos.getX();
        double dz = minecraft.player.getZ() - cachedArrivalPos.getZ();
        if (dx * dx + dz * dz > 16 * 16) {
            return false;
        }
        // 必须脱战
        if (SixtySecondsCombatClient.isInCombat()) {
            return false;
        }
        return true;
    }

    private String getReturnBlockReason() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return "";
        }
        if (!data.teleportAllowed()) {
            return Component.translatable(LANG + "return_teleport_disabled").getString();
        }
        if (!SixtySecondsSearchZonesClient.isInSearchZone()) {
            return Component.translatable(LANG + "return_need_explore").getString();
        }
        if (cachedArrivalPos != null) {
            double dx = minecraft.player.getX() - cachedArrivalPos.getX();
            double dz = minecraft.player.getZ() - cachedArrivalPos.getZ();
            if (dx * dx + dz * dz > 16 * 16) {
                return Component.translatable(LANG + "return_need_nearby").getString();
            }
        }
        if (SixtySecondsCombatClient.isInCombat()) {
            return Component.translatable(LANG + "return_in_combat").getString();
        }
        return "";
    }

    private static int darken(int argb) {
        int r = (argb >> 16 & 0xFF) * 7 / 10;
        int g = (argb >> 8 & 0xFF) * 7 / 10;
        int b = (argb & 0xFF) * 7 / 10;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void drawCircle(GuiGraphics graphics, int cx, int cy, int r, int color) {
        // 大圈降采样
        int step = r > 100 ? 2 : 1;
        for (int dx = -r; dx <= r; dx += step) {
            for (int dy = -r; dy <= r; dy += step) {
                if (dx * dx + dy * dy <= r * r) {
                    int px = cx + dx;
                    int py = cy + dy;
                    if (px >= 0 && px < width && py >= 0 && py < height) {
                        graphics.fill(px, py, px + step, py + step, color);
                    }
                }
            }
        }
    }

    private void drawCircleBorder(GuiGraphics graphics, int cx, int cy, int r, int color) {
        int prevX = cx + r;
        int prevY = cy;
        for (int angle = 1; angle <= 360; angle += 4) {
            double rad = Math.toRadians(angle);
            int px = cx + (int) (Math.cos(rad) * r);
            int py = cy + (int) (Math.sin(rad) * r);
            if (px >= 0 && px < width && py >= 0 && py < height) {
                graphics.fill(px - 1, py - 1, px + 1, py + 1, color);
            }
        }
    }

    // ── 交互 ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIsland >= 0) {
            SixtySecondsSeaChartS2CPacket.Entry entry = byId(hoveredIsland);
            Minecraft minecraft = Minecraft.getInstance();
            if (entry != null && minecraft.player != null) {
                if (!entry.unlocked()) {
                    minecraft.player.displayClientMessage(
                            Component.translatable(LANG + "chart_locked_hint").withStyle(ChatFormatting.GRAY), true);
                } else if (!data.teleportAllowed()) {
                    // sea_teleport 关闭：岛照常显示，但只能自己开船过去
                    minecraft.player.displayClientMessage(
                            Component.translatable(LANG + "sail_teleport_disabled").withStyle(ChatFormatting.RED),
                            true);
                } else {
                    minecraft.player.connection.sendCommand("sre:60s island sail " + entry.id());
                    onClose();
                }
                return true;
            }
        }
        if (button == 0) {
            // 开始拖拽
            dragging = true;
            dragMouseX = (int) mouseX;
            dragMouseY = (int) mouseY;
            dragStartX = viewCenterX;
            dragStartZ = viewCenterZ;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            viewCenterX = dragStartX - (mouseX - dragMouseX) / scale;
            viewCenterZ = dragStartZ - (mouseY - dragMouseY) / scale;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double oldScale = scale;
        double zoomFactor = 1.0 + scrollY * 0.15;
        scale = Mth.clamp(scale * zoomFactor, 0.1, 8.0);

        // 以鼠标位置为中心缩放
        if (scale != oldScale) {
            double worldX = toWorldX((int) mouseX);
            double worldZ = toWorldZ((int) mouseY);
            double newWorldX = (mouseX - width / 2.0) / scale + viewCenterX;
            double newWorldZ = (mouseY - height / 2.0) / scale + viewCenterZ;
            viewCenterX += worldX - newWorldX;
            viewCenterZ += worldZ - newWorldZ;
        }
        return true;
    }

    // ── 键盘快捷键 ────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // R 键：重置视图（居中）
        if (keyCode == 82) { // R key
            viewCenterX = (worldMinX + worldMaxX) / 2.0;
            viewCenterZ = (worldMinZ + worldMaxZ) / 2.0;
            double spanX = worldMaxX - worldMinX;
            double spanZ = worldMaxZ - worldMinZ;
            scale = Math.min((double) width / spanX, (double) height / spanZ);
            return true;
        }
        // P 键：回到玩家位置
        if (keyCode == 80) { // P key
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                viewCenterX = minecraft.player.getX();
                viewCenterZ = minecraft.player.getZ();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────

    private SixtySecondsSeaChartS2CPacket.Entry byId(int id) {
        for (SixtySecondsSeaChartS2CPacket.Entry entry : data.islands()) {
            if (entry.id() == id) {
                return entry;
            }
        }
        return null;
    }

    private SixtySecondsIsland toIsland(SixtySecondsSeaChartS2CPacket.Entry entry) {
        SixtySecondsIsland island = new SixtySecondsIsland();
        island.id = entry.id();
        island.level = entry.level();
        island.seed = entry.seed();
        island.centerX = entry.centerX();
        island.centerZ = entry.centerZ();
        island.seaY = data.seaY();
        island.radius = entry.radius();
        return island;
    }

    private Component islandName(SixtySecondsSeaChartS2CPacket.Entry entry) {
        return Component.translatable(LANG + "name_prefix." + entry.namePrefix())
                .append(Component.translatable(LANG + "name_suffix." + entry.nameSuffix()));
    }

    private static ChatFormatting levelFormatting(int level) {
        return level >= 4 ? ChatFormatting.RED : level >= 3 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
