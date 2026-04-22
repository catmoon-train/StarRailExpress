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
    // 偏移量（静态：同一会话跨实例保持；文件：跨会话持久）
    // ══════════════════════════════════════════════════════════════════

    private static double offsetX = 0;
    private static double offsetY = 0;
    private static double offsetZ = 0;

    /** config/sre_map_offset.txt — 存 "dx,dy,dz" */
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

    /** 当前激活的 tab：0=Positions 1=Areas 2=Settings */
    private int activeTab = 0;

    private EditBox dxBox;
    private EditBox dyBox;
    private EditBox dzBox;

    /** 各 tab 的 widget 列表，用于统一切换 visible */
    private final List<AbstractWidget> tabWidgets0 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets1 = new ArrayList<>();
    private final List<AbstractWidget> tabWidgets2 = new ArrayList<>();

    // ══════════════════════════════════════════════════════════════════
    // 构造
    // ══════════════════════════════════════════════════════════════════

    public MapBuildHelperScreen(BlockPos position) {
        super(Component.literal("Map Build Helper"));
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
    // 命令发送
    // ══════════════════════════════════════════════════════════════════

    private void send(String cmd) {
        var p = Minecraft.getInstance().player;
        if (p != null)
            p.connection.sendCommand(cmd);
    }

    // ══════════════════════════════════════════════════════════════════
    // init
    // ══════════════════════════════════════════════════════════════════

    @Override
    protected void init() {
        tabWidgets0.clear();
        tabWidgets1.clear();
        tabWidgets2.clear();

        final int cx = width / 2;
        final int px = cx - 150; // 面板左边，宽度固定 300
        final int bw = 145; // 按钮宽度（各半）
        final int bh = 16; // 按钮高度
        final int gap = 3; // 行间距
        final int cy = 84; // 内容区起始 Y

        // ── 偏移量输入行 ──────────────────────────────────────────────
        buildOffsetRow(px);

        // ── Tab 栏 ────────────────────────────────────────────────────
        buildTabBar(px, cx);

        // ── Tab 0: Positions ─────────────────────────────────────────
        addTabWidget(tabWidgets0, ModernButton.builder(
                Component.literal("Set Spawn Pos"),
                b -> send(String.format("sre:area_manager set spawnPos %.4f %.4f %.4f %.4f %.4f",
                        ax(), ay(), az(), playerYaw(), playerPitch())))
                .bounds(px, cy, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build());

        addTabWidget(tabWidgets0, ModernButton.builder(
                Component.literal("Set Spectator Spawn"),
                b -> send(String.format("sre:area_manager set spectatorSpawnPos %.4f %.4f %.4f %.4f %.4f",
                        ax(), ay(), az(), playerYaw(), playerPitch())))
                .bounds(px + bw + gap, cy, bw, bh)
                .accentBar(AccentSide.LEFT)
                .build());

        addTabWidget(tabWidgets0, ModernButton.builder(
                Component.literal("Set Play Area Offset"),
                b -> send(String.format("sre:area_manager set playAreaOffset %.4f %.4f %.4f",
                        ax(), ay(), az())))
                .bounds(px, cy + bh + gap, bw * 2 + gap, bh)
                .accentBar(AccentSide.BOTTOM)
                .build());

        // ── Tab 1: Areas ─────────────────────────────────────────────
        final String[][] areas = {
                { "readyArea", "Ready Area" },
                { "playArea", "Play Area" },
                { "sceneArea", "Scene Area" },
                { "resetTemplateArea", "Reset Template" },
                { "resetPasteArea", "Reset Paste" },
        };
        for (int i = 0; i < areas.length; i++) {
            final String aCmd = areas[i][0];
            final String aLabel = areas[i][1];
            final int rowY = cy + i * (bh + gap);

            addTabWidget(tabWidgets1, ModernButton.builder(
                    Component.literal(aLabel + "  [Min]"),
                    b -> send(String.format("sre:area_manager set %s set min %.4f %.4f %.4f",
                            aCmd, ax(), ay(), az())))
                    .bounds(px, rowY, bw, bh)
                    .accentBar(AccentSide.LEFT)
                    .build());

            addTabWidget(tabWidgets1, ModernButton.builder(
                    Component.literal(aLabel + "  [Max]"),
                    b -> send(String.format("sre:area_manager set %s set max %.4f %.4f %.4f",
                            aCmd, ax(), ay(), az())))
                    .bounds(px + bw + gap, rowY, bw, bh)
                    .accentBar(AccentSide.RIGHT)
                    .build());
        }

        // ── Tab 2: Settings ──────────────────────────────────────────
        final String[] boolFields = {
                "canJump", "canSwim", "noReset",
                "haveOutsideSound", "sceneOffsetEnabled", "mustCopy"
        };
        for (int i = 0; i < boolFields.length; i++) {
            final String field = boolFields[i];
            final int rowY = cy + i * (bh + gap);

            addTabWidget(tabWidgets2, ModernButton.builder(
                    Component.literal("✔  " + field), b -> send("sre:area_manager set " + field + " true"))
                    .bounds(px, rowY, bw, bh)
                    .accentBar(AccentSide.LEFT)
                    .build());

            addTabWidget(tabWidgets2, ModernButton.builder(
                    Component.literal("✘  " + field), b -> send("sre:area_manager set " + field + " false"))
                    .bounds(px + bw + gap, rowY, bw, bh)
                    .accentBar(AccentSide.RIGHT)
                    .build());
        }

        // 注册所有 tab widget，然后设置初始可见性
        tabWidgets0.forEach(this::addRenderableWidget);
        tabWidgets1.forEach(this::addRenderableWidget);
        tabWidgets2.forEach(this::addRenderableWidget);
        syncTabVisibility();
    }

    // ── 构建偏移量行 ──────────────────────────────────────────────────

    private void buildOffsetRow(int px) {
        // 布局：[ΔX: 输入框] [ΔY: 输入框] [ΔZ: 输入框] [✕]
        // 标签宽 16，字段宽 52，间距 8
        final int oy = 37;
        final int fh = 14;
        final int fw = 52;
        final int lw = 17;
        final int seg = fw + lw + 6; // 每组（标签+字段+间隙）的宽度

        dxBox = makeField(px + lw, oy, fw, fh, "0",
                v -> {
                    try {
                        offsetX = Double.parseDouble(v);
                        saveOffset();
                    } catch (Exception ignored) {
                    }
                });
        dyBox = makeField(px + lw + seg, oy, fw, fh, "0",
                v -> {
                    try {
                        offsetY = Double.parseDouble(v);
                        saveOffset();
                    } catch (Exception ignored) {
                    }
                });
        dzBox = makeField(px + lw + seg * 2, oy, fw, fh, "0",
                v -> {
                    try {
                        offsetZ = Double.parseDouble(v);
                        saveOffset();
                    } catch (Exception ignored) {
                    }
                });

        dxBox.setValue(fmtDouble(offsetX));
        dyBox.setValue(fmtDouble(offsetY));
        dzBox.setValue(fmtDouble(offsetZ));

        addRenderableWidget(dxBox);
        addRenderableWidget(dyBox);
        addRenderableWidget(dzBox);

        // 重置按钮
        addRenderableWidget(ModernButton.builder(Component.literal("✕ Reset"), b -> {
            offsetX = 0;
            offsetY = 0;
            offsetZ = 0;
            dxBox.setValue("0");
            dyBox.setValue("0");
            dzBox.setValue("0");
            saveOffset();
        }).bounds(px + lw + seg * 3 - 4, oy, 52, fh)
                .accentBar(AccentSide.BOTTOM)
                .build());
    }

    // ── 构建 Tab 栏 ────────────────────────────────────────────────────

    private void buildTabBar(int px, int cx) {
        final int tabY = 57;
        final int tabH = 16;
        final int tabW = 98;
        final int tabG = 3;

        String[] labels = { "Positions", "Areas", "Settings" };
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            var builder = ModernButton.builder(Component.literal(labels[i]), b -> {
                activeTab = idx;
                rebuildWidgets();
            }).bounds(px + i * (tabW + tabG), tabY, tabW, tabH);

            // 激活 tab 底部高亮
            if (activeTab == i)
                builder.accentBar(AccentSide.BOTTOM);
            else
                builder.accentBar();

            addRenderableWidget(builder.build());
        }
    }

    // ── 辅助 ──────────────────────────────────────────────────────────

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

    /** 格式化 double：整数时不显示小数点，否则最多保留 4 位 */
    private static String fmtDouble(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e9)
            return String.valueOf((long) v);
        // 去除尾部多余的 0
        String s = String.format("%.4f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    // ══════════════════════════════════════════════════════════════════
    // 渲染
    // ══════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        final int cx = width / 2;
        final int px = cx - 150;
        final int pw = 300;

        // ── 面板背景 ────────────────────────────────────────────────
        g.fill(px - 6, 3, px + pw + 6, height - 3, 0xCC080C18);
        // 顶部强调线
        g.fill(px - 6, 3, px + pw + 6, 4, 0xFF5577CC);
        // 偏移行底部分隔线
        g.fill(px, 54, px + pw, 55, 0x33AABBCC);
        // Tab 栏底部分隔线
        g.fill(px, 75, px + pw, 76, 0x33AABBCC);

        // ── 标题 ─────────────────────────────────────────────────────
        g.drawCenteredString(font,
                Component.literal("Map Build Helper").withStyle(s -> s.withColor(0x55BBFF).withBold(true)),
                cx, 7, 0xFFFFFF);

        // ── 原始坐标 ─────────────────────────────────────────────────
        g.drawCenteredString(font,
                Component.literal(String.format(
                        "Source  [%d, %d, %d]",
                        position.getX(), position.getY(), position.getZ()))
                        .withStyle(s -> s.withColor(0x778899)),
                cx, 18, 0xFFFFFF);

        // ── 应用偏移后坐标 ───────────────────────────────────────────
        boolean hasOffset = offsetX != 0 || offsetY != 0 || offsetZ != 0;
        g.drawCenteredString(font,
                Component.literal(String.format(
                        "Applied  [%.2f, %.2f, %.2f]", ax(), ay(), az()))
                        .withStyle(s -> s.withColor(hasOffset ? 0x55DD88 : 0x445566)),
                cx, 27, 0xFFFFFF);

        // ── 偏移量标签 ───────────────────────────────────────────────
        final int oy = 37;
        final int lw = 17;
        final int fw = 52;
        final int seg = fw + lw + 6;
        drawLabel(g, "ΔX", px, oy + 3, 0xAABBCC);
        drawLabel(g, "ΔY", px + seg, oy + 3, 0xAABBCC);
        drawLabel(g, "ΔZ", px + seg * 2, oy + 3, 0xAABBCC);

        // ── Tab 内容区标题 ───────────────────────────────────────────
        String[] tabTitles = { "Spawn / Offset", "AABB Areas", "Boolean Settings" };
        g.drawString(font,
                Component.literal("▌ " + tabTitles[activeTab])
                        .withStyle(Style.EMPTY.withColor(0x5577CC).withBold(true)),
                px, 78, 0xFFFFFF, false);

        // ── Applied 坐标对应行（Areas tab 中提示用）──────────────────
        if (activeTab == 1) {
            g.drawString(font,
                    Component.literal(String.format(
                            "Pos: %.1f, %.1f, %.1f  (incl. offset)",
                            ax(), ay(), az()))
                            .withStyle(s -> s.withColor(0x445566)),
                    px + 2, height - 12, 0xFFFFFF, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawLabel(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    // ══════════════════════════════════════════════════════════════════
    // 杂项
    // ══════════════════════════════════════════════════════════════════

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