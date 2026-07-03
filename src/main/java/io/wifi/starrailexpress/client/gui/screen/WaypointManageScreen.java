package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.client.gui.screen.WaypointHUD.WaypointMarker;
import io.wifi.starrailexpress.network.packet.WaypointDeleteC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;

/**
 * 路径点管理 GUI：列出所有客户端已知路径点（按 path 分组），提供单点删除与整条路径删除。
 * 删除请求经 {@link WaypointDeleteC2SPayload} 发往服务端（限 OP），服务端落库后重广播全量对账。
 */
public class WaypointManageScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int ROW_WIDTH = 360;
    private static final int HEADER_COLOR = 0xFFFFD479;
    private static final int DEL_BTN_W = 56;
    private static final int DEL_BTN_H = 18;

    private static final class Entry {
        final boolean isPathHeader;
        final String path;
        final String name;          // header 为 null
        final WaypointMarker marker; // header 为 null

        Entry(boolean isPathHeader, String path, String name, WaypointMarker marker) {
            this.isPathHeader = isPathHeader;
            this.path = path;
            this.name = name;
            this.marker = marker;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int scrollOffset = 0;
    private int listTop;
    private int listBottom;
    private int visibleRows;

    public WaypointManageScreen() {
        super(Component.literal("路径点管理"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        buildEntries();

        listTop = 40;
        listBottom = this.height - 36;
        visibleRows = Math.max(1, (listBottom - listTop) / ROW_HEIGHT);

        int maxScroll = Math.max(0, entries.size() - visibleRows);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int rowX = this.width / 2 - ROW_WIDTH / 2;
        int btnX = rowX + ROW_WIDTH - DEL_BTN_W;

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= entries.size()) {
                break;
            }
            final Entry e = entries.get(idx);
            int y = listTop + i * ROW_HEIGHT;
            if (e.isPathHeader) {
                addRenderableWidget(Button.builder(Component.literal("删整条"), b -> {
                    ClientPlayNetworking.send(new WaypointDeleteC2SPayload(e.path, "", true));
                    WaypointHUD.removePath(e.path);
                    rebuild();
                }).pos(btnX, y + 2).size(DEL_BTN_W, DEL_BTN_H).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal("删除"), b -> {
                    ClientPlayNetworking.send(new WaypointDeleteC2SPayload(e.path, e.name, false));
                    WaypointHUD.removeWaypoint(e.path, e.name);
                    rebuild();
                }).pos(btnX, y + 2).size(DEL_BTN_W, DEL_BTN_H).build());
            }
        }

        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> this.onClose())
                .pos(this.width / 2 - 50, this.height - 28).size(100, 20).build());
    }

    private void rebuild() {
        this.init();
    }

    private void buildEntries() {
        entries.clear();
        Map<String, List<WaypointMarker>> byPath = new TreeMap<>();
        for (WaypointMarker m : WaypointHUD.getWaypoints()) {
            byPath.computeIfAbsent(m.path, k -> new ArrayList<>()).add(m);
        }
        for (Map.Entry<String, List<WaypointMarker>> ent : byPath.entrySet()) {
            entries.add(new Entry(true, ent.getKey(), null, null));
            ent.getValue().sort(Comparator.comparing(m -> m.name));
            for (WaypointMarker m : ent.getValue()) {
                entries.add(new Entry(false, m.path, m.name, m));
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        if (scrollY > 0 && scrollOffset > 0) {
            scrollOffset--;
            this.init();
            return true;
        }
        if (scrollY < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
            this.init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);

        int rowX = this.width / 2 - ROW_WIDTH / 2;
        if (entries.isEmpty()) {
            g.drawCenteredString(this.font, Component.literal("暂无路径点"), this.width / 2, listTop + 10, 0xFFAAAAAA);
            return;
        }

        for (int i = 0; i < visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= entries.size()) {
                break;
            }
            Entry e = entries.get(idx);
            int y = listTop + i * ROW_HEIGHT;
            g.fill(rowX, y, rowX + ROW_WIDTH, y + ROW_HEIGHT - 2, 0x60000000);
            if (e.isPathHeader) {
                g.drawString(this.font, Component.literal("§l" + e.path + "/"), rowX + 6, y + 7, HEADER_COLOR, false);
            } else {
                int swatch = 0xFF000000 | (e.marker.color.getRGB() & 0xFFFFFF);
                g.fill(rowX + 16, y + 6, rowX + 26, y + 16, swatch);
                String label = e.name + "  (" + e.marker.pos.getX() + ", " + e.marker.pos.getY()
                        + ", " + e.marker.pos.getZ() + ")";
                g.drawString(this.font, label, rowX + 32, y + 7, 0xFFFFFFFF, false);
            }
        }

        int maxScroll = Math.max(0, entries.size() - visibleRows);
        if (maxScroll > 0) {
            String hint = (scrollOffset + 1) + "-" + Math.min(entries.size(), scrollOffset + visibleRows)
                    + " / " + entries.size() + "  （滚轮翻页）";
            g.drawString(this.font, hint, rowX, listTop - 12, 0xFF888888, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
