package org.agmas.noellesroles.game.modes.echo.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.repair.EchoRepairCheck;
import org.agmas.noellesroles.game.modes.echo.repair.EchoRepairPayload;

/**
 * 修机小游戏：圆环指针 + 判定弧 + 空格/按钮停下。
 *
 * <p>只负责画与提交；判定一律由服务端按自己的 tick 重算（见 {@link EchoRepairCheck}）。
 * 指针位置用本地 {@code System.nanoTime()} 插值，因此低帧率下依然平滑。
 */
public final class EchoRepairScreen extends Screen {
    private static final int RING_RADIUS = 52;
    private static final int RING_THICK = 9;
    private static final int SEGMENTS = 120;

    private final EchoRepairPayload check;
    private final long openedAt = System.nanoTime();
    private boolean submitted;

    public EchoRepairScreen(EchoRepairPayload check) {
        super(Component.translatable("echo.repair.title"));
        this.check = check;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        int cx = width / 2, cy = height / 2 - 6;
        g.fill(cx - 118, cy - 118, cx + 118, cy + 96, 0xD0100B06);
        g.drawCenteredString(font, check.accident()
                        ? Component.translatable("echo.repair.accident_title").withStyle(ChatFormatting.RED)
                        : Component.translatable("echo.repair.title"),
                cx, cy - 106, check.accident() ? 0xFFFF8A80 : 0xFFFFE3A0);

        // 外圈底环
        arc(g, cx, cy, RING_RADIUS, 0xFF33261A, 0F, 1F);
        // 判定弧（绿色；失控时用琥珀色提示更窄的窗口）
        int zoneColor = check.accident() ? 0xFFE0A040 : 0xFF48D17A;
        float from = check.zoneStart() / (float) EchoRepairCheck.FULL_CIRCLE;
        float to = (check.zoneStart() + check.zoneWidth()) / (float) EchoRepairCheck.FULL_CIRCLE;
        arc(g, cx, cy, RING_RADIUS, zoneColor, from, Math.min(1F, to));
        if (to > 1F) arc(g, cx, cy, RING_RADIUS, zoneColor, 0F, to - 1F);

        // 指针
        float progress = position() / (float) EchoRepairCheck.FULL_CIRCLE;
        double angle = -Math.PI / 2 + progress * Math.PI * 2;
        int px = (int) Math.round(cx + Math.cos(angle) * (RING_RADIUS + RING_THICK + 4));
        int py = (int) Math.round(cy + Math.sin(angle) * (RING_RADIUS + RING_THICK + 4));
        g.fill(px - 3, py - 3, px + 3, py + 3, 0xFFFFF176);
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFD4AF37);

        g.drawCenteredString(font, Component.translatable("echo.repair.hint").withStyle(ChatFormatting.GRAY),
                cx, cy + RING_RADIUS + 26, 0xFFB0B8C0);
        if (check.accident())
            g.drawCenteredString(font, Component.translatable("echo.repair.accident_hint")
                    .withStyle(ChatFormatting.RED), cx, cy + RING_RADIUS + 40, 0xFFFF8A80);
        super.render(g, mouseX, mouseY, delta);
    }

    /** 画一段圆弧；用逐段小方块近似，避免依赖额外的缓冲或着色器。 */
    private static void arc(GuiGraphics g, int cx, int cy, int radius, int color, float from, float to) {
        from = Math.max(0F, Math.min(1F, from));
        to = Math.max(from, Math.min(1F, to));
        if (to <= from) return;
        int steps = Math.max(2, Math.round((to - from) * SEGMENTS));
        for (int i = 0; i <= steps; i++) {
            double angle = -Math.PI / 2 + (from + (to - from) * i / steps) * Math.PI * 2;
            int x = (int) Math.round(cx + Math.cos(angle) * radius);
            int y = (int) Math.round(cy + Math.sin(angle) * radius);
            g.fill(x - RING_THICK / 2, y - RING_THICK / 2,
                    x + RING_THICK / 2 + 1, y + RING_THICK / 2 + 1, color);
        }
    }

    /** 本地插值的指针位置（0..999），与服务端按 tick 重算的结果同源同速。 */
    private int position() {
        // 1 tick = 50ms；一圈耗时 = 1000 刻度 / 每 tick 刻度 * 50ms。
        long elapsedMs = (System.nanoTime() - openedAt) / 1_000_000L;
        long lapMs = Math.max(1L, 50L * EchoRepairCheck.FULL_CIRCLE / Math.max(1, check.speed()));
        long within = elapsedMs % lapMs;
        return (int) (within * EchoRepairCheck.FULL_CIRCLE / lapMs);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 空格或回车 = 停下
        if (keyCode == 32 || keyCode == 257) { submit(); return true; }
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { submit(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void submit() {
        if (submitted) return;
        submitted = true;
        EchoRepairClient.submit(check, position());
    }

    @Override public void onClose() {
        if (!submitted) {
            submitted = true;
            EchoRepairClient.cancel(check);
        }
        super.onClose();
    }
}
