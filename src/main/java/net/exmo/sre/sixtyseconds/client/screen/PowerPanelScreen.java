package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 电力面板（空手/非燃料右键发电机打开）：实时供电倒计时 + 进度条 + 燃料换算表。
 * 剩余时间在打开瞬间快照为「客户端 gameTime 终点」，本地每帧倒计时，无需持续同步。
 * 风格遵循 {@code docs/ui_style.md}。
 */
public class PowerPanelScreen extends Screen {
    private static final int PANEL_TOP = 0xD81A1008;
    private static final int PANEL_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECO = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;
    private static final int RED = 0xFFE06B65;

    private static final int PANEL_W = 250;
    private static final int PANEL_H = 210;
    /** 进度条满刻度：燃料罐一罐的量（450s），仅作可视化参照。 */
    private static final long BAR_FULL_TICKS = SixtySecondsBalance.POWER_PER_FUEL_TICKS * 5L;

    private final long endGameTime;

    public PowerPanelScreen(long remainingTicks) {
        super(Component.translatable("gui.noellesroles.sixty_seconds.power_title"));
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        this.endGameTime = now + Math.max(0, remainingTicks);
    }

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(px + PANEL_W / 2 - 40, py + PANEL_H - 28, 80, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        g.fillGradient(px, py, px + PANEL_W, py + PANEL_H, PANEL_TOP, PANEL_BOTTOM);
        g.renderOutline(px, py, PANEL_W, PANEL_H, BORDER);
        g.fill(px + 1, py + 1, px + PANEL_W - 1, py + 2, DECO);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        int x = px + 14;

        g.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                this.width / 2, py + 12, GOLD);

        long remaining = mc.level == null ? 0 : Math.max(0, endGameTime - mc.level.getGameTime());
        boolean powered = remaining > 0;
        long seconds = remaining / 20;

        // 状态行
        Component status = powered
                ? Component.translatable("gui.noellesroles.sixty_seconds.power_on",
                        String.format("%02d:%02d", seconds / 60, seconds % 60))
                : Component.translatable("gui.noellesroles.sixty_seconds.power_off");
        g.drawString(this.font, status, x, py + 32, powered ? GREEN : RED);

        // 供电进度条（满刻度=一罐燃料 450s）
        int barY = py + 46;
        int barW = PANEL_W - 28;
        g.fill(x, barY, x + barW, barY + 10, 0xFF1B2129);
        int fill = (int) (barW * Mth.clamp(remaining / (double) BAR_FULL_TICKS, 0, 1));
        if (fill > 0) {
            g.fill(x, barY, x + fill, barY + 10, powered ? 0xFFD4AF37 : 0xFF5A4530);
            g.fill(x, barY, x + fill, barY + 1, 0x55FFFFFF);
        }
        for (int i = 1; i < 5; i++) { // 每 90s 一格刻度
            int tx = x + barW * i / 5;
            g.fill(tx, barY, tx + 1, barY + 10, 0x33000000);
        }

        // 供电用途说明
        g.drawString(this.font,
                Component.translatable("gui.noellesroles.sixty_seconds.power_usage"), x, py + 64, MUTED);

        // 燃料换算表
        g.drawString(this.font,
                Component.translatable("gui.noellesroles.sixty_seconds.power_fuel_title")
                        .withStyle(ChatFormatting.BOLD), x, py + 82, GOLD);
        int rowY = py + 96;
        rowY = fuelRow(g, x, rowY, "gui.noellesroles.sixty_seconds.power_fuel_scrap");
        rowY = fuelRow(g, x, rowY, "gui.noellesroles.sixty_seconds.power_fuel_coal");
        rowY = fuelRow(g, x, rowY, "gui.noellesroles.sixty_seconds.power_fuel_newspaper");
        rowY = fuelRow(g, x, rowY, "gui.noellesroles.sixty_seconds.power_fuel_battery");
        rowY = fuelRow(g, x, rowY, "gui.noellesroles.sixty_seconds.power_fuel_can");
        rowY = fuelRow(g, x, rowY, "gui.noellesroles.sixty_seconds.power_fuel_solar");
        rowY = fuelRow(g, x, rowY, "gui.noellesroles.sixty_seconds.power_fuel_amplifier");
        g.drawString(this.font,
                Component.translatable("gui.noellesroles.sixty_seconds.power_hint"), x, rowY + 4, MUTED);
    }

    private int fuelRow(GuiGraphics g, int x, int y, String key) {
        g.drawString(this.font, Component.translatable(key), x + 6, y, TEXT);
        return y + 11;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
