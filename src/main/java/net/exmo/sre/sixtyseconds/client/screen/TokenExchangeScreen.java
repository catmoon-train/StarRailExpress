package net.exmo.sre.sixtyseconds.client.screen;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import net.exmo.sre.sixtyseconds.network.TokenExchangeC2SPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * 兑换实体游戏币（余额 → 实体币，1 枚 = 1 币）：输入数量或「全部」，
 * 经 {@code TokenExchangeC2SPacket} 由服务端校验扣余额并发放 {@code sixty_seconds_coin}。
 * 由 E 背包（{@code SixtySecondsEquipSlotMixin} 注入的「兑换实体币」按钮）打开；
 * 余额直接读本人 CCA 组件（{@link SREPlayerMinigameTaskComponent}，服务端自动同步）。
 * 面板配色遵循 docs/ui_style.md。
 */
public class TokenExchangeScreen extends Screen {
    private static final int PANEL_W = 240;
    private static final int PANEL_H = 118;

    private EditBox amountInput;

    public TokenExchangeScreen() {
        super(Component.translatable("message.noellesroles.sixty_seconds.coin_exchange_title"));
    }

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
    }

    private int balance() {
        return this.minecraft != null && this.minecraft.player != null
                ? SREPlayerMinigameTaskComponent.KEY.get(this.minecraft.player).getTokens() : 0;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();
        amountInput = new EditBox(this.font, x + 12, y + 52, PANEL_W - 24, 18, Component.empty());
        amountInput.setMaxLength(6);
        amountInput.setFilter(s -> s.matches("\\d*"));
        addRenderableWidget(amountInput);
        setInitialFocus(amountInput);
        int buttonW = (PANEL_W - 24 - 8) / 3;
        addRenderableWidget(Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.coin_exchange_confirm"),
                b -> exchange(parseAmount())).bounds(x + 12, y + 82, buttonW, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("message.noellesroles.sixty_seconds.coin_exchange_all"),
                b -> exchange(balance())).bounds(x + 12 + buttonW + 4, y + 82, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x + 12 + (buttonW + 4) * 2, y + 82, buttonW, 20).build());
    }

    private int parseAmount() {
        try {
            return Integer.parseInt(amountInput.getValue().strip());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void exchange(int amount) {
        if (amount > 0) {
            ClientPlayNetworking.send(new TokenExchangeC2SPacket(amount));
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && amountInput != null && amountInput.isFocused()) {
            exchange(parseAmount());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = panelX();
        int y = panelY();
        graphics.fillGradient(x, y, x + PANEL_W, y + PANEL_H, 0xD81A1008, 0xD820140A);
        graphics.renderOutline(x, y, PANEL_W, PANEL_H, 0xFF8B6914);
        graphics.fill(x + 1, y + 1, x + PANEL_W - 1, y + 2, 0x33FFE8C0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = panelX();
        int y = panelY();
        graphics.drawCenteredString(this.font, this.title.copy().withStyle(ChatFormatting.BOLD),
                this.width / 2, y + 10, 0xFFD4AF37);
        graphics.drawString(this.font, Component.translatable(
                "message.noellesroles.sixty_seconds.coin_exchange_balance", balance()),
                x + 12, y + 28, 0xFFFFF4DC, false);
        graphics.drawString(this.font, Component.translatable(
                "message.noellesroles.sixty_seconds.coin_exchange_hint"),
                x + 12, y + 40, 0xFF9E8B6E, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
