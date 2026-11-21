/* GNU General Public License v3 or later */
package io.wifi.starrailexpress.client.ponder;

import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import java.util.List;

/** 思索里显示心情值、技能效果、渲染文字与伪装标签的覆盖层。 */
public class PonderInfoOverlayElement implements PonderOverlayElement {
    public enum Kind { MOOD, SKILL, LINES, BADGE }

    private static final int BAR_WIDTH = 72;
    private final Kind kind;
    private final PonderLocalizedText label;
    private final List<PonderLocalizedText> lines;
    private final float before;
    private final float after;
    private final int accent;
    private boolean visible = true;

    public PonderInfoOverlayElement(Kind kind, PonderLocalizedText label, List<PonderLocalizedText> lines,
                                    float before, float after, int accent) {
        this.kind = kind;
        this.label = label == null ? PonderLocalizedText.of("") : label;
        this.lines = List.copyOf(lines == null ? List.of() : lines);
        this.before = Mth.clamp(before, 0.0F, 1.0F);
        this.after = Mth.clamp(after, 0.0F, 1.0F);
        this.accent = accent;
    }

    public PonderInfoOverlayElement addTo(SceneBuilder scene) {
        scene.addInstruction(ps -> { ps.addElement(this); this.visible = true; });
        return this;
    }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; }

    @Override
    public void render(PonderScene scene, PonderUI screen, GuiGraphics graphics, float partialTicks) {
        if (!visible) return;
        Font font = Minecraft.getInstance().font;
        int width = 60;
        for (PonderLocalizedText line : lines) width = Math.max(width, font.width(line.plain()) + 22);
        width = Math.max(width, font.width(label.plain()) + 22);
        if (kind == Kind.MOOD) width = Math.max(width, BAR_WIDTH + 22);
        width = Math.min(width, Math.max(140, screen.width - 40));

        int x = screen.width - width - 16;
        // 与左上角状态面板统一下移，避开 Ponder 顶部的原生标题 / 段落标签。
        int y = 112;
        // 不画整块半透明面板和外框：只要文字与窄状态条，背景保持透明。
        int textX = x + 2;
        int textY = y + 2;
        if (!label.isEmpty()) {
            graphics.drawString(font, label.resolve(), textX, textY, accent, true);
            textY += font.lineHeight + 4;
        }
        if (kind == Kind.MOOD) {
            graphics.fill(textX, textY + 2, textX + BAR_WIDTH, textY + 12, 0xFF202028);
            graphics.renderOutline(textX, textY + 2, BAR_WIDTH, 10, 0xFF55556A);
            graphics.fill(textX + 1, textY + 3,
                    textX + 1 + Math.round((BAR_WIDTH - 2) * before), textY + 11, 0x80AAAAAA);
            graphics.fill(textX + 1, textY + 3,
                    textX + 1 + Math.round((BAR_WIDTH - 2) * after), textY + 11, accent);
            graphics.drawString(font, Component.literal(
                    Math.round(before * 100) + "% -> " + Math.round(after * 100) + "%"),
                    textX, textY + 15, 0xFFDDDDDD, true);
            return;
        }
        for (PonderLocalizedText line : lines) {
            graphics.drawString(font, line.resolve(), textX, textY,
                    kind == Kind.BADGE ? accent : 0xFFEEEEEE, true);
            textY += font.lineHeight + 3;
        }
    }
}
