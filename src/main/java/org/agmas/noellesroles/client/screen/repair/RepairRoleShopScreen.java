package org.agmas.noellesroles.client.screen.repair;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;
import org.agmas.noellesroles.packet.RepairRoleShopPurchaseC2SPacket;

import java.util.LinkedHashSet;
import java.util.Set;

public class RepairRoleShopScreen extends Screen {
    private int skinCoins;
    private final Set<String> ownedRoles = new LinkedHashSet<>();
    private RepairRoleDefinition previewRole = RepairRoleDefinition.MECHANIC;

    public RepairRoleShopScreen(int skinCoins, java.util.List<String> ownedRoles) {
        super(Component.translatable("screen.noellesroles.repair_shop.title"));
        updateData(skinCoins, ownedRoles);
    }

    public void updateData(int skinCoins, java.util.List<String> ownedRoles) {
        this.skinCoins = skinCoins;
        this.ownedRoles.clear();
        this.ownedRoles.addAll(ownedRoles);
    }

    @Override
    protected void init() {
        rebuildButtons();
    }

    private void rebuildButtons() {
        clearWidgets();
        int cardW = 94;
        int cardH = 24;
        int startX = width / 2 - 150;
        int startY = height / 2 - 42;
        int index = 0;
        for (RepairRoleDefinition role : RepairRoleDefinition.values()) {
            int x = startX + (index % 3) * (cardW + 8);
            int y = startY + (index / 3) * (cardH + 8);
            Button button = Button.builder(role.displayName(), ignored -> {
                previewRole = role;
                if (!isOwned(role) && !role.starter) {
                    ClientPlayNetworking.send(new RepairRoleShopPurchaseC2SPacket(role.id));
                }
            }).bounds(x, y, cardW, cardH).build();
            button.active = !isOwned(role) && !role.starter && skinCoins >= RepairRoleDefinition.UNLOCK_PRICE;
            addRenderableWidget(button);
            index++;
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {

    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {


        graphics.fill(width / 2 - 184, height / 2 - 108, width / 2 + 184, height / 2 + 112, 0xDD120905);
        graphics.fill(width / 2 - 180, height / 2 - 104, width / 2 + 180, height / 2 + 108, 0xCC241109);
        graphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.GOLD), width / 2, height / 2 - 96,
                0xFFFFE3A0);
        graphics.drawCenteredString(font, Component.translatable("screen.noellesroles.repair_shop.coins", skinCoins),
                width / 2, height / 2 - 82, 0xFFFFD166);

        drawPreview(graphics);

        drawRoleFrames(graphics);
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawPreview(GuiGraphics graphics) {
        int x = width / 2 - 170;
        int y = height / 2 + 62;
        boolean owned = isOwned(previewRole);
        graphics.drawString(font, previewRole.displayName().copy().withStyle(ChatFormatting.BOLD), x, y,
                owned ? 0xFFB7E4A6 : 0xFFFFD166);
        graphics.drawWordWrap(font, previewRole.description(), x, y + 14, 220, 0xFFE6D4B0);
        Component state = owned || previewRole.starter
                ? Component.translatable("screen.noellesroles.repair_shop.owned")
                : Component.translatable("screen.noellesroles.repair_shop.price", RepairRoleDefinition.UNLOCK_PRICE);
        graphics.drawString(font, state, x + 236, y, owned || previewRole.starter ? 0xFFB7E4A6 : 0xFFFF8A80);
    }

    private void drawRoleFrames(GuiGraphics graphics) {
        int cardW = 94;
        int cardH = 24;
        int startX = width / 2 - 150;
        int startY = height / 2 - 42;
        int index = 0;
        for (RepairRoleDefinition role : RepairRoleDefinition.values()) {
            int x = startX + (index % 3) * (cardW + 8);
            int y = startY + (index / 3) * (cardH + 8);
            int color = isOwned(role) || role.starter ? 0xFFB7E4A6 : 0xFF8C5E36;
            if (role == previewRole) {
                graphics.fill(x - 2, y - 2, x + cardW + 2, y, color);
                graphics.fill(x - 2, y + cardH, x + cardW + 2, y + cardH + 2, color);
                graphics.fill(x - 2, y - 2, x, y + cardH + 2, color);
                graphics.fill(x + cardW, y - 2, x + cardW + 2, y + cardH + 2, color);
            }
            index++;
        }
    }

    private boolean isOwned(RepairRoleDefinition role) {
        return role.starter || ownedRoles.contains(role.id);
    }
}
