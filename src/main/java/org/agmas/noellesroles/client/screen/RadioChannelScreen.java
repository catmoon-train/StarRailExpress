package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.content.item.RadioItem;
import org.agmas.noellesroles.packet.RadioChannelC2SPacket;

/**
 * 对讲机频道界面：输入 {@link RadioItem#MIN_CHANNEL}..{@link RadioItem#MAX_CHANNEL} 的频道号接入通话。
 * 风格遵循 {@code docs/ui_style.md}（棕黑渐变面板 + 棕褐描边 + 顶部装饰线 + 金色标题）。
 */
public class RadioChannelScreen extends Screen {
    // ── ui_style 色板 ─────────────────────────────────────────
    private static final int BG_TOP = 0xD81A1008;
    private static final int BG_BOTTOM = 0xD820140A;
    private static final int BORDER = 0xFF8B6914;
    private static final int DECO_LINE = 0x33FFE8C0;
    private static final int GOLD = 0xFFD4AF37;
    private static final int TEXT = 0xFFFFF4DC;
    private static final int MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B;

    private static final int PANEL_W = 230;
    private static final int PANEL_H = 138;

    private final int currentChannel;
    private EditBox channelBox;

    public RadioChannelScreen(int currentChannel) {
        super(Component.translatable("gui.noellesroles.radio.title"));
        this.currentChannel = currentChannel;
    }

    @Override
    protected void init() {
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;

        channelBox = new EditBox(this.font, px + 20, py + 44, PANEL_W - 40, 20,
                Component.translatable("gui.noellesroles.radio.channel"));
        channelBox.setMaxLength(4);
        channelBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        channelBox.setHint(Component.translatable("gui.noellesroles.radio.hint").withStyle(ChatFormatting.DARK_GRAY));
        if (currentChannel >= RadioItem.MIN_CHANNEL) {
            channelBox.setValue(String.valueOf(currentChannel));
        }
        addRenderableWidget(channelBox);
        setInitialFocus(channelBox);

        int by = py + PANEL_H - 30;
        addRenderableWidget(Button.builder(
                Component.translatable("gui.noellesroles.radio.join"), b -> join())
                .bounds(px + 16, by, 88, 20).build());
        addRenderableWidget(Button.builder(
                Component.translatable("gui.noellesroles.radio.leave"), b -> {
                    ClientPlayNetworking.send(new RadioChannelC2SPacket(0, true));
                    onClose();
                })
                .bounds(px + 110, by, 48, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(px + 164, by, 50, 20).build());
    }

    private void join() {
        String raw = channelBox.getValue();
        if (raw.isEmpty()) {
            return;
        }
        int channel;
        try {
            channel = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return;
        }
        channel = Math.max(RadioItem.MIN_CHANNEL, Math.min(RadioItem.MAX_CHANNEL, channel));
        ClientPlayNetworking.send(new RadioChannelC2SPacket(channel, false));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        g.fillGradient(px, py, px + PANEL_W, py + PANEL_H, BG_TOP, BG_BOTTOM);
        g.renderOutline(px, py, PANEL_W, PANEL_H, BORDER);
        g.fill(px + 1, py + 1, px + PANEL_W - 1, py + 2, DECO_LINE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int px = (this.width - PANEL_W) / 2;
        int py = (this.height - PANEL_H) / 2;
        g.drawCenteredString(this.font,
                Component.translatable("gui.noellesroles.radio.title").withStyle(ChatFormatting.BOLD),
                this.width / 2, py + 12, GOLD);
        g.drawString(this.font, Component.translatable("gui.noellesroles.radio.channel"), px + 20, py + 32, TEXT);
        // 当前频道状态
        Component status = currentChannel >= RadioItem.MIN_CHANNEL
                ? Component.translatable("gui.noellesroles.radio.current", currentChannel)
                : Component.translatable("gui.noellesroles.radio.offline");
        g.drawCenteredString(this.font,
                status.copy().withStyle(currentChannel >= RadioItem.MIN_CHANNEL ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                this.width / 2, py + PANEL_H - 44, currentChannel >= RadioItem.MIN_CHANNEL ? GREEN : MUTED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
