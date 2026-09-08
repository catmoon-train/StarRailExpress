package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.innocence.insurance.InsuranceReason;
import org.agmas.noellesroles.packet.InsuranceSubmitC2SPacket;

/** Eight-option insurance contract page. Pressing Esc closes it without signing. */
public final class InsuranceScreen extends AbstractPixelScreen {
    private static final int PANEL_W = 512;
    private static final int PANEL_H = 341;
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "textures/gui/insurance.png");

    private final InteractionHand hand;
    private InsuranceReason selected;
    private Button confirm;

    public InsuranceScreen(InteractionHand hand) {
        super(Component.translatable("screen.noellesroles.insurance.title"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        InsuranceReason[] reasons = InsuranceReason.values();
        for (int i = 0; i < reasons.length; i++) {
            InsuranceReason reason = reasons[i];
            int column = i % 3;
            int row = i / 3;
            addRenderableWidget(Button.builder(reasonText(reason), button -> {
                selected = reason;
                if (confirm != null) confirm.active = true;
            }).bounds(left + 65 + column * 134, top + 140 + row * 29, 116, 22).build());
        }
        confirm = addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.insurance.confirm"),
                button -> submit()).bounds(left + 198, top + 294, 116, 22).build());
        confirm.active = false;
    }

    private Component reasonText(InsuranceReason reason) {
        return Component.translatable("screen.noellesroles.insurance.reason." + reason.id());
    }

    private void submit() {
        if (selected == null) return;
        ClientPlayNetworking.send(new InsuranceSubmitC2SPacket(hand, selected.id()));
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        graphics.blit(BACKGROUND, left, top, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        graphics.drawString(font, Component.translatable("screen.noellesroles.insurance.title"),
                left + 198, top + 15, 0xF3D18B, false);
        graphics.drawCenteredString(font,
                Component.translatable("screen.noellesroles.insurance.instruction"),
                left + PANEL_W / 2, top + 91, 0xE8DCC0);
        Component selectedText = selected == null
                ? Component.translatable("screen.noellesroles.insurance.selected.none")
                : Component.translatable("screen.noellesroles.insurance.selected",
                        reasonText(selected));
        graphics.drawCenteredString(font, selectedText,
                left + PANEL_W / 2, top + 278, 0xF3D18B);
        graphics.drawString(font, Component.translatable("screen.noellesroles.insurance.esc"),
                left + 22, top + 323, 0xC7B89A, false);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
