package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsVisitSystem;
import net.exmo.sre.sixtyseconds.network.VisitResponseC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** 拜访提示：目标队成员对某玩家的拜访请求选择同意/拒绝（任一成员先响应即生效）。 */
public class VisitPromptScreen extends Screen {
    private final UUID visitor;
    private final String visitorName;
    private final int type;

    public VisitPromptScreen(UUID visitor, String visitorName, int type) {
        super(Component.translatable("message.noellesroles.sixty_seconds.visit_prompt_title"));
        this.visitor = visitor;
        this.visitorName = visitorName;
        this.type = type;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.visit_accept"), b -> respond(true))
                .bounds(this.width / 2 - 105, this.height / 2 + 10, 100, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.visit_reject"), b -> respond(false))
                .bounds(this.width / 2 + 5, this.height / 2 + 10, 100, 20).build());
    }

    private void respond(boolean accept) {
        ClientPlayNetworking.send(new VisitResponseC2SPacket(visitor, accept));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component typeText = Component.translatable(type == SixtySecondsVisitSystem.TYPE_ENTER
                ? "message.noellesroles.sixty_seconds.visit_enter_btn"
                : "message.noellesroles.sixty_seconds.visit_trade_btn");
        graphics.drawCenteredString(this.font,
                Component.translatable("message.noellesroles.sixty_seconds.visit_prompt", visitorName, typeText),
                this.width / 2, this.height / 2 - 20, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
