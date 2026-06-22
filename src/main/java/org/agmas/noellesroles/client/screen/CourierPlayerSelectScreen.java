package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.item.CourierMailData;
import org.agmas.noellesroles.content.item.CourierMailItem;
import org.agmas.noellesroles.packet.CourierMailSendC2SPacket;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 选择收信玩家头像页面 */
public class CourierPlayerSelectScreen extends Screen {
    private final InteractionHand hand;
    private final String message;
    private final int effect;
    private final int itemSlot;
    private final List<PlayerEntry> candidates = new ArrayList<>();

    private record PlayerEntry(UUID uuid, String name, ResourceLocation skin) {}

    public CourierPlayerSelectScreen(InteractionHand hand, String message, int effect, int itemSlot) {
        super(Component.translatable("screen.noellesroles.courier.select_player"));
        this.hand = hand;
        this.message = message;
        this.effect = effect;
        this.itemSlot = itemSlot;
    }

    @Override
    protected void init() {
        candidates.clear();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) { onClose(); return; }
        if (minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                ResourceLocation skin = info.getSkin() != null ? info.getSkin().texture() : DefaultPlayerSkin.get(info.getProfile().getId()).texture();
                candidates.add(new PlayerEntry(info.getProfile().getId(), info.getProfile().getName(), skin));
            }
        }
        candidates.sort(Comparator.comparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
    }

    private void sendTo(UUID target) {
        byte[] msgBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ClientPlayNetworking.send(new CourierMailSendC2SPacket(hand == InteractionHand.MAIN_HAND, target, msgBytes, effect, itemSlot));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFE6C37A);

        int size = 32, spacing = 8, cols = Math.min(8, Math.max(1, candidates.size()));
        int totalW = cols * (size + spacing) - spacing;
        int sx = (width - totalW) / 2, sy = 50;

        for (int i = 0; i < candidates.size(); i++) {
            int col = i % 8, row = i / 8;
            int x = sx + col * (size + spacing), y = sy + row * (size + spacing);
            PlayerEntry e = candidates.get(i);
            boolean hover = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;

            int bg = hover ? new Color(110, 75, 40, 210).getRGB() : new Color(65, 45, 25, 170).getRGB();
            g.fill(x - 2, y - 2, x + size + 2, y + size + 2, bg);
            g.renderOutline(x - 2, y - 2, size + 4, size + 4, hover ? 0xDDB478 : 0x9B7346);

            if (e.skin != null) {
                PlayerFaceRenderer.draw(g, e.skin, x, y, size);
            }

            if (hover) {
                g.renderTooltip(font, Component.literal(e.name), x, y - 12);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int size = 32, spacing = 8, cols = Math.min(8, Math.max(1, candidates.size()));
        int totalW = cols * (size + spacing) - spacing;
        int sx = (width - totalW) / 2, sy = 50;
        for (int i = 0; i < candidates.size(); i++) {
            int col = i % 8, row = i / 8;
            int x = sx + col * (size + spacing), y = sy + row * (size + spacing);
            if (mx >= x && mx <= x + size && my >= y && my <= y + size) {
                sendTo(candidates.get(i).uuid);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }
}
