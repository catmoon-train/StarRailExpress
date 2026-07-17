package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.island.SixtySecondsIsland;
import net.exmo.sre.sixtyseconds.island.SixtySecondsIslandGenerator;
import net.exmo.sre.sixtyseconds.network.SixtySecondsSeaChartS2CPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 海图（魔改区域地图而来的新地图界面）：用与服务端相同的岛屿形状函数
 * （{@link SixtySecondsIslandGenerator#landValue}）按 seed 重画岛屿轮廓——
 * 无需同步任何方块数据。已解锁岛显示名字+危险等级（按等级着色，✓=已踏足），
 * 未解锁岛为迷雾「未知海域」。点击已解锁岛=扬帆前往（发服务端命令）。
 */
public class SeaChartScreen extends Screen {

    private static final String LANG = SixtySecondsIsland.LANG;
    /** 等级 1..5 的岛屿主色。 */
    private static final int[] LEVEL_COLORS = {0xFF59C24A, 0xFF3EC7A0, 0xFFE0C34A, 0xFFE07B39, 0xFFD94040};
    private static final int COLOR_BEACH = 0xFFD8CC9A;
    private static final int COLOR_OCEAN = 0xFF0B2740;
    private static final int COLOR_OCEAN_DEEP = 0xFF081C30;
    private static final int COLOR_FOG = 0xFF25313D;

    private final SixtySecondsSeaChartS2CPacket data;

    private int mapX;
    private int mapY;
    private int mapW;
    private int mapH;
    /** 世界坐标 → 屏幕坐标映射。 */
    private double scale;
    private double worldMinX;
    private double worldMinZ;
    private int hoveredIsland = -1;

    public SeaChartScreen(SixtySecondsSeaChartS2CPacket data) {
        super(Component.translatable(LANG + "chart_title"));
        this.data = data;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {

    }

    @Override
    protected void init() {
        mapW = Math.min(width - 60, 420);
        mapH = Math.min(height - 80, 260);
        mapX = (width - mapW) / 2;
        mapY = (height - mapH) / 2 - 8;
        // 世界范围：所有岛单元格的外包 + 余量
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
        int spanX = Math.max(64, maxX - minX);
        int spanZ = Math.max(64, maxZ - minZ);
        scale = Math.min((double) mapW / spanX, (double) mapH / spanZ);
        worldMinX = minX + (spanX - mapW / scale) / 2.0;
        worldMinZ = minZ + (spanZ - mapH / scale) / 2.0;

        addRenderableWidget(Button.builder(Component.translatable(LANG + "chart_home"), button -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.connection.sendCommand("sre:60s island home");
            }
            onClose();
        }).bounds(mapX, mapY + mapH + 6, 96, 20).build());
        addRenderableWidget(Button.builder(net.minecraft.network.chat.CommonComponents.GUI_DONE,
                button -> onClose()).bounds(mapX + mapW - 96, mapY + mapH + 6, 96, 20).build());
    }

    private int toScreenX(double worldX) {
        return mapX + (int) ((worldX - worldMinX) * scale);
    }

    private int toScreenY(double worldZ) {
        return mapY + (int) ((worldZ - worldMinZ) * scale);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // 图框 + 海面
        graphics.fill(mapX - 3, mapY - 3, mapX + mapW + 3, mapY + mapH + 3, 0xFF3A2E1E);
        graphics.fill(mapX - 1, mapY - 1, mapX + mapW + 1, mapY + mapH + 1, 0xFF6B5636);
        graphics.fill(mapX, mapY, mapX + mapW, mapY + mapH, COLOR_OCEAN);
        // 深海纹理点（确定性散点当波纹）
        for (int i = 0; i < 260; i++) {
            int px = mapX + (i * 97 + (i * i * 13) % 51) % mapW;
            int py = mapY + (i * 61 + (i * i * 7) % 37) % mapH;
            graphics.fill(px, py, px + 2, py + 1, COLOR_OCEAN_DEEP);
        }
        graphics.drawCenteredString(font, title, width / 2, mapY - 16, 0xFFE8D9A8);

        hoveredIsland = -1;
        List<Runnable> labels = new ArrayList<>();
        for (SixtySecondsSeaChartS2CPacket.Entry entry : data.islands()) {
            renderIsland(graphics, entry, mouseX, mouseY, labels);
        }
        // 名字最后画，避免被相邻岛的像素盖住
        for (Runnable label : labels) {
            label.run();
        }
        renderPlayerMarker(graphics);
        // 悬浮提示
        if (hoveredIsland >= 0) {
            SixtySecondsSeaChartS2CPacket.Entry entry = byId(hoveredIsland);
            if (entry != null) {
                List<Component> tooltip = new ArrayList<>();
                if (entry.unlocked()) {
                    tooltip.add(islandName(entry).copy().withStyle(ChatFormatting.WHITE));
                    tooltip.add(Component.translatable(LANG + "level", entry.level())
                            .withStyle(levelFormatting(entry.level())));
                    if (entry.visited()) {
                        tooltip.add(Component.translatable(LANG + "chart_visited")
                                .withStyle(ChatFormatting.GREEN));
                    }
                    tooltip.add(Component.translatable(LANG + "chart_click_sail")
                            .withStyle(ChatFormatting.GRAY));
                } else {
                    tooltip.add(Component.translatable(LANG + "chart_locked")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    tooltip.add(Component.translatable(LANG + "chart_locked_hint")
                            .withStyle(ChatFormatting.GRAY));
                }
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            }
        }
        graphics.drawCenteredString(font, Component.translatable(LANG + "chart_travel_hint")
                .withStyle(ChatFormatting.DARK_GRAY), width / 2, mapY + mapH + 32, 0xFF888888);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderIsland(GuiGraphics graphics, SixtySecondsSeaChartS2CPacket.Entry entry,
            int mouseX, int mouseY, List<Runnable> labels) {
        int cx = toScreenX(entry.centerX());
        int cy = toScreenY(entry.centerZ());
        int screenR = Math.max(4, (int) (entry.radius() * scale));
        boolean hovered = (mouseX - cx) * (mouseX - cx) + (mouseY - cy) * (mouseY - cy)
                <= (screenR + 3) * (screenR + 3);
        if (hovered && mouseX >= mapX && mouseX < mapX + mapW && mouseY >= mapY && mouseY < mapY + mapH) {
            hoveredIsland = entry.id();
        }
        if (!entry.unlocked()) {
            // 迷雾：暗色噪点团 + 「?」
            SixtySecondsIsland island = toIsland(entry);
            rasterize(graphics, island, entry, cx, cy, screenR, true);
            labels.add(() -> graphics.drawCenteredString(font, "?", cx, cy - 4, 0xFF6C7A88));
            return;
        }
        SixtySecondsIsland island = toIsland(entry);
        rasterize(graphics, island, entry, cx, cy, screenR, false);
        int color = LEVEL_COLORS[Mth.clamp(entry.level(), 1, 5) - 1];
        String label = islandName(entry).getString() + " Lv." + entry.level()
                + (entry.visited() ? " ✓" : "");
        labels.add(() -> {
            graphics.drawCenteredString(font, label, cx, cy + screenR + 2, color);
            if (hoveredIsland == entry.id()) { // 悬浮描边
                graphics.fill(cx - screenR - 2, cy - screenR - 2, cx + screenR + 2, cy - screenR - 1, 0xFFFFFFFF);
                graphics.fill(cx - screenR - 2, cy + screenR + 1, cx + screenR + 2, cy + screenR + 2, 0xFFFFFFFF);
                graphics.fill(cx - screenR - 2, cy - screenR - 2, cx - screenR - 1, cy + screenR + 2, 0xFFFFFFFF);
                graphics.fill(cx + screenR + 1, cy - screenR - 2, cx + screenR + 2, cy + screenR + 2, 0xFFFFFFFF);
            }
        });
    }

    /** 用服务端同款形状函数低分辨率栅格化岛屿轮廓（2px 格）。 */
    private void rasterize(GuiGraphics graphics, SixtySecondsIsland island,
            SixtySecondsSeaChartS2CPacket.Entry entry, int cx, int cy, int screenR, boolean fog) {
        int step = 2;
        int mainColor = fog ? COLOR_FOG : LEVEL_COLORS[Mth.clamp(entry.level(), 1, 5) - 1];
        for (int px = cx - screenR; px <= cx + screenR; px += step) {
            for (int py = cy - screenR; py <= cy + screenR; py += step) {
                if (px < mapX || px >= mapX + mapW - step || py < mapY || py >= mapY + mapH - step) {
                    continue;
                }
                double worldX = worldMinX + (px - mapX) / scale;
                double worldZ = worldMinZ + (py - mapY) / scale;
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
                graphics.fill(px, py, px + step, py + step, color);
            }
        }
    }

    private static int darken(int argb) {
        int r = (argb >> 16 & 0xFF) * 7 / 10;
        int g = (argb >> 8 & 0xFF) * 7 / 10;
        int b = (argb & 0xFF) * 7 / 10;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private void renderPlayerMarker(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int px = toScreenX(minecraft.player.getX());
        int py = toScreenY(minecraft.player.getZ());
        if (px < mapX || px >= mapX + mapW || py < mapY || py >= mapY + mapH) {
            return;
        }
        graphics.fill(px - 2, py - 2, px + 2, py + 2, 0xFFFFFFFF);
        graphics.fill(px - 1, py - 1, px + 1, py + 1, 0xFFCC3333);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredIsland >= 0) {
            SixtySecondsSeaChartS2CPacket.Entry entry = byId(hoveredIsland);
            Minecraft minecraft = Minecraft.getInstance();
            if (entry != null && minecraft.player != null) {
                if (entry.unlocked()) {
                    minecraft.player.connection.sendCommand("sre:60s island sail " + entry.id());
                    onClose();
                } else {
                    minecraft.player.displayClientMessage(Component.translatable(LANG + "chart_locked_hint")
                            .withStyle(ChatFormatting.GRAY), true);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private SixtySecondsSeaChartS2CPacket.Entry byId(int id) {
        for (SixtySecondsSeaChartS2CPacket.Entry entry : data.islands()) {
            if (entry.id() == id) {
                return entry;
            }
        }
        return null;
    }

    /** 网络条目 → 形状函数需要的岛屿元数据。 */
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
