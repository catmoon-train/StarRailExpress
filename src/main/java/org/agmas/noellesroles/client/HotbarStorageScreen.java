package org.agmas.noellesroles.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class HotbarStorageScreen extends AbstractContainerScreen<HotbarStorageMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int CONTAINER_HEIGHT = 71;
    private static final int HOTBAR_BACKGROUND_Y = CONTAINER_HEIGHT;
    private static final int HOTBAR_TEXTURE_Y = 190;
    private static final int HOTBAR_BACKGROUND_HEIGHT = 32;

    public HotbarStorageScreen(HotbarStorageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = HOTBAR_BACKGROUND_Y + HOTBAR_BACKGROUND_HEIGHT;
        this.inventoryLabelY = 10000;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        graphics.blit(TEXTURE, left, top, 0, 0, this.imageWidth, CONTAINER_HEIGHT);
        graphics.blit(TEXTURE, left, top + HOTBAR_BACKGROUND_Y, 0, HOTBAR_TEXTURE_Y,
                this.imageWidth, HOTBAR_BACKGROUND_HEIGHT);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
