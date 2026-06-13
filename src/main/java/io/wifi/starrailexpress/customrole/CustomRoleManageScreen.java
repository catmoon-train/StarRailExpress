package io.wifi.starrailexpress.customrole;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton.AccentSide;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class CustomRoleManageScreen extends Screen {

    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 380;
    private static final int SCROLL_W = 7;
    private static final int SCROLL_MIN_THUMB = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 12;
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
        panelLeftX = (width - PANEL_WIDTH) / 2;
        panelTopY = (height - PANEL_HEIGHT) / 2;

        CustomRoleConfig config = CustomRoleConfig.loadPreferWorldPath(minecraft.getSingleplayerServer());
        roles = config.roles;

        // 计算最大滚动量
        maxScroll = Math.max(0, (roles.size() - VISIBLE_ROWS) * ROW_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(VISIBLE_ROWS + 1, roles.size() - startIdx); // +1 防半行

        for (int i = 0; i < visibleCount; i++) {
            final int idx = startIdx + i;
            if (idx >= roles.size()) break;
            CustomRoleData role = roles.get(idx);
            int y = baseY + i * ROW_HEIGHT - (scrollOffset % ROW_HEIGHT);

            String displayText = role.englishId + (role.displayName.isEmpty() ? "" : " (" + role.displayName + ")");
            ModernButton nameBtn = ModernButton.builder(
                Component.literal(displayText), b -> {
                    minecraft.setScreen(new CustomRoleScreen(role));
                }).bounds(panelLeftX + 10, y, 200, 20).accentBar(AccentSide.LEFT).build();
            addRenderableWidget(nameBtn);

            ModernButton delBtn = ModernButton.builder(Component.literal("X"), b -> {
                config.roles.remove(idx);
                config.savePreferWorldPath(minecraft.getSingleplayerServer());
                var server = minecraft.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> { try { io.wifi.starrailexpress.customrole.CustomRoleLoader.reload(server); } catch (Exception ignored) {} });
                }
                init(minecraft, width, height);
            }).bounds(panelLeftX + 220, y, 30, 20).accentBar(AccentSide.RIGHT).build();
            addRenderableWidget(delBtn);
        }

        ModernButton backBtn = ModernButton.builder(Component.translatable("sre.custom_role.back"), b -> {
            minecraft.setScreen(backScreenSupplier.get());
        }).bounds(panelLeftX + 10, panelTopY + PANEL_HEIGHT - 28, 80, 20)
            .accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(backBtn);

        ModernButton newBtn = ModernButton.builder(Component.translatable("sre.custom_role.new"), b -> {
            minecraft.setScreen(new CustomRoleScreen());
        }).bounds(panelLeftX + 100, panelTopY + PANEL_HEIGHT - 28, 80, 20)
            .accentBar(AccentSide.BOTTOM).build();
        addRenderableWidget(newBtn);
    }

    @Override
    public void renderBackground(GuiGraphics g, int i, int j, float f) {
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY + PANEL_HEIGHT + 3, 0xCC080C18);
        g.fill(panelLeftX - 6, panelTopY - 3, panelLeftX + PANEL_WIDTH + 6, panelTopY - 2, 0xFF5577CC);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = panelLeftX + PANEL_WIDTH / 2;
        g.drawCenteredString(font,
            Component.translatable("sre.custom_role.manage.title")
                .withStyle(s -> s.withColor(0x55BBFF).withBold(true)),
            cx, panelTopY + 10, 0xFFFFFF);

        // 裁剪内容区域，防止行溢出到面板外
        int listTop = panelTopY + 32;
        int listBottom = panelTopY + PANEL_HEIGHT - 34;
        g.enableScissor(panelLeftX + 8, listTop, panelLeftX + PANEL_WIDTH - 16, listBottom);

        // Draw role info (color preview & type)
        int baseY = panelTopY + 34;
        int startIdx = scrollOffset / ROW_HEIGHT;
        int visibleCount = Math.min(VISIBLE_ROWS + 1, roles.size() - startIdx);
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
                    .withStyle(s -> s.withColor(0x778899)),
                cx, panelTopY + PANEL_HEIGHT / 2, 0xFFFFFF);
        }
    }

    private void renderVScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int sbX = panelLeftX + PANEL_WIDTH - 12;
        int sbY = panelTopY + 34;
        int sbH = PANEL_HEIGHT - 68; // 减去顶部标题和底部按钮区域
        // 轨道背景
        g.fill(sbX, sbY, sbX + SCROLL_W, sbY + sbH, 0xFF111828);
        g.fill(sbX + 1, sbY + 1, sbX + SCROLL_W - 1, sbY + sbH - 1, 0x55334466);
        // 滑块
        int totalContentH = sbH + maxScroll;
        float ratio = Math.min(1f, (float) sbH / Math.max(1, totalContentH));
        int thumbH = Math.max(SCROLL_MIN_THUMB, (int) (sbH * ratio));
        int thumbY = sbY + (int) ((sbH - thumbH) * ((float) scrollOffset / maxScroll));
        boolean hl = isInRect(mouseX, mouseY, sbX, thumbY, SCROLL_W, thumbH);
        g.fill(sbX, thumbY, sbX + SCROLL_W, thumbY + thumbH,
                hl ? 0xFF8899CC : 0xFF556699);
        g.fill(sbX + 1, thumbY + 1, sbX + SCROLL_W - 1, thumbY + thumbH - 1,
                hl ? 0xFFAABBEE : 0xFF7788BB);
        g.fill(sbX + 1, thumbY + 1, sbX + SCROLL_W - 1, thumbY + 3, 0x44FFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= panelLeftX && mx < panelLeftX + PANEL_WIDTH
                && my >= panelTopY + 32 && my < panelTopY + PANEL_HEIGHT - 34
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
