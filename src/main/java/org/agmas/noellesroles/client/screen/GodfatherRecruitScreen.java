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

    public GodfatherRecruitScreen() {
        super(Component.translatable("screen.noellesroles.godfather.recruit"));
    }

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int midY = height / 2 - 40;

        // Mafioso button
        addRenderableWidget(Button.builder(
            Component.translatable("role.noellesroles.mafioso"),
            btn -> sendRecruit(MafiaActionC2SPacket.RECRUIT_MAFIOSO))
            .pos(cx - 160, midY).size(100, 60).build());

        // Janitor button
        addRenderableWidget(Button.builder(
            Component.translatable("role.noellesroles.janitor"),
            btn -> sendRecruit(MafiaActionC2SPacket.RECRUIT_JANITOR))
            .pos(cx - 50, midY).size(100, 60).build());

        // Nutritionist button
        addRenderableWidget(Button.builder(
            Component.translatable("role.noellesroles.nutritionist"),
            btn -> sendRecruit(MafiaActionC2SPacket.RECRUIT_NUTRITIONIST))
            .pos(cx + 60, midY).size(100, 60).build());

        // Close button
        addRenderableWidget(Button.builder(
            Component.translatable("gui.cancel"),
            btn -> onClose())
            .pos(cx - 50, midY + 80).size(100, 20).build());
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
