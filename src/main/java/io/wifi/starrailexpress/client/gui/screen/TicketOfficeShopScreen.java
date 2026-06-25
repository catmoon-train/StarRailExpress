package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.content.item.AdmissionTicketItem;
import io.wifi.starrailexpress.network.TicketPayload;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public class TicketOfficeShopScreen extends Screen {
    private final BlockPos pos;
    private final CompoundTag data;
    private final ShopEntry.Currency currency;
    private final ItemStack ticket;

    public TicketOfficeShopScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("screen.starrailexpress.ticket_office.shop"));
        this.pos = pos;
        this.data = data.copy();
        this.currency = ShopEntry.Currency.fromSerializedName(data.getString("Currency"));
        this.ticket = AdmissionTicketItem.create(readTicketId(data), data.getString("TicketName"), data.getInt("Uses"));
    }

    private static UUID readTicketId(CompoundTag data) {
        try {
            return UUID.fromString(data.getString("TicketId"));
        } catch (IllegalArgumentException e) {
            return new UUID(0L, 0L);
        }
    }

    @Override
    protected void init() {
        int panelX = this.width / 2 - 96;
        int y = this.height / 2 + 42;
        addRenderableWidget(Button.builder(Component.translatable("screen.starrailexpress.ticket_office.buy"), b -> {
            ClientPlayNetworking.send(new TicketPayload.BuyTicket(pos));
            onClose();
        }).bounds(panelX + 56, y, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int panelX = this.width / 2 - 96;
        int y = this.height / 2 - 82;
        graphics.fill(panelX, y, panelX + 192, y + 154, 0xEE20282E);
        graphics.drawString(font, title, panelX + 14, y + 12, 0xFFEAF5F5, false);
        graphics.renderItem(ticket, panelX + 22, y + 40);
        graphics.drawString(font, Component.literal(data.getString("TicketName")), panelX + 48, y + 44, 0xFFFFFFFF, false);
        graphics.renderItem(currency.iconStack(), panelX + 24, y + 74);
        Component price = Component.literal(String.valueOf(data.getInt("Price")))
                .append(" ")
                .append(Component.translatable(currency.priceTranslationKey()));
        graphics.drawString(font, price, panelX + 48, y + 78, currency.color(), false);
        int uses = data.getInt("Uses");
        Component useText = uses < 0
                ? Component.translatable("tooltip.starrailexpress.admission_ticket.infinite")
                : Component.translatable("tooltip.starrailexpress.admission_ticket.uses", uses);
        graphics.drawString(font, useText, panelX + 24, y + 104, 0xFFC7D4D8, false);
    }
}
