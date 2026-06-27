package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.backpack.BackpackState;
import io.wifi.starrailexpress.client.data.ClientPlayerDataCache;
import io.wifi.starrailexpress.progression.ProgressionState.FactionCardType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.widget.custom_button.ModernButton;

/**
 * 场外背包 GUI：展示各阵营卡牌计数，点击非空卡可激活（沿用 {@code sre:pass activate} 路径）。
 * 数据来自 {@link ClientPlayerDataCache#backpack}（由服务端 BackpackManager 通过通用 part 同步）。
 */
public class BackpackScreen extends Screen {

    /** 渲染顺序与配色，与 ProgressionPassScreen 的阵营卡按钮保持一致。 */
    private static final FactionCardType[] DISPLAY_ORDER = {
            FactionCardType.KILLER, FactionCardType.CIVILIAN,
            FactionCardType.NEUTRAL, FactionCardType.NEUTRAL_FOR_KILLER };
    private static final int[] ACCENT = { 0xFFB84141, 0xFF4EA5D9, 0xFFD9A44E, 0xFF8E7BD6 };

    private static final int PANEL_W = 280;
    private static final int ROW_H = 26;
    private static final int ROW_STRIDE = ROW_H + 6;

    private final LocalPlayer player;
    private BackpackState backpack;

    private int panelX, panelY, panelW, panelH;

    public BackpackScreen(Screen  parent) {
        super(Component.translatable("sre.backpack.title"));
        this.player = Minecraft.getInstance().player;
        this.backpack = ClientPlayerDataCache.backpack(player.getUUID());
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen((Screen) parent);
        }
    }

    private Screen parent;
    @Override
    protected void init() {
        clearWidgets();
        this.backpack = ClientPlayerDataCache.backpack(player.getUUID());

        panelW = PANEL_W;
        panelH = 72 + DISPLAY_ORDER.length * ROW_STRIDE + 40;
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int rowX = panelX + 20;
        int rowW = panelW - 40;
        int rowY = panelY + 56;
        for (int i = 0; i < DISPLAY_ORDER.length; i++) {
            addRenderableWidget(createCardButton(rowX, rowY, rowW, DISPLAY_ORDER[i], ACCENT[i]));
            rowY += ROW_STRIDE;
        }

        addRenderableWidget(
                ModernButton.builder(Component.translatable("sre.backpack.close"), button -> this.onClose())
                        .bounds(panelX + panelW - 20 - 120, panelY + panelH - 30, 120, 22)
                        .accentColor(0xFFE8E8F2)
                        .build());
    }

    private net.minecraft.client.gui.components.Button createCardButton(int x, int y, int width,
            FactionCardType type, int accentColor) {
        int count = backpack.cards.getOrDefault(type, 0);
        Component label = Component.literal(
                Component.translatable("sre.pass.faction." + type.questKey).getString() + "  x" + count);
        var btn = ModernButton.builder(label, b -> {
            if (count > 0) {
                sendCommand("sre:pass activate " + type.questKey);
                onClose();
            }
        }).bounds(x, y, width, ROW_H).accentColor(accentColor).build();
        btn.active = count > 0;
        return btn;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, Component.translatable("sre.backpack.title"),
                panelX + panelW / 2, panelY + 22, 0xFFF4F7FF);

        boolean empty = true;
        for (FactionCardType type : DISPLAY_ORDER) {
            if (backpack.cards.getOrDefault(type, 0) > 0) {
                empty = false;
                break;
            }
        }
        if (empty) {
            g.drawCenteredString(font, Component.translatable("sre.backpack.empty"),
                    panelX + panelW / 2, panelY + 38, 0xFF6A7D99);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xE0080C14, 0xF0121B2E);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xD20C1018);
        g.fill(panelX + 14, panelY + 14, panelX + panelW - 14, panelY + 16, 0xFF3AA6FF);
        g.fill(panelX + 14, panelY + panelH - 14, panelX + panelW - 14, panelY + panelH - 12, 0x6648D3FF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendCommand(String command) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        minecraft.player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }
}
