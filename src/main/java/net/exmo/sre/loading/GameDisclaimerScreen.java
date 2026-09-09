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

package net.exmo.sre.loading;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进入主菜单前的健康 / 医学 / 开源免责声明。
 * 每次启动会话展示一次，必须点击确认后才能继续。
 */
@Environment(EnvType.CLIENT)
public class GameDisclaimerScreen extends Screen {

    private static final AtomicBoolean ACKNOWLEDGED = new AtomicBoolean(false);

    private static final int BODY_COLOR = 0xFFC8B898;
    private static final int TITLE_LINE_H = 14;
    private static final int BODY_LINE_H = 12;
    private static final int SECTION_GAP = 10;

    private final List<Line> lines = new ArrayList<>();
    private GameDisclaimerLayout layout;
    private long openedAt = -1L;
    private float scrollOffset;
    private float maxScroll;
    private boolean draggingScroll;
    private float buttonHover;

    public GameDisclaimerScreen() {
        super(Component.translatable(GameDisclaimerContent.HEADER));
    }

    public static boolean isAcknowledged() {
        return ACKNOWLEDGED.get();
    }

    /** 测试 / 调试用：重置本会话已确认状态。 */
    public static void resetAcknowledged() {
        ACKNOWLEDGED.set(false);
    }

    @Override
    protected void init() {
        if (openedAt < 0L) {
            openedAt = Util.getMillis();
        }
        layout = GameDisclaimerLayout.of(this.width, this.height);
        rebuildLines();
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);
    }

    private void rebuildLines() {
        this.lines.clear();
        int maxW = Math.max(40, layout.contentW());
        for (GameDisclaimerContent.Section section : GameDisclaimerContent.sections()) {
            Component title = Component.translatable(section.titleKey())
                    .withStyle(ChatFormatting.BOLD);
            for (FormattedCharSequence seq : this.font.split(title, maxW)) {
                this.lines.add(new Line(seq, section.titleColor(), TITLE_LINE_H));
            }
            this.lines.add(new Line(null, 0, 4));
            String body = Component.translatable(section.bodyKey()).getString();
            String[] paras = body.split("\n", -1);
            for (int i = 0; i < paras.length; i++) {
                String para = paras[i];
                if (para.isEmpty()) {
                    this.lines.add(new Line(null, 0, 6));
                    continue;
                }
                for (FormattedCharSequence seq : this.font.split(Component.literal(para), maxW)) {
                    this.lines.add(new Line(seq, BODY_COLOR, BODY_LINE_H));
                }
                if (i < paras.length - 1) {
                    this.lines.add(new Line(null, 0, 3));
                }
            }
            this.lines.add(new Line(null, 0, SECTION_GAP));
        }
        int totalH = this.lines.stream().mapToInt(l -> l.height).sum();
        this.maxScroll = Math.max(0, totalH - layout.contentH() + 4);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (layout == null) {
            layout = GameDisclaimerLayout.of(this.width, this.height);
        }
        float enter = SreUiStyle.enterT(this.openedAt);
        SreUiStyle.renderMenuBackdrop(g, this.width, this.height, partialTick, 1.0F);
        SreUiStyle.drawPanel(g, layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH(), enter);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        float enter = SreUiStyle.enterT(this.openedAt);

        int cx = layout.panelX() + layout.panelW() / 2;
        Component header = Component.translatable(GameDisclaimerContent.HEADER)
                .withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, header,
                cx - this.font.width(header) / 2,
                layout.panelY() + 8,
                LoadingFx.withAlpha(0xF5E8C8, enter), false);
        Component sub = Component.translatable(GameDisclaimerContent.SUBTITLE);
        g.drawString(this.font, sub,
                cx - this.font.width(sub) / 2,
                layout.panelY() + 20,
                LoadingFx.withAlpha(0x9E8B6E, enter * 0.95F), false);
        SreUiStyle.drawTitleUnderline(g, cx, layout.panelY() + layout.headerH() - 4,
                Math.min(90, layout.panelW() / 5), enter);

        g.enableScissor(layout.contentX(), layout.contentY(),
                layout.contentX() + layout.contentW(), layout.contentY() + layout.contentH());
        try {
            int y = layout.contentY() + 2 - (int) this.scrollOffset;
            int clipBottom = layout.contentY() + layout.contentH();
            for (Line line : this.lines) {
                if (line.text != null
                        && y + line.height >= layout.contentY() - 8
                        && y <= clipBottom + 8) {
                    g.drawString(this.font, line.text, layout.contentX(), y,
                            LoadingFx.withAlpha(line.color, enter), false);
                }
                y += line.height;
            }
        } finally {
            g.disableScissor();
        }

        drawScrollbar(g, enter);
        drawConfirmButton(g, mouseX, mouseY, enter);

        Component hint = Component.translatable(GameDisclaimerContent.HINT);
        int hintY = layout.buttonY() - 12;
        g.drawString(this.font, hint,
                cx - this.font.width(hint) / 2, hintY,
                LoadingFx.withAlpha(0x9E8B6E, enter * 0.9F), false);
    }

    private void drawScrollbar(GuiGraphics g, float enter) {
        if (this.maxScroll <= 0.01F) {
            return;
        }
        int trackH = layout.sbBot() - layout.sbTop();
        int totalH = this.lines.stream().mapToInt(l -> l.height).sum();
        int thumbH = Math.max(18, (int) (trackH * (layout.contentH() / (float) Math.max(1, totalH))));
        float prog = this.maxScroll <= 0 ? 0 : this.scrollOffset / this.maxScroll;
        int thumbY = layout.sbTop() + (int) (prog * (trackH - thumbH));
        g.fill(layout.sbX(), layout.sbTop(), layout.sbX() + layout.sbW(), layout.sbBot(),
                LoadingFx.withAlpha(0xFFE8C0, 0.18F * enter));
        int a = this.draggingScroll ? 0xAA : 0x66;
        g.fill(layout.sbX(), thumbY, layout.sbX() + layout.sbW(), thumbY + thumbH,
                LoadingFx.lerpArgb(enter, 0x00C9A84C, (a << 24) | 0x00C9A84C));
    }

    private void drawConfirmButton(GuiGraphics g, int mouseX, int mouseY, float enter) {
        boolean hovered = layout.inButton(mouseX, mouseY);
        this.buttonHover += ((hovered ? 1.0F : 0.0F) - this.buttonHover) * 0.22F;
        int x = layout.buttonX();
        int y = layout.buttonY();
        int w = layout.buttonW();
        int h = layout.buttonH();
        int bg = SreUiStyle.blend(0xFF1A1008, 0xFFC9A84C, 0.18F + this.buttonHover * 0.22F);
        g.fillGradient(x, y, x + w, y + h, bg, SreUiStyle.blend(bg, 0xFF120A04, 0.35F));
        int border = this.buttonHover > 0.5F ? SreUiStyle.GOLD : SreUiStyle.BORDER;
        g.renderOutline(x, y, w, h, LoadingFx.lerpArgb(enter, 0x008B6914, border));
        Component label = Component.translatable(GameDisclaimerContent.CONFIRM)
                .withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, label,
                x + (w - this.font.width(label)) / 2, y + 7,
                LoadingFx.withAlpha(0xFFF4DC, enter), false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
            double horizontalAmount, double verticalAmount) {
        if (layout != null && this.maxScroll > 0 && layout.inPanel(mouseX, mouseY)) {
            this.scrollOffset = Mth.clamp(
                    (float) (this.scrollOffset - verticalAmount * 16.0), 0, this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (layout == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (layout.inButton(mouseX, mouseY)) {
            confirm();
            return true;
        }
        if (this.maxScroll > 0 && layout.inScrollbar(mouseX, mouseY)) {
            jumpScrollTo(mouseY);
            this.draggingScroll = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        if (button == 0 && this.draggingScroll && this.maxScroll > 0) {
            jumpScrollTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.draggingScroll = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void jumpScrollTo(double mouseY) {
        int trackH = layout.sbBot() - layout.sbTop();
        if (trackH <= 0) {
            return;
        }
        float prog = (float) ((mouseY - layout.sbTop()) / (float) trackH);
        this.scrollOffset = Mth.clamp(prog * this.maxScroll, 0, this.maxScroll);
    }

    private void confirm() {
        ACKNOWLEDGED.set(true);
        StarRailExpressTitleScreen.skipOpeningPrompt();
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.setScreen(new TitleScreen());
        }
    }

    private record Line(FormattedCharSequence text, int color, int height) {}
}
