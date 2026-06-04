package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.packet.MafiaActionC2SPacket;

import java.util.UUID;

public class GodfatherRecruitScreen extends Screen {
    private static final int COLOR_MAFIOSO = 0xFFDA70D6;
    private static final int COLOR_JANITOR = 0xFFFF69B4;
    private static final int COLOR_NUTRITIONIST = 0xFF32CD32;
    private static final int COLOR_PARASOL = 0xFF008B8B;

    public GodfatherRecruitScreen() {
        super(Component.translatable("screen.noellesroles.godfather.recruit"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int midY = height / 2 - 50;
        int bw = 100, bh = 50, gap = 10;

        // 上排: 家族教徒 (左) | 家族侍卫 (右)
        addRenderableWidget(Button.builder(
            Component.translatable("role.noellesroles.mafioso"),
            btn -> sendRecruit(MafiaActionC2SPacket.RECRUIT_MAFIOSO))
            .pos(cx - bw - gap / 2, midY).size(bw, bh).build());

        addRenderableWidget(Button.builder(
            Component.translatable("role.noellesroles.janitor"),
            btn -> sendRecruit(MafiaActionC2SPacket.RECRUIT_JANITOR))
            .pos(cx + gap / 2, midY).size(bw, bh).build());

        // 下排: 家族调理师 (左) | 家族保护伞 (右)
        addRenderableWidget(Button.builder(
            Component.translatable("role.noellesroles.nutritionist"),
            btn -> sendRecruit(MafiaActionC2SPacket.RECRUIT_NUTRITIONIST))
            .pos(cx - bw - gap / 2, midY + bh + gap).size(bw, bh).build());

        addRenderableWidget(Button.builder(
            Component.translatable("role.noellesroles.parasol"),
            btn -> sendRecruit(MafiaActionC2SPacket.RECRUIT_PARASOL))
            .pos(cx + gap / 2, midY + bh + gap).size(bw, bh).build());

        // Close button
        addRenderableWidget(Button.builder(
            Component.translatable("gui.cancel"),
            btn -> onClose())
            .pos(cx - 50, midY + bh * 2 + gap * 2 + 10).size(100, 20).build());
    }

    private void sendRecruit(int action) {
        Minecraft client = Minecraft.getInstance();
        if (client.crosshairPickEntity instanceof Player target && client.player != null) {
            ClientPlayNetworking.send(new MafiaActionC2SPacket(action, target.getUUID()));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, title, width / 2, height / 2 - 80, 0xFFFFFF);
    }
}
