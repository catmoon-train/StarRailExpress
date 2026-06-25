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
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 160;
    private static final int HEADER_H = 24;

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

    private int left() { return (width - PANEL_W) / 2; }
    private int top()  { return (height - PANEL_H) / 2; }

    @Override
    protected void init() {
        int l = left(), t = top();
        addRenderableWidget(Button.builder(Component.translatable("screen.starrailexpress.ticket_office.buy"), b -> {
            ClientPlayNetworking.send(new TicketPayload.BuyTicket(pos));
            onClose();
        }).bounds(l + 45, t + 120, 130, 22).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int l = left(), t = top();

        MinigameUI.panel(g, l, t, l + PANEL_W, t + PANEL_H, HEADER_H);
        g.drawCenteredString(font, title, width / 2, t + 8, MinigameUI.WHITE);

        int sepY = t + HEADER_H + 6;
        g.fill(l + 20, sepY, l + PANEL_W - 20, sepY + 1, 0xFF3A4A60);

        // 入场券图标（居中放大）
        int ticketX = l + PANEL_W / 2 - 12;
        int ticketY = t + 36;
        g.pose().pushPose();
        g.pose().translate(ticketX + 8, ticketY + 8, 0);
        g.pose().scale(2.5f, 2.5f, 1f);
        g.renderItem(ticket, -8, -8);
        g.pose().popPose();
        String ticketName = data.getString("TicketName");
        g.drawCenteredString(font,
                ticketName.isBlank() ? Component.translatable("item.starrailexpress.admission_ticket")
                        : Component.literal(ticketName),
                width / 2, ticketY + 40, 0xFFFFFFFF);

        // 价格行：用翻译键渲染（含自定义字体货币图标）
        int priceY = t + 84;
        Component priceText = Component.translatable(currency.priceTranslationKey(), data.getInt("Price"));
        g.drawString(font, priceText, l + 28, priceY + 1, currency.color(), false);

        // 使用次数
        int uses = data.getInt("Uses");
        Component useText = uses < 0
                ? Component.translatable("tooltip.starrailexpress.admission_ticket.infinite")
                : Component.translatable("tooltip.starrailexpress.admission_ticket.uses", uses);
        g.drawString(font, useText, l + 28, t + 104, 0xFFA0B0C0, false);

        g.fill(l + 20, t + 116, l + PANEL_W - 20, t + 117, 0xFF3A4A60);
    }
}
