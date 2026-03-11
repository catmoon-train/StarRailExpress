package org.agmas.noellesroles.client.screen;

import org.agmas.noellesroles.component.TelegrapherPlayerComponent;
import org.agmas.noellesroles.packet.TelegrapherC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 电报员消息编辑屏幕
 * 
 * 功能：
 * - 输入匿名消息文本
 * - 点击确认发送给所有玩家
 * - 显示剩余使用次数
 */
public class TelegrapherScreen extends Screen {

    // 文本输入框
    private EditBox messageField;
    // 确认按钮
    private Button confirmButton;

    // 剩余使用次数
    private int remainingUses = 0;

    public TelegrapherScreen() {
        super(Component.translatable("\"screen.noellesroles.telegrapher.title\""));
    }

    @Override
    protected void init() {
        super.init();
        
        // 获取剩余使用次数
        if (minecraft != null && minecraft.player != null) {
            TelegrapherPlayerComponent comp = TelegrapherPlayerComponent.KEY.get(minecraft.player);
            if (comp != null)
                remainingUses = comp.remainingUses;
        }

        // 创建文本输入框
        int fieldWidth = 300;
        int fieldHeight = 20;
        int fieldX = (width - fieldWidth) / 2;
        int fieldY = height / 2 - 10;

        messageField = new EditBox(
                font,
                fieldX,
                fieldY,
                fieldWidth,
                fieldHeight,
                Component.translatable("screen.noellesroles.telegrapher.message"));
        messageField.setValue("");
        messageField.setMaxLength(200); // 限制最大长度
        messageField.setHint(Component.translatable("screen.noellesroles.telegrapher.placeholder")
                .withStyle(ChatFormatting.GRAY));
        addWidget(messageField);
        setInitialFocus(messageField);

        // 创建确认按钮
        int buttonWidth = 100;
        int buttonHeight = 20;
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = fieldY + fieldHeight + 10;

        confirmButton = Button.builder(
                Component.translatable("screen.noellesroles.telegrapher.confirm"),
                button -> onConfirm())
                .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
                .build();

        addRenderableWidget(confirmButton);
    }

    /**
     * 确认按钮被点击
     */
    private void onConfirm() {
        if (minecraft == null || minecraft.player == null)
            return;

        String message = messageField.getValue().trim();

        // 发送网络包到服务端
        if (!message.isEmpty()) {
            // 发送包到服务器
            ClientPlayNetworking.send(new TelegrapherC2SPacket(message));
        }
        
        // 关闭屏幕
        if (this.minecraft != null)
            this.minecraft.setScreen((Screen) null);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 渲染背景
        renderBackground(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
        
        // 渲染标题
        Component title = Component.translatable("screen.noellesroles.telegrapher.title")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        context.drawCenteredString(font, title, width / 2, 40, 0xFFFFFF);

        // 渲染剩余次数提示
        Component usesText =
                Component.translatable("screen.noellesroles.telegrapher.remaining",
                remainingUses)
                .withStyle(remainingUses > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
        context.drawCenteredString(font, usesText, width / 2, 60, 0xFFFFFF);

        // 渲染说明文本
        Component hint = Component.translatable("screen.noellesroles.telegrapher.hint")
                .withStyle(ChatFormatting.GRAY);
        context.drawCenteredString(font, hint, width / 2, height / 2 - 30, 0x888888);

        messageField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void resize(Minecraft client, int width, int height) {
        String text = messageField.getValue();
        super.resize(client, width, height);
        messageField.setValue(text);
    }
}
