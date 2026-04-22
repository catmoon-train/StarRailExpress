package io.wifi.starrailexpress.client.gui.screen.map_dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MapBuildHelperScreen extends Screen {

    // ══════════════════════════════════════════════════════════════════
    // 偏移量（静态 + 文件持久化）
    // ══════════════════════════════════════════════════════════════════

    private static double offsetX = 0;
    private static double offsetY = 0;
    private static double offsetZ = 0;

    private static final Path OFFSET_FILE = Path.of("config/sre_map_offset.txt");

    private static void loadOffset() {
        try {
            if (!Files.exists(OFFSET_FILE))
                return;
            String[] p = Files.readString(OFFSET_FILE).trim().split(",");
            if (p.length >= 3) {
                offsetX = Double.parseDouble(p[0].trim());
                offsetY = Double.parseDouble(p[1].trim());
                offsetZ = Double.parseDouble(p[2].trim());
            }
        } catch (Exception ignored) {
        }
    }

    private static void saveOffset() {
        try {
            Files.createDirectories(OFFSET_FILE.getParent());
            Files.writeString(OFFSET_FILE, offsetX + "," + offsetY + "," + offsetZ);
        } catch (IOException ignored) {
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 实例字段
    // ══════════════════════════════════════════════════════════════════

    private final BlockPos position;
    private int activeTab = 0;

    private EditBox dxBox, dyBox, dzBox;
    private final List<AbstractWidget> tabWidgets0 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets1 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets2 = new ArrayList<>();

    // 面板居中定位
    private int panelLeftX;
    private int panelTopY;
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 320;

    // ══════════════════════════════════════════════════════════════════
    // 构造
    // ══════════════════════════════════════════════════════════════════

    public MapBuildHelperScreen(BlockPos position) {
        super(Component.translatable("sre.map_helper.title"));
        this.position = position;
        loadOffset();
    }

    // ══════════════════════════════════════════════════════════════════
    // 坐标计算
    // ══════════════════════════════════════════════════════════════════

    private double ax() {
        return position.getX() + offsetX;
    }

    private double ay() {
        return position.getY() + offsetY;
    }

    private double az() {
        return position.getZ() + offsetZ;
    }

    private float playerYaw() {
        var p = Minecraft.getInstance().player;
        return p != null ? p.getYRot() : 0f;
    }

    private float playerPitch() {
        var p = Minecraft.getInstance().player;
        return p != null ? p.getXRot() : 0f;
    }

    // ══════════════════════════════════════════════════════════════════
    // 命令发送（可选是否关闭界面）
    // ══════════════════════════════════════════════════════════════════

    private void sendAndClose(String cmd) {
        var p = Minecraft.getInstance().player;
        if (p != null)
            p.connection.sendCommand(cmd);
        onClose();
    }

    private void sendOnly(String cmd) {
        var p = Minecraft.getInstance().player;
        if (p != null)
            p.connection.sendCommand(cmd);
    }

    // ══════════════════════════════════════════════════════════════════
    // init – 全部 UI 构建
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        tabWidgets0.clear();
        tabWidgets1.clear();
        tabWidgets2.clear();

        final int bw = 158;
        final int gap = 12;
        final int bh = 22;

        panelLeftX = (width - PANEL_WIDTH) / 2;
        panelTopY = (height - PANEL_HEIGHT) / 2;

        buildOffsetRow();
        buildTabBar();

        final int cy = panelTopY + 114;

        // ---------- Tab 0: Positions ----------
        addTabWidget(tabWidgets0, ModernButton.builder(
                Component.translatable("sre.map_helper.set_spawn"),
                b -> sendAndClose(String.format("sre:area_manager set spawnPos %.4f %.4f %.4f %.4f %.4f",
                        ax(), ay(), az(), playerYaw(), playerPitch())))
                .bounds(panelLeftX + 6, cy, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build());

        addTabWidget(tabWidgets0, ModernButton.builder(
                Component.translatable("sre.map_helper.set_spectator_spawn"),
                b -> sendAndClose(String.format("sre:area_manager set spectatorSpawnPos %.4f %.4f %.4f %.4f %.4f",
                        ax(), ay(), az(), playerYaw(), playerPitch())))
                .bounds(panelLeftX + bw + gap, cy, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build());

        // ---------- Tab 1: Areas ----------
        String[] areaKeys = { "readyArea", "playArea", "sceneArea", "resetTemplateArea", "resetPasteArea" };
        for (int i = 0; i < areaKeys.length; i++) {
            final String cmd = areaKeys[i];
            final Component areaName = Component.translatable("sre.area." + cmd);
            final int rowY = cy + i * (bh + gap);

            addTabWidget(tabWidgets1, ModernButton.builder(
                    Component.translatable("sre.map_helper.area.set_min", areaName),
                    b -> sendAndClose(
                            String.format("sre:area_manager set %s set min %.4f %.4f %.4f", cmd, ax(), ay(), az())))
                    .bounds(panelLeftX + 6, rowY, bw, bh)
                    .accentBar(AccentSide.LEFT)
                    .build());

            addTabWidget(tabWidgets1, ModernButton.builder(
                    Component.translatable("sre.map_helper.area.set_max", areaName),
                    b -> sendAndClose(
                            String.format("sre:area_manager set %s set max %.4f %.4f %.4f", cmd, ax(), ay(), az())))
                    .bounds(panelLeftX + 6 + bw + gap, rowY, bw, bh)
                    .accentBar(AccentSide.RIGHT)
                    .build());
        }

        // ---------- Tab 2: Settings ----------
        String[] boolFields = { "canJump", "canSwim", "noReset", "haveOutsideSound", "sceneOffsetEnabled", "mustCopy" };
        String[] boolFieldKeys = {
                "sre.field.canJump",
                "sre.field.canSwim",
                "sre.field.noReset",
                "sre.field.haveOutsideSound",
                "sre.field.sceneOffsetEnabled",
                "sre.field.mustCopy"
        };
        for (int i = 0; i < boolFields.length; i++) {
            final String field = boolFields[i];
            final int rowY = cy + i * (bh + gap);

            addTabWidget(tabWidgets2, ModernButton.builder(
                    Component.translatable("sre.map_helper.set_true", Component.translatable(boolFieldKeys[i])),
                    b -> sendOnly("sre:area_manager set " + field + " true"))
                    .bounds(panelLeftX + 6, rowY, bw, bh)
                    .accentBar(AccentSide.LEFT)
                    .build());

            addTabWidget(tabWidgets2, ModernButton.builder(
                    Component.translatable("sre.map_helper.set_false", Component.translatable(boolFieldKeys[i])),
                    b -> sendOnly("sre:area_manager set " + field + " false"))
                    .bounds(panelLeftX + 6 + bw + gap, rowY, bw, bh)
                    .accentBar(AccentSide.RIGHT)
                    .build());
        }

        tabWidgets0.forEach(this::addRenderableWidget);
        tabWidgets1.forEach(this::addRenderableWidget);
        tabWidgets2.forEach(this::addRenderableWidget);
        syncTabVisibility();
    }

    // ── 偏移量行 ─────────────────────────────────────────────────────
    private void buildOffsetRow() {
        final int oy = panelTopY + 52;
        final int fh = 18;
        final int labelW = 14;
        final int fieldW = 64;
        final int smallGap = 6;
        final int bigGap = 12;
        final int groupW = labelW + smallGap + fieldW;
        final int resetW = 48;
        final int totalW = groupW * 3 + bigGap * 2 + resetW;
        final int startX = panelLeftX + (PANEL_WIDTH - totalW) / 2;

        dxBox = makeField(startX + labelW + smallGap, oy, fieldW, fh, "0",
                v -> {
                    try {
                        offsetX = Double.parseDouble(v);
                        saveOffset();
                    } catch (Exception ignored) {
                    }
                });
        dxBox.setValue(fmtDouble(offsetX));
        addRenderableWidget(dxBox);

        int yStart = startX + groupW + bigGap;
        dyBox = makeField(yStart + labelW + smallGap, oy, fieldW, fh, "0",
                v -> {
                    try {
                        offsetY = Double.parseDouble(v);
                        saveOffset();
                    } catch (Exception ignored) {
                    }
                });
        dyBox.setValue(fmtDouble(offsetY));
        addRenderableWidget(dyBox);

        int zStart = yStart + groupW + bigGap;
        dzBox = makeField(zStart + labelW + smallGap, oy, fieldW, fh, "0",
                v -> {
                    try {
                        offsetZ = Double.parseDouble(v);
                        saveOffset();
                    } catch (Exception ignored) {
                    }
                });
        dzBox.setValue(fmtDouble(offsetZ));
        addRenderableWidget(dzBox);

        int resetX = zStart + groupW + bigGap;
        addRenderableWidget(ModernButton.builder(Component.translatable("sre.map_helper.reset"), b -> {
            offsetX = offsetY = offsetZ = 0;
            dxBox.setValue("0");
            dyBox.setValue("0");
            dzBox.setValue("0");
            saveOffset();
        }).bounds(resetX, oy, resetW, fh)
                .accentBar(AccentSide.BOTTOM)
                .build());
    }

    // ── Tab 栏 ──────────────────────────────────────────────────────
    private void buildTabBar() {
        final int tabY = panelTopY + 74;
        final int tabH = 22;
        final int tabW = 98;
        final int tabGap = 12;
        final int totalTabW = tabW * 3 + tabGap * 2;
        final int startX = panelLeftX + (PANEL_WIDTH - totalTabW) / 2;

        String[] tabKeys = { "positions", "areas", "settings" };
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            var builder = ModernButton.builder(Component.translatable("sre.map_helper.tab." + tabKeys[i]), b -> {
                activeTab = idx;
                init(minecraft, width, height);
            }).bounds(startX + i * (tabW + tabGap), tabY, tabW, tabH);

            if (activeTab == i)
                builder.accentBar(AccentSide.BOTTOM);
            else
                builder.accentBar();
            addRenderableWidget(builder.build());
        }
    }

    // ── 辅助方法 ────────────────────────────────────────────────────
    private void addTabWidget(List<AbstractWidget> list, AbstractWidget widget) {
        list.add(widget);
    }

    private void syncTabVisibility() {
        tabWidgets0.forEach(w -> w.visible = (activeTab == 0));
        tabWidgets1.forEach(w -> w.visible = (activeTab == 1));
        tabWidgets2.forEach(w -> w.visible = (activeTab == 2));
    }

    private EditBox makeField(int x, int y, int w, int h, String defaultVal, Consumer<String> responder) {
        var box = new EditBox(font, x, y, w, h, Component.empty());
        box.setValue(defaultVal);
        box.setMaxLength(20);
        box.setResponder(responder);
        return box;
    }

    private static String fmtDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e9)
            return String.valueOf((long) v);
        String s = String.format("%.4f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY + PANEL_HEIGHT + 3, 0xCC080C18);
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY - 2, 0xFF5577CC);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        final int cx = panelLeftX + PANEL_WIDTH / 2;

        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.title").withStyle(s -> s.withColor(0x55BBFF).withBold(true)),
                cx, panelTopY + 10, 0xFFFFFF);

        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.source_pos", position.getX(), position.getY(), position.getZ())
                        .withStyle(s -> s.withColor(0x778899)),
                cx, panelTopY + 22, 0xFFFFFF);

        boolean hasOffset = offsetX != 0 || offsetY != 0 || offsetZ != 0;
        g.drawCenteredString(font,
                Component.translatable("sre.map_helper.applied_pos", ax(), ay(), az())
                        .withStyle(s -> s.withColor(hasOffset ? 0x55DD88 : 0x445566)),
                cx, panelTopY + 32, 0xFFFFFF);

        // 偏移量标签
        final int oy = panelTopY + 52;
        final int fh = 18;
        final int labelW = 14;
        final int fieldW = 64;
        final int smallGap = 6;
        final int bigGap = 12;
        int groupW = labelW + smallGap + fieldW;
        int resetW = 48;
        int totalW = groupW * 3 + bigGap * 2 + resetW;
        int startX = panelLeftX + (PANEL_WIDTH - totalW) / 2;

        g.drawString(font, Component.translatable("sre.map_helper.dx"), startX, oy + 4, 0xAABBCC, false);
        int yStart = startX + groupW + bigGap;
        g.drawString(font, Component.translatable("sre.map_helper.dy"), yStart, oy + 4, 0xAABBCC, false);
        int zStart = yStart + groupW + bigGap;
        g.drawString(font, Component.translatable("sre.map_helper.dz"), zStart, oy + 4, 0xAABBCC, false);

        g.fill(panelLeftX, panelTopY + 70, panelLeftX + PANEL_WIDTH, panelTopY + 71, 0x33AABBCC);
        g.fill(panelLeftX, panelTopY + 94, panelLeftX + PANEL_WIDTH, panelTopY + 95, 0x33AABBCC);

        String[] tabTitlesKeys = { "spawn_offset", "aabb_areas", "boolean_settings" };
        g.drawString(font,
                Component.translatable("sre.map_helper.tab_title." + tabTitlesKeys[activeTab])
                        .withStyle(Style.EMPTY.withColor(0x5577CC).withBold(true)),
                panelLeftX + 6, panelTopY + 98, 0xFFFFFF, false);

        if (activeTab == 1) {
            g.drawString(font,
                    Component.translatable("sre.map_helper.areas.hint", ax(), ay(), az())
                            .withStyle(s -> s.withColor(0x445566)),
                    panelLeftX + 6, panelTopY + PANEL_HEIGHT - 12, 0xFFFFFF, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        saveOffset();
        super.onClose();
    }
}