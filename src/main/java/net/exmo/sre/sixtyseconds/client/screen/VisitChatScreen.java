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

/** 拜访双向对话窗：显示消息记录 + 输入框；发送经 C2S 中继到双方。 */
public class VisitChatScreen extends Screen {
    private static final int MAX_SHOWN = 15;

    private final String partnerName;
    private final List<String> messages = new ArrayList<>();
    private EditBox input;

    public VisitChatScreen(String partnerName) {
        super(Component.translatable("message.noellesroles.sixty_seconds.visit_chat_title", partnerName));
        this.partnerName = partnerName;
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

    /** 由 S2C 消息包调用，追加一条消息。 */
    public void addMessage(String sender, String text) {
        messages.add(sender + ": " + text);
        if (messages.size() > 100) {
            messages.remove(0);
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
        int start = Math.max(0, messages.size() - MAX_SHOWN);
        int y = 40;
        for (int i = start; i < messages.size(); i++) {
            graphics.drawString(this.font, messages.get(i), this.width / 2 - 150, y, 0xFFE0E0E0);
            y += 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
