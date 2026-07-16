package org.agmas.noellesroles.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 电话拨号界面 - 6位号码输入，拨号按钮
 */
public class PhoneDialScreen extends Screen {

    private final ItemStack stack;
    private final InteractionHand hand;
    private final int[] digits = new int[6];
    private int currentPos = 0;
    private boolean connected = false;

    // 按钮映射
    private Button[] numberButtons = new Button[10];

    public PhoneDialScreen(ItemStack stack, InteractionHand hand) {
        super(Component.translatable("screen.noellesroles.phone_dial"));
        this.stack = stack;
        this.hand = hand;
        for (int i = 0; i < 6; i++) digits[i] = -1;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // 数字按钮：3x3 + 底部一行
        int[][] layout = {
                {1, 2, 3},
                {4, 5, 6},
                {7, 8, 9},
                {-1, 0, -1}
        };

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                int num = layout[row][col];
                if (num < 0) continue;
                int bx = cx - 40 + col * 40;
                int by = cy - 20 + row * 40;
                addRenderableWidget(Button.builder(
                        Component.literal(String.valueOf(num)),
                        btn -> pressNumber(num))
                        .bounds(bx, by, 36, 36)
                        .build());
            }
        }

        // 拨号按钮
        addRenderableWidget(Button.builder(
                Component.translatable("gui.noellesroles.phone_dial.call"),
                btn -> dial())
                .bounds(cx - 60, cy + 100, 56, 20)
                .build());

        // 退格
        addRenderableWidget(Button.builder(
                Component.literal("<"),
                btn -> backspace())
                .bounds(cx + 4, cy + 100, 56, 20)
                .build());

        // 挂断
        addRenderableWidget(Button.builder(
                Component.translatable("gui.noellesroles.phone_dial.hangup"),
                btn -> onClose())
                .bounds(cx - 28, cy + 125, 56, 20)
                .build());
    }

    private void pressNumber(int num) {
        if (currentPos < 6 && !connected) {
            digits[currentPos] = num;
            currentPos++;
        }
    }

    private void backspace() {
        if (currentPos > 0 && !connected) {
            currentPos--;
            digits[currentPos] = -1;
        }
    }

    private void dial() {
        if (currentPos < 6) return; // 号码不完整
        if (minecraft == null || minecraft.player == null) return;

        // 构建号码字符串
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(digits[i]);
        String number = sb.toString();

        // 发送拨号请求到服务端
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new net.exmo.sre.sixtyseconds.network.PhoneDialC2SPacket(number));

        // 关闭界面，等待服务端响应
        minecraft.player.displayClientMessage(
                Component.translatable("message.noellesroles.phone.dialing", number), true);
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // 标题
        guiGraphics.drawCenteredString(font, Component.translatable("screen.noellesroles.phone_dial"),
                cx, cy - 90, 0xFFFFFF);

        // 号码显示: □□□-□□□
        String display = "";
        for (int i = 0; i < 6; i++) {
            if (i == 3) display += "-";
            display += (digits[i] >= 0) ? String.valueOf(digits[i]) : "□";
            if (i == currentPos && !connected) display += "|";
        }
        guiGraphics.drawCenteredString(font, Component.literal(display),
                cx, cy - 60, 0xFFFFAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
