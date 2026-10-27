package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.specialization.EchoSpecializationPayload;

import java.util.Locale;

/** 阵营内专长选择：两张卡片 + 倒计时 + 确认/默认/返回，遵循 docs/ui_style.md。 */
public final class EchoSpecializationScreen extends Screen {
    private final Screen parent;
    private int x, y, w, h, cardW, cardH, cardY, selected = -1;
    private long openedAt;
    public EchoSpecializationScreen(Screen parent) {
        super(Component.translatable("echo.spec.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        clearWidgets();
        w = Math.min(560, width - 16);
        h = Math.min(260, height - 16);
        x = (width - w) / 2;
        y = (height - h) / 2;
        var view = EchoSpecializationClient.view();
        boolean narrow = w < 420;
        cardW = narrow ? w - 16 : (w - 24) / 2;
        cardH = narrow ? 62 : 92;
        cardY = y + 40;
        if (selected < 0) selected = Math.max(0, EchoSpecializationClient.selectedIndex());
        int by = y + h - 22;
        if (view == null || view.locked()) {
            button(Component.translatable("echo.ui.back"), x + w - 72, by, 64, this::onClose);
            return;
        }
        if (openedAt == 0) { openedAt = System.currentTimeMillis(); minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F)); }
        button(Component.translatable("echo.spec.confirm"), x + 8, by, 88, () -> {
            EchoSpecializationClient.select(selected);
            onClose();
        });
        button(Component.translatable("echo.spec.default"), x + 100, by, 88, () -> {
            int fallback = Math.max(0, EchoSpecializationClient.selectedIndex());
            EchoSpecializationClient.select(fallback);
            onClose();
        });
        button(Component.translatable("echo.ui.back"), x + w - 72, by, 64, this::onClose);
    }

    private void button(Component label, int bx, int by, int bw, Runnable action) {
        var button = new SreButton(label, ignored -> action.run());
        button.setX(bx); button.setY(by); button.setWidth(bw); button.setHeight(18);
        addRenderableWidget(button);
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float p = EchoAnim.openProgress(openedAt, 180);
        g.fillGradient(0, 0, width, height, EchoAnim.fade(0xF018120A, p), EchoAnim.fade(0xF0061018, p));
        g.fill(x, y, x + w, y + h, EchoAnim.fade(0xF01A1008, p));
        g.renderOutline(x, y, w, h, EchoAnim.fade(0xFF8B6914, p));
        g.fill(x + 1, y + 1, x + w - 1, y + 3, EchoAnim.fade(0x33FFE8C0, p));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        var view = EchoSpecializationClient.view();
        float p = EchoAnim.openProgress(openedAt, 180);
        g.drawString(font, title, x + 8, y + 8, EchoAnim.fade(0xFFD4AF37, p), false);
        if (view == null) {
            g.drawString(font, Component.translatable("echo.ui.waiting"), x + 8, y + 30, EchoAnim.fade(0xFF9E8B6E, p), false);
            return;
        }
        var data = EchoBagClient.data();
        long now = data == null ? 0 : data.projectedTick();
        long seconds = Math.max(0, (view.deadline() - now + 19) / 20);
        long remainingMs = Math.max(0, view.deadline() - now) * 50L;
        float urgency = view.locked() ? 0F : EchoAnim.urgency(remainingMs, 5000);
        Component status = view.locked() ? Component.translatable("echo.spec.locked")
                : Component.translatable("echo.spec.timer", seconds);
        int statusColor = view.locked() ? 0xFF9E8B6E : EchoAnim.blend(0xFF5EB7D8, 0xFFE06B65, urgency);
        g.drawString(font, status, x + 8, y + 22, EchoAnim.fade(statusColor, p), false);
        int accent = EchoSpecializationClient.factionColor(view.faction());
        boolean narrow = w < 420;
        for (int n = 0; n < view.options().size(); n++) {
            var option = view.options().get(n);
            int cx = narrow ? x + 8 : x + 8 + n * (cardW + 8);
            int cy = narrow ? cardY + n * (cardH + 6) : cardY;
            boolean active = n == selected;
            boolean hover = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;
            int fill = active ? 0xF03A2A12 : hover ? 0xF0302216 : 0xE0241810;
            int border = active ? 0xFFD4AF37 : hover ? 0xFFC9A84C : 0xFF5A4530;
            g.fill(cx, cy, cx + cardW, cy + cardH, EchoAnim.fade(fill, p));
            g.renderOutline(cx, cy, cardW, cardH, EchoAnim.fade(border, p));
            g.fill(cx + 1, cy + 1, cx + cardW - 1, cy + 3, EchoAnim.fade(accent, p));
            g.drawString(font, Component.translatable(option.nameKey()), cx + 6, cy + 8, EchoAnim.fade(active ? 0xFFD4AF37 : 0xFFFFF4DC, p), false);
            int line = cy + 22;
            for (var part : font.split(Component.translatable(option.descKey()), Math.max(20, cardW - 12))) {
                if (line > cy + cardH - 10) break;
                g.drawString(font, part, cx + 6, line, 0xFFC8B898, false);
                line += 10;
            }
        }
        if (data != null) {
            int py = narrow ? cardY + 2 * (cardH + 6) : cardY + cardH + 8;
            g.drawString(font, Component.translatable("echo.spec.current", data.capacity(),
                    String.format(Locale.ROOT, "%.0f", data.cargoPenalty() * 100)), x + 8, py, 0xFF9E8B6E, false);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var view = EchoSpecializationClient.view();
        if (view != null && !view.locked()) {
            boolean narrow = w < 420;
            for (int n = 0; n < view.options().size(); n++) {
                int cx = narrow ? x + 8 : x + 8 + n * (cardW + 8);
                int cy = narrow ? cardY + n * (cardH + 6) : cardY;
                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                    selected = n; return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void tick() {
        var view = EchoSpecializationClient.view();
        if (view == null || view.match() == null) { onClose(); return; }
        if (EchoBagClient.data() == null) onClose();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
