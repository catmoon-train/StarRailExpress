package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.network.TradeActionC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 交易窗（主手对主手）：显示提示 + 确认/取消。双方都确认后服务端交换主手物品。
 * P0：以主手物为筹码；多物品交易为后续增强。
 */
public class TradeScreen extends Screen {
    private final String partnerName;
    private boolean resolved = false;

    public TradeScreen(String partnerName) {
        super(Component.translatable("message.noellesroles.sixty_seconds.trade_title", partnerName));
        this.partnerName = partnerName;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("message.noellesroles.sixty_seconds.trade_confirm"),
                b -> action(TradeActionC2SPacket.CONFIRM))
                .bounds(this.width / 2 - 105, this.height / 2 + 20, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("message.noellesroles.sixty_seconds.trade_cancel"),
                b -> action(TradeActionC2SPacket.CANCEL))
                .bounds(this.width / 2 + 5, this.height / 2 + 20, 100, 20).build());
    }

    private void action(int action) {
        resolved = true;
        ClientPlayNetworking.send(new TradeActionC2SPacket(action));
        onClose();
    }

    @Override
    public void onClose() {
        if (!resolved) {
            resolved = true;
            ClientPlayNetworking.send(new TradeActionC2SPacket(TradeActionC2SPacket.CANCEL));
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("message.noellesroles.sixty_seconds.trade_hint"),
                this.width / 2, this.height / 2 - 20, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
