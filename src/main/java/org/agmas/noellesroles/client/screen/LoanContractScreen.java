package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.LoanContractOpenS2CPacket;
import org.agmas.noellesroles.packet.LoanContractSubmitC2SPacket;

import java.util.UUID;

/** Contract form shown after the borrower accepts a lender's request. */
public final class LoanContractScreen extends AbstractPixelScreen {
    private static final int PANEL_W = 512;
    private static final int PANEL_H = 341;
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            Noellesroles.MOD_ID, "textures/gui/loan_contract.png");

    private final UUID lender;
    private EditBox amount;

    public LoanContractScreen(LoanContractOpenS2CPacket payload) {
        super(Component.translatable("screen.noellesroles.loan_contract.title"));
        this.lender = payload.lender();
    }

    @Override
    protected void init() {
        super.init();
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        amount = new EditBox(font, left + 142, top + 128, 150, 24,
                Component.translatable("screen.noellesroles.loan_contract.amount"));
        amount.setValue("25");
        amount.setMaxLength(3);
        amount.setFilter(value -> value.matches("\\d*"));
        addRenderableWidget(amount);
        addRenderableWidget(Button.builder(Component.translatable("screen.noellesroles.loan_contract.confirm"),
                button -> submit()).bounds(left + 180, top + 215, 152, 24).build());
        setInitialFocus(amount);
    }

    private void submit() {
        try {
            int value = Integer.parseInt(amount.getValue());
            ClientPlayNetworking.send(new LoanContractSubmitC2SPacket(lender, value));
            Minecraft.getInstance().setScreen(null);
        } catch (NumberFormatException ignored) {
            amount.setValue("");
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        int left = (width - PANEL_W) / 2;
        int top = (height - PANEL_H) / 2;
        graphics.blit(BACKGROUND, left, top, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        graphics.drawString(font, Component.translatable("screen.noellesroles.loan_contract.title"),
                left + 28, top + 28, 0x4B2818, false);
        graphics.drawString(font, Component.translatable("screen.noellesroles.loan_contract.amount"),
                left + 142, top + 112, 0x4B2818, false);
        graphics.drawString(font, Component.translatable("screen.noellesroles.loan_contract.limit", 175),
                left + 142, top + 158, 0x7D3A30, false);
        graphics.drawString(font, Component.translatable("screen.noellesroles.loan_contract.terms"),
                left + 28, top + 278, 0x4B2818, false);
        graphics.drawString(font, Component.translatable("screen.noellesroles.loan_contract.esc"),
                left + 28, top + 298, 0x6B5645, false);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
