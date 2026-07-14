package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.network.TradeActionC2SPacket;
import net.exmo.sre.sixtyseconds.network.VisitChatSendC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 交易窗（主手对主手）：显示提示 + 确认/取消。双方都确认后服务端交换主手物品。
 * 内嵌<b>聊天区</b>：交易会话建立时服务端已挂上拜访聊天中继（{@code SixtySecondsTrade.start}），
 * 消息历史与拜访聊天共用 {@link VisitChatScreen#history()}（S2C 接收器统一 record）。
 * P0：以主手物为筹码；多物品交易为后续增强。
 */
public class TradeScreen extends Screen {
    private static final int MAX_SHOWN = 8;

    private final String partnerName;
    private boolean resolved = false;
    private EditBox chatInput;

    public TradeScreen(String partnerName) {
        super(Component.translatable("message.noellesroles.sixty_seconds.trade_title", partnerName));
        this.partnerName = partnerName;
        VisitChatScreen.ensurePartner(partnerName);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("message.noellesroles.sixty_seconds.trade_confirm"),
                b -> action(TradeActionC2SPacket.CONFIRM))
                .bounds(this.width / 2 - 105, 74, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("message.noellesroles.sixty_seconds.trade_cancel"),
                b -> action(TradeActionC2SPacket.CANCEL))
                .bounds(this.width / 2 + 5, 74, 100, 20).build());
        chatInput = new EditBox(this.font, this.width / 2 - 150, this.height - 40, 240, 20, Component.empty());
        chatInput.setMaxLength(128);
        addRenderableWidget(chatInput);
        addRenderableWidget(Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.visit_chat_send"), b -> sendChat())
                .bounds(this.width / 2 + 95, this.height - 40, 55, 20).build());
    }

    private void sendChat() {
        String text = chatInput.getValue().strip();
        if (!text.isEmpty()) {
            ClientPlayNetworking.send(new VisitChatSendC2SPacket(text));
            chatInput.setValue("");
        }
    }

    private void action(int action) {
        resolved = true;
        ClientPlayNetworking.send(new TradeActionC2SPacket(action));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && chatInput != null && chatInput.isFocused()) {
            sendChat();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("message.noellesroles.sixty_seconds.trade_hint"),
                this.width / 2, 40, 0xFFAAAAAA);
        // 聊天区：确认按钮下方到输入框之间显示最近消息
        List<String> history = VisitChatScreen.history();
        int start = Math.max(0, history.size() - MAX_SHOWN);
        int y = 106;
        for (int i = start; i < history.size(); i++) {
            graphics.drawString(this.font, history.get(i), this.width / 2 - 150, y, 0xFFE0E0E0);
            y += 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
