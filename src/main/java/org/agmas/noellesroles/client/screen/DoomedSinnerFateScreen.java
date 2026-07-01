package org.agmas.noellesroles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.roles.neutral.doomedsinner.DoomedSinnerPlayerComponent;
import org.agmas.noellesroles.packet.DoomedSinnerFateRevealS2CPacket;

import java.util.List;

/**
 * 宿命的罪人「命运的启示」结果界面：展示目标最近的杀人方式。
 */
public class DoomedSinnerFateScreen extends Screen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 180;
    private static final int ROW_HEIGHT = 24;

    private final String targetName;
    private final List<String> killMethods;

    public DoomedSinnerFateScreen(DoomedSinnerFateRevealS2CPacket payload) {
        super(Component.translatable("screen.noellesroles.doomed_sinner.title"));
        this.targetName = payload.targetName();
        this.killMethods = payload.killMethods();
    }

    private int panelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (height - PANEL_HEIGHT) / 2;
    }

    @Override
    protected void init() {
        int left = panelX();
        int top = panelY();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + PANEL_WIDTH / 2 - 50, top + PANEL_HEIGHT - 28, 100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int left = panelX();
        int top = panelY();
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;
        int cx = left + PANEL_WIDTH / 2;

        g.fill(left, top, right, bottom, 0xEE140F1D);
        g.fill(left + 3, top + 3, right - 3, bottom - 3, 0xCC241433);

        g.drawCenteredString(font, title, cx, top + 12, 0xFFD08CFF);
        g.drawCenteredString(font,
                Component.translatable("screen.noellesroles.doomed_sinner.target", targetName),
                cx, top + 28, 0xFFE0C8FF);

        int y = top + 50;
        if (killMethods.isEmpty()) {
            g.drawCenteredString(font,
                    Component.translatable("screen.noellesroles.doomed_sinner.no_kills"),
                    cx, y + 10, 0xFFAAAAAA);
        } else {
            for (int i = 0; i < killMethods.size(); i++) {
                ResourceLocation reason = ResourceLocation.tryParse(killMethods.get(i));
                Component reasonText = reason == null
                        ? Component.literal(killMethods.get(i))
                        : Component.translatable(DoomedSinnerPlayerComponent.deathReasonKey(reason));
                g.drawString(font,
                        Component.translatable("screen.noellesroles.doomed_sinner.kill_entry", i + 1, reasonText),
                        left + 20, y, 0xFFEBDFFF);
                y += ROW_HEIGHT;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
