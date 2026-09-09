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

import io.wifi.starrailexpress.SREClientConfig;
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
 * 进入主菜单前的健康 / 医学 / 开源 / 二创免责声明。
 * 仅首次启动展示：必须滚动到内容底部、点击确认按钮后写入客户端配置，之后不再弹出。
 * 各段独立成卡片、标题与正文居中，内容区可滚动，确认按钮是内容的最后一项。
 * 滚动提示固定在标题下方，始终可见。
 */
@Environment(EnvType.CLIENT)
public class GameDisclaimerScreen extends Screen {

    private static final AtomicBoolean ACKNOWLEDGED = new AtomicBoolean(false);

    private static final int BODY_COLOR = 0xFFC8B898;
    private static final int TITLE_LINE_H = 16;
    private static final int BODY_LINE_H = 13;

    private final List<SectionBlock> sections = new ArrayList<>();
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
        return ACKNOWLEDGED.get() || SREClientConfig.instance().gameDisclaimerAccepted;
    }

    /** 测试 / 调试用：清除已确认状态，下次启动会重新弹出声明。 */
    public static void resetAcknowledged() {
        ACKNOWLEDGED.set(false);
        SREClientConfig config = SREClientConfig.instance();
        config.gameDisclaimerAccepted = false;
        SREClientConfig.HANDLER.save();
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
        this.sections.clear();
        int pad = GameDisclaimerLayout.SECTION_INNER_PAD;
        int textW = Math.max(40, layout.contentW() - pad * 2);
        List<GameDisclaimerContent.Section> defs = GameDisclaimerContent.sections();
        for (int i = 0; i < defs.size(); i++) {
            GameDisclaimerContent.Section section = defs.get(i);
            List<Line> lines = new ArrayList<>();
            String index = String.format("%02d", i + 1);
            lines.add(new Line(Component.literal(index).getVisualOrderText(),
                    0xFF9E8B6E, 11, false));
            Component title = Component.translatable(section.titleKey())
                    .withStyle(ChatFormatting.BOLD);
            for (FormattedCharSequence seq : this.font.split(title, textW)) {
                lines.add(new Line(seq, section.titleColor(), TITLE_LINE_H, true));
            }
            lines.add(new Line(null, 0, 6, false));
            String body = Component.translatable(section.bodyKey()).getString();
            String[] paras = body.split("\n", -1);
            for (int p = 0; p < paras.length; p++) {
                String para = paras[p];
                if (para.isEmpty()) {
                    lines.add(new Line(null, 0, 8, false));
                    continue;
                }
                for (FormattedCharSequence seq : this.font.split(Component.literal(para), textW)) {
                    lines.add(new Line(seq, BODY_COLOR, BODY_LINE_H, false));
                }
                if (p < paras.length - 1 && !paras[p + 1].isEmpty()) {
                    lines.add(new Line(null, 0, 5, false));
                }
            }
            int innerH = lines.stream().mapToInt(l -> l.height).sum();
            this.sections.add(new SectionBlock(lines, innerH + pad * 2, section.titleColor()));
        }
        this.maxScroll = Math.max(0, totalContentHeight() - layout.contentH());
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);
    }

    /** 所有卡片连同间距的总高度。 */
    private int sectionsHeight() {
        if (this.sections.isEmpty()) {
            return 0;
        }
        int h = 0;
        for (SectionBlock block : this.sections) {
            h += block.height + GameDisclaimerLayout.SECTION_GAP;
        }
        return h - GameDisclaimerLayout.SECTION_GAP;
    }

    /** 提示文字与确认按钮在内容坐标系中的起点。 */
    private int buttonContentY() {
        return sectionsHeight() + GameDisclaimerLayout.BUTTON_TOP_GAP;
    }

    private int totalContentHeight() {
        return buttonContentY() + layout.buttonH() + GameDisclaimerLayout.CONTENT_BOTTOM_PAD;
    }

    /** 确认按钮在当前滚动位置下的屏幕 Y。 */
    private int buttonScreenY() {
        return layout.contentY() - (int) this.scrollOffset + buttonContentY();
    }

    private boolean inButton(double mouseX, double mouseY) {
        if (layout == null
                || !GameDisclaimerLayout.isButtonReachable(this.scrollOffset, this.maxScroll)) {
            return false;
        }
        return GameDisclaimerLayout.inRect(mouseX, mouseY,
                layout.buttonX(), buttonScreenY(), layout.buttonW(), layout.buttonH());
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

        Component hint = Component.translatable(GameDisclaimerContent.HINT);
        g.drawString(this.font, hint,
                cx - this.font.width(hint) / 2,
                layout.panelY() + GameDisclaimerLayout.HINT_Y,
                LoadingFx.withAlpha(0x9E8B6E, enter * 0.9F), false);

        g.enableScissor(layout.contentX(), layout.contentY(),
                layout.contentX() + layout.contentW(), layout.contentY() + layout.contentH());
        try {
            int y = layout.contentY() - (int) this.scrollOffset;
            int clipTop = layout.contentY();
            int clipBottom = layout.contentY() + layout.contentH();
            int axisX = layout.contentX() + layout.contentW() / 2;
            int pad = GameDisclaimerLayout.SECTION_INNER_PAD;
            for (SectionBlock block : this.sections) {
                int blockBottom = y + block.height;
                if (blockBottom >= clipTop - 8 && y <= clipBottom + 8) {
                    drawSectionCard(g, y, block, enter);
                    int lineY = y + pad;
                    for (Line line : block.lines) {
                        if (line.text != null
                                && lineY + line.height >= clipTop - 8
                                && lineY <= clipBottom + 8) {
                            int textW = this.font.width(line.text);
                            int textX = GameDisclaimerContent.centerX(axisX, textW);
                            g.drawString(this.font, line.text, textX, lineY,
                                    LoadingFx.withAlpha(line.color, enter), false);
                            if (line.title) {
                                int ruleW = Math.min(56, Math.max(24, textW / 2));
                                int ruleY = lineY + line.height - 3;
                                g.fill(axisX - ruleW / 2, ruleY, axisX + ruleW / 2, ruleY + 1,
                                        LoadingFx.withAlpha(block.accent, 0.55F * enter));
                            }
                        }
                        lineY += line.height;
                    }
                }
                y += block.height + GameDisclaimerLayout.SECTION_GAP;
            }

            drawConfirmButton(g, mouseX, mouseY, enter);
        } finally {
            g.disableScissor();
        }

        drawScrollbar(g, enter);
    }

    private void drawSectionCard(GuiGraphics g, int y, SectionBlock block, float enter) {
        int x = layout.contentX();
        int w = layout.contentW();
        int h = block.height;
        int top = LoadingFx.lerpArgb(enter, 0x001A1008, 0xB81A1008);
        int bot = LoadingFx.lerpArgb(enter, 0x00120804, 0xB8120804);
        g.fillGradient(x, y, x + w, y + h, top, bot);
        int border = SreUiStyle.blend(SreUiStyle.BORDER, block.accent, 0.40F);
        g.renderOutline(x, y, w, h, LoadingFx.lerpArgb(enter, 0x008B6914, border));
        g.fill(x + 1, y + 1, x + w - 1, y + 2, LoadingFx.lerpArgb(enter, 0x00FFE8C0, 0x22FFE8C0));
        int accentBar = LoadingFx.withAlpha(block.accent, 0.85F * enter);
        g.fill(x, y + 4, x + 2, y + h - 4, accentBar);
        g.fill(x + w - 2, y + 4, x + w, y + h - 4, accentBar);
    }

    private void drawScrollbar(GuiGraphics g, float enter) {
        if (this.maxScroll <= 0.01F) {
            return;
        }
        int trackH = layout.sbBot() - layout.sbTop();
        int totalH = totalContentHeight();
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
        boolean hovered = inButton(mouseX, mouseY);
        this.buttonHover += ((hovered ? 1.0F : 0.0F) - this.buttonHover) * 0.22F;
        int x = layout.buttonX();
        int y = buttonScreenY();
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
        if (layout != null && this.maxScroll > 0) {
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
        if (inButton(mouseX, mouseY)) {
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
        if (scrollByKey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean scrollByKey(int keyCode) {
        if (this.maxScroll <= 0) {
            return false;
        }
        float delta = switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> 16.0F;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> -16.0F;
            case GLFW.GLFW_KEY_PAGE_DOWN -> layout.contentH() * 0.8F;
            case GLFW.GLFW_KEY_PAGE_UP -> -layout.contentH() * 0.8F;
            case GLFW.GLFW_KEY_HOME -> -this.scrollOffset;
            case GLFW.GLFW_KEY_END -> this.maxScroll - this.scrollOffset;
            default -> 0.0F;
        };
        if (delta == 0.0F) {
            return false;
        }
        this.scrollOffset = Mth.clamp(this.scrollOffset + delta, 0, this.maxScroll);
        return true;
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
        if (!GameDisclaimerLayout.isButtonReachable(this.scrollOffset, this.maxScroll)) {
            return; // 必须滚动到底部才能确认
        }
        ACKNOWLEDGED.set(true);
        SREClientConfig config = SREClientConfig.instance();
        config.gameDisclaimerAccepted = true;
        SREClientConfig.HANDLER.save();
        StarRailExpressTitleScreen.skipOpeningPrompt();
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.setScreen(new TitleScreen());
        }
    }

    private record Line(FormattedCharSequence text, int color, int height, boolean title) {}

    private record SectionBlock(List<Line> lines, int height, int accent) {}
}
