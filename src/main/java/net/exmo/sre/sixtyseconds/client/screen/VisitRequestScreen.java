package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsVisitSystem;
import net.exmo.sre.sixtyseconds.network.VisitRequestC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 拜访请求界面：列出别队，对每队可选“交易 / 进入避难所”。 */
public class VisitRequestScreen extends Screen {
    private static final int ROW_H = 24;
    private final int[] teamIds;
    private final String[] labels;

    public VisitRequestScreen(int[] teamIds, String[] labels) {
        super(Component.translatable("message.noellesroles.sixty_seconds.visit_request_title"));
        this.teamIds = teamIds;
        this.labels = labels;
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 180;
        for (int i = 0; i < teamIds.length; i++) {
            int y = 40 + i * ROW_H;
            final int targetTeam = teamIds[i];
            addRenderableWidget(Button.builder(
                    Component.translatable("message.noellesroles.sixty_seconds.visit_trade_btn"),
                    b -> send(targetTeam, SixtySecondsVisitSystem.TYPE_TRADE))
                    .bounds(left + 130, y, 80, 20).build());
            addRenderableWidget(Button.builder(
                    Component.translatable("message.noellesroles.sixty_seconds.visit_enter_btn"),
                    b -> send(targetTeam, SixtySecondsVisitSystem.TYPE_ENTER))
                    .bounds(left + 214, y, 80, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 40, 40 + teamIds.length * ROW_H + 10, 80, 20).build());
    }

    private void send(int targetTeam, int type) {
        ClientPlayNetworking.send(new VisitRequestC2SPacket(targetTeam, type));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        int left = this.width / 2 - 180;
        for (int i = 0; i < labels.length; i++) {
            graphics.drawString(this.font, labels[i], left, 40 + i * ROW_H + 6, 0xFFDDDDDD);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
