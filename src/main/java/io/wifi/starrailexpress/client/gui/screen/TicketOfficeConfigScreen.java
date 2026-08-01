/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.gui.screen;

import io.wifi.starrailexpress.network.TicketPayload;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public class TicketOfficeConfigScreen extends Screen {
    private final BlockPos pos;
    private final CompoundTag data;
    private EditBox priceBox;
    private EditBox nameBox;
    private EditBox usesBox;
    private EditBox maxPurchasesBox;
    private ShopEntry.Currency currency;

    public TicketOfficeConfigScreen(BlockPos pos, CompoundTag data) {
        super(Component.translatable("screen.starrailexpress.ticket_office.config"));
        this.pos = pos;
        this.data = data.copy();
        this.currency = ShopEntry.Currency.fromSerializedName(data.getString("Currency"));
    }

    @Override
    protected void init() {
        int panelX = this.width / 2 - 120;
        int y = this.height / 2 - 86;
        this.nameBox = new EditBox(this.font, panelX + 86, y, 150, 20, Component.empty());
        this.nameBox.setMaxLength(64);
        this.nameBox.setValue(data.getString("TicketName"));
        this.priceBox = new EditBox(this.font, panelX + 86, y + 30, 72, 20, Component.empty());
        this.priceBox.setValue(String.valueOf(Math.max(0, data.getInt("Price"))));
        this.usesBox = new EditBox(this.font, panelX + 86, y + 60, 72, 20, Component.empty());
        this.usesBox.setValue(String.valueOf(Math.max(1, data.contains("Uses") ? data.getInt("Uses") : 1)));
        this.maxPurchasesBox = new EditBox(this.font, panelX + 86, y + 90, 72, 20, Component.empty());
        this.maxPurchasesBox.setValue(String.valueOf(data.contains("MaxPurchases") ? data.getInt("MaxPurchases") : -1));
        addRenderableWidget(nameBox);
        addRenderableWidget(priceBox);
        addRenderableWidget(usesBox);
        addRenderableWidget(maxPurchasesBox);
        addRenderableWidget(Button.builder(Component.translatable(currency.priceTranslationKey()), b -> {
            this.currency = this.currency == ShopEntry.Currency.MONEY
                    ? ShopEntry.Currency.MINIGAME_TOKEN
                    : ShopEntry.Currency.MONEY;
            b.setMessage(Component.translatable(this.currency.priceTranslationKey()));
        }).bounds(panelX + 164, y + 30, 72, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> saveAndClose())
                .bounds(panelX + 40, y + 120, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(panelX + 126, y + 120, 76, 20).build());
    }

    private void saveAndClose() {
        CompoundTag tag = data.copy();
        tag.putString("TicketName", nameBox.getValue().trim());
        tag.putInt("Price", parseInt(priceBox.getValue(), 0));
        tag.putInt("Uses", Math.max(1, parseInt(usesBox.getValue(), tag.contains("Uses") ? tag.getInt("Uses") : 1)));
        tag.putInt("MaxPurchases", parseInt(maxPurchasesBox.getValue(), tag.contains("MaxPurchases") ? tag.getInt("MaxPurchases") : -1));
        tag.putString("Currency", currency.serializedName());
        ClientPlayNetworking.send(new TicketPayload.SaveOfficeConfig(pos, tag));
        onClose();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int panelX = this.width / 2 - 120;
        int y = this.height / 2 - 86;
        graphics.fill(panelX, y - 30, panelX + 240, y + 152, 0xEE1D2328);
        graphics.drawString(font, title, panelX + 12, y - 20, 0xFFE8F1F2, false);
        graphics.drawString(font, Component.translatable("screen.starrailexpress.ticket_office.name"), panelX + 12, y + 6,
                0xFFC8D4D8, false);
        graphics.drawString(font, Component.translatable("screen.starrailexpress.ticket_office.price"), panelX + 12, y + 36,
                0xFFC8D4D8, false);
        graphics.drawString(font, Component.translatable("screen.starrailexpress.ticket_office.uses"), panelX + 12, y + 66,
                0xFFC8D4D8, false);
        graphics.drawString(font, Component.translatable("screen.starrailexpress.ticket_office.max_purchases"),
                panelX + 12, y + 96, 0xFFC8D4D8, false);
    }
}
