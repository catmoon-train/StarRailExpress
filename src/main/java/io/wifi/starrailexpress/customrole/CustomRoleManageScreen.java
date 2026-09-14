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

package io.wifi.starrailexpress.customrole;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CustomRoleManageScreen extends Screen {

    private static final int PREF_PANEL_WIDTH = 420;
    private static final int PREF_PANEL_HEIGHT = 380;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int PREF_VISIBLE_ROWS = 12;

    /** 实际面板尺寸（小窗口下会被裁进窗口）。 */
    private int panelW;
    private int panelH;
    /** 实际可见行数（由面板高度算出）。 */
    private int visibleRows;
    /** 列表行布局（由面板宽度算出）。 */
    private int nameW;
    private int delX;
    private int infoX;
    private int panelLeftX, panelTopY;
    private final java.util.function.Supplier<Screen> backScreenSupplier;
    private List<CustomRoleData> roles = new ArrayList<>();

    // 滚动状态
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public CustomRoleManageScreen(java.util.function.Supplier<Screen> backSupplier) {
        super(Component.translatable("sre.custom_role.manage.title"));
        this.backScreenSupplier = backSupplier;
    }

    public CustomRoleManageScreen(Screen backScreen) {
        super(Component.translatable("sre.custom_role.manage.title"));
        this.backScreenSupplier = () -> backScreen;
    }

    @Override
    protected void init() {
        // 小窗口下面板跟着窗口收缩（列表靠滚动查看），保证面板不超出显示区域
        panelW = Math.min(PREF_PANEL_WIDTH, Math.max(240, width - 8));
        panelH = Math.min(PREF_PANEL_HEIGHT, Math.max(140, height - 8));
        panelLeftX = (width - panelW) / 2;
        panelTopY = (height - panelH) / 2;
        visibleRows = Math.max(2, Math.min(PREF_VISIBLE_ROWS, (panelH - 68) / ROW_HEIGHT));
        nameW = Math.min(200, Math.max(80, panelW - 60));
        delX = panelLeftX + 10 + nameW + 8;
        infoX = delX + 38;

        CustomRoleConfig config = CustomRoleConfig.loadPreferWorldPath(minecraft.getSingleplayerServer());
        roles = config.roles;

        // 计算最大滚动量
        maxScroll = Math.max(0, (roles.size() - visibleRows) * ROW_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(visibleRows + 1, roles.size() - startIdx); // +1 防半行

        for (int i = 0; i < visibleCount; i++) {
            final int idx = startIdx + i;
            if (idx >= roles.size()) break;
            CustomRoleData role = roles.get(idx);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);

            String displayText = role.englishId + (role.displayName.isEmpty() ? "" : " (" + role.displayName + ")");
            Button nameBtn = Button.builder(
                Component.literal(displayText), b -> {
                    minecraft.setScreen(new CustomRoleScreen(role));
                }).bounds(panelLeftX + 10, y, nameW, 20).build();
            addRenderableWidget(nameBtn);

            Button delBtn = Button.builder(Component.literal("X"), b -> {
                config.roles.remove(idx);
                config.savePreferWorldPath(minecraft.getSingleplayerServer());
                var server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> { try { io.wifi.starrailexpress.customrole.CustomRoleLoader.reload(server); } catch (Exception ignored) {} });
                }
                init(minecraft, width, height);
            }).bounds(panelLeftX + delX, y, 30, 20).build();
            addRenderableWidget(delBtn);
        }

        Button backBtn = Button.builder(Component.translatable("sre.custom_role.back"), b -> {
            minecraft.setScreen(backScreenSupplier.get());
        }).bounds(panelLeftX + 10, panelTopY + panelH - 28, 80, 20).build();
        addRenderableWidget(backBtn);

        Button newBtn = Button.builder(Component.translatable("sre.custom_role.new"), b -> {
            minecraft.setScreen(new CustomRoleScreen());
        }).bounds(panelLeftX + 100, panelTopY + panelH - 28, 80, 20).build();
        addRenderableWidget(newBtn);
    }

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
        SREPanelStyle.drawPanel(g, panelLeftX - 6, panelTopY - 3, panelW + 12, panelH + 6);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = panelLeftX + panelW / 2;
        g.drawCenteredString(font,
            Component.translatable("sre.custom_role.manage.title")
                .withStyle(s -> s.withColor(0xFFD4AF37).withBold(true)),
            cx, panelTopY + 10, 0xFFFFFF);

        // 裁剪内容区域，防止行溢出到面板外
        int listTop = panelTopY + 32;
        int listBottom = panelTopY + panelH - 34;
        g.enableScissor(panelLeftX + 8, listTop, panelLeftX + panelW - 16, listBottom);

        // Draw role info (color preview & type)
        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(visibleRows + 1, roles.size() - startIdx);
        for (int i = 0; i < visibleCount; i++) {
            int idx = startIdx + i;
            if (idx >= roles.size()) break;
            CustomRoleData role = roles.get(idx);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);
            int color = 0xFF000000 | (role.colorR << 16) | (role.colorG << 8) | role.colorB;
            String info = (role.isInnocent ? "[平民]" : (role.canUseKiller ? "[杀手]" : "[中立]"))
                + " " + role.moodType + " max:" + role.maxCount;
            g.drawString(font, Component.literal(info).withStyle(Style.EMPTY.withColor(color)),
                panelLeftX + 260, y + 4, 0xFFFFFF, false);
        }

        g.disableScissor();

        // 滚动条
        if (maxScroll > 0) {
            renderVScrollbar(g, mouseX, mouseY);
        }

        if (roles.isEmpty()) {
            g.drawCenteredString(font,
                Component.translatable("sre.custom_role.manage.empty")
                    .withStyle(s -> s.withColor(0xFF9E8B6E)),
                cx, panelTopY + panelH / 2, 0xFFFFFF);
        }
    }

    private void renderVScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + panelW - 12;
        int sbY = panelTopY + 34;
        int sbH = panelH - 68; // 减去顶部标题和底部按钮区域
        // 轨道背景        // 滑块
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hl = isInRect(mouseX, mouseY, sbX, thumbY, SCROLL_W, thumbH);
        SREPanelStyle.drawScrollbar(g, sbX, sbY, sbH, thumbY, thumbH, hl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= panelLeftX && mx < panelLeftX + panelW
                && my >= panelTopY + 32 && my < panelTopY + panelH - 34
                && maxScroll > 0) {
            scrollOffset = Mth.clamp(
                    (int) (scrollOffset - scrollY * ROW_HEIGHT),
                    0, maxScroll);
            init(minecraft, width, height);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private static boolean isInRect(int mx, int my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
