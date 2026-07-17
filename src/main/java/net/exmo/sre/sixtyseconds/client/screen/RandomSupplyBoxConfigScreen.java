package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.network.RandomSupplyBoxConfigSaveC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.*;

/**
 * 随机物资箱配置 GUI（复古车票风，遵循 docs/ui_style.md）：
 * 显示该箱可刷新的所有 loot 类别，每项带勾选框。勾选的类别会在搜刮时随机抽取。
 * 「保存」经 {@link RandomSupplyBoxConfigSaveC2SPacket} 上传服务端落 NBT。
 */
public class RandomSupplyBoxConfigScreen extends Screen {
    // ── ui_style.md 色板 ─────────────────────────────────────────────
    private static final int BG_TOP = 0xD81A1008;
    private static final int BG_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int ACCENT_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int HOVER_BG = 0x22FFFFFF;
    private static final int IDLE_BORDER = 0xFF5A4530;

    private static final int PAD = 8;
    private static final int ROW_H = 24;
    private static final int CHECK_SIZE = 14;

    private final java.util.List<String> allCategories;
    private final java.util.Set<String> enabledCategories;
    private final String tier;
    private final net.minecraft.core.BlockPos pos;
    private int openTicks;
    private int scrollY;

    private int panelX, panelY, panelW, panelH;

    public RandomSupplyBoxConfigScreen(
            net.minecraft.core.BlockPos pos,
            String tier,
            java.util.List<String> allCategories,
            java.util.Set<String> enabledCategories) {
        super(Component.translatable("screen.noellesroles.sixty_seconds.random_supply_box_config.title"));
        this.pos = pos;
        this.tier = tier;
        this.allCategories = new ArrayList<>(allCategories);
        this.enabledCategories = new LinkedHashSet<>(enabledCategories);
    }

    // ── 布局 ─────────────────────────────────────────────────────────

    private void computeLayout() {
        panelW = 300;
        panelH = Math.min(this.height - 40, allCategories.size() * ROW_H + 80);
        panelH = Mth.clamp(panelH, 180, 380);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;
    }

    @Override
    protected void init() {
        computeLayout();
    }

    // ── 渲染 ─────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        openTicks++;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float open = easeOutCubic(Mth.clamp((openTicks + partialTick) / 5f, 0f, 1f));
        g.fill(0, 0, this.width, this.height, ((int) (0x66 * open) << 24));
        g.fillGradient(panelX, panelY, panelX + panelW, panelY + panelH, BG_TOP, BG_BOTTOM);
        g.renderOutline(panelX, panelY, panelW, panelH, BORDER);
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, ACCENT_LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 标题
        g.drawString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                panelX + PAD, panelY + 8, GOLD, false);

        // 等级标签
        String tierLabel = "low".equals(tier)
                ? Component.translatable("screen.noellesroles.sixty_seconds.random_supply_box_config.low_tier")
                        .getString()
                : Component.translatable("screen.noellesroles.sixty_seconds.random_supply_box_config.high_tier")
                        .getString();
        int tierColor = "low".equals(tier) ? 0xFF72C17B : 0xFFE06B65;
        g.drawString(this.font, tierLabel, panelX + panelW - PAD - this.font.width(tierLabel), panelY + 8,
                tierColor, false);

        // 类别列表
        int listTop = panelY + 28;
        int listBottom = panelY + panelH - 36;
        int listH = listBottom - listTop;
        int contentH = allCategories.size() * ROW_H;
        int maxScroll = Math.max(0, contentH - listH);
        scrollY = Mth.clamp(scrollY, 0, maxScroll);

        g.enableScissor(panelX + PAD, listTop, panelX + panelW - PAD, listBottom);

        for (int i = 0; i < allCategories.size(); i++) {
            int y = listTop + i * ROW_H - scrollY;
            if (y + ROW_H < listTop || y > listBottom) continue;

            String cat = allCategories.get(i);
            boolean checked = enabledCategories.contains(cat);

            int rowX = panelX + PAD + 2;

            // hover 背景
            if (isInRect(mouseX, mouseY, rowX, y, panelW - PAD * 2 - 4, ROW_H)) {
                g.fill(rowX, y, rowX + panelW - PAD * 2 - 4, y + ROW_H, HOVER_BG);
            }

            // 勾选框
            int checkX = rowX + 4;
            int checkY = y + (ROW_H - CHECK_SIZE) / 2;
            g.fill(checkX, checkY, checkX + CHECK_SIZE, checkY + CHECK_SIZE, 0x55000000);
            g.renderOutline(checkX, checkY, CHECK_SIZE, CHECK_SIZE, checked ? GOLD : IDLE_BORDER);
            if (checked) {
                // 绘制 ✓
                g.drawString(this.font, "✓", checkX + 2, checkY + 2, GREEN, false);
            }

            // 类别名
            g.drawString(this.font, cat, checkX + CHECK_SIZE + 6, y + (ROW_H - 9) / 2, checked ? TEXT : MUTED, false);
        }

        g.disableScissor();

        // 滚动条
        if (contentH > listH) {
            drawScrollbar(g, panelX + panelW - 6, listTop, listBottom, contentH, scrollY);
        }

        // 底部按钮
        drawBottomButtons(g, mouseX, mouseY);
    }

    private void drawBottomButtons(GuiGraphics g, int mouseX, int mouseY) {
        int y = panelY + panelH - 28;
        // 保存
        int saveX = panelX + panelW / 2 - 70;
        boolean saveHover = isInRect(mouseX, mouseY, saveX, y, 64, 18);
        g.fill(saveX, y, saveX + 64, y + 18,
                saveHover ? blendColors(0xFF1A1008, GOLD, 0.35F) : 0x551A1008);
        g.renderOutline(saveX, y, 64, 18, saveHover ? GOLD : BORDER);
        g.drawCenteredString(this.font,
                Component.translatable("screen.noellesroles.sixty_seconds.loot_edit.save"),
                saveX + 32, y + 5, GOLD);

        // 完成
        int doneX = saveX + 72;
        boolean doneHover = isInRect(mouseX, mouseY, doneX, y, 64, 18);
        g.fill(doneX, y, doneX + 64, y + 18, doneHover ? HOVER_BG : 0x551A1008);
        g.renderOutline(doneX, y, 64, 18, doneHover ? GOLD : IDLE_BORDER);
        g.drawCenteredString(this.font, Component.translatable("gui.done"), doneX + 32, y + 5, TEXT);
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int bottom, int contentH, int scrollPx) {
        int viewH = bottom - top;
        if (contentH <= viewH) return;
        int thumbH = Math.max(18, viewH * viewH / contentH);
        int thumbY = top + (viewH - thumbH) * scrollPx / Math.max(1, contentH - viewH);
        g.fill(x, top, x + 3, bottom, 0x33000000);
        g.fill(x, thumbY, x + 3, thumbY + thumbH, GOLD);
    }

    // ── 交互 ─────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;

        int listTop = panelY + 28;
        int listBottom = panelY + panelH - 36;

        // 类别行点击
        if (mouseX >= panelX + PAD && mouseX < panelX + panelW - PAD
                && mouseY >= listTop && mouseY < listBottom) {
            int index = ((int) mouseY - listTop + scrollY) / ROW_H;
            if (index >= 0 && index < allCategories.size()) {
                String cat = allCategories.get(index);
                if (enabledCategories.contains(cat)) {
                    enabledCategories.remove(cat);
                } else {
                    enabledCategories.add(cat);
                }
                clickSound();
                return true;
            }
            return true;
        }

        // 底部按钮
        int by = panelY + panelH - 28;
        int saveX = panelX + panelW / 2 - 70;
        if (isInRect(mouseX, mouseY, saveX, by, 64, 18)) {
            ClientPlayNetworking.send(new RandomSupplyBoxConfigSaveC2SPacket(pos, enabledCategories));
            clickSound();
            return true;
        }
        if (isInRect(mouseX, mouseY, saveX + 72, by, 64, 18)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollY = Mth.clamp(scrollY - (int) (scrollY * ROW_H), 0,
                Math.max(0, allCategories.size() * ROW_H - (panelY + panelH - 36 - panelY - 28)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── 工具 ─────────────────────────────────────────────────────────

    private void clickSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean isInRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float easeOutCubic(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }

    private static int blendColors(int c1, int c2, float t) {
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        return ((int) (a1 + (a2 - a1) * t) << 24) | ((int) (r1 + (r2 - r1) * t) << 16)
                | ((int) (g1 + (g2 - g1) * t) << 8) | (int) (b1 + (b2 - b1) * t);
    }
}
