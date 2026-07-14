package net.exmo.sre.sixtyseconds.client.screen;

import net.exmo.sre.sixtyseconds.network.VisitChatSendC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 拜访双向对话窗：显示消息记录 + 输入框；发送经 C2S 中继到双方。
 * 由避难所门 GUI 打开（点门 → 「对话」）；消息历史为<b>静态</b>——关屏期间收到的消息
 * 由 {@code VisitChatMessageS2CPacket} 接收器直接 {@link #record} 进历史，重开门 GUI 不丢。
 * 换了对话对象（partnerName 变化）时自动清空历史。
 */
public class VisitChatScreen extends Screen {
    private static final int MAX_SHOWN = 15;

    /** 静态消息历史：跨开屏保留；对话对象变化时清空。 */
    private static final List<String> HISTORY = new ArrayList<>();
    private static String historyPartner = "";

    private final String partnerName;
    private EditBox input;

    public VisitChatScreen(String partnerName) {
        super(Component.translatable("message.noellesroles.sixty_seconds.visit_chat_title", partnerName));
        this.partnerName = partnerName;
        ensurePartner(partnerName);
    }

    /** 由 S2C 消息包接收器调用（无论本屏是否打开），追加进静态历史。 */
    public static void record(String sender, String text) {
        HISTORY.add(sender + ": " + text);
        if (HISTORY.size() > 100) {
            HISTORY.remove(0);
        }
    }

    /** 换了对话对象则清空历史（交易窗内嵌聊天也共用这份历史，见 {@code TradeScreen}）。 */
    public static void ensurePartner(String partnerName) {
        if (!partnerName.equals(historyPartner)) {
            HISTORY.clear();
            historyPartner = partnerName;
        }
    }

    /** 只读访问静态消息历史（供交易窗聊天区渲染）。 */
    public static List<String> history() {
        return HISTORY;
    }

    @Override
    protected void init() {
        input = new EditBox(this.font, this.width / 2 - 150, this.height - 50, 240, 20, Component.empty());
        input.setMaxLength(128);
        addRenderableWidget(input);
        setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.translatable("message.noellesroles.sixty_seconds.visit_chat_send"),
                b -> send()).bounds(this.width / 2 + 95, this.height - 50, 55, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 40, this.height - 26, 80, 20).build());
    }

    private void send() {
        String text = input.getValue().strip();
        if (!text.isEmpty()) {
            ClientPlayNetworking.send(new VisitChatSendC2SPacket(text));
            input.setValue("");
        }
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && input != null && input.isFocused()) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFFFF);
        int start = Math.max(0, HISTORY.size() - MAX_SHOWN);
        int y = 40;
        for (int i = start; i < HISTORY.size(); i++) {
            graphics.drawString(this.font, HISTORY.get(i), this.width / 2 - 150, y, 0xFFE0E0E0);
            y += 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
