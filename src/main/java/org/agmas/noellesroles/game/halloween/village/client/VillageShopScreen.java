package org.agmas.noellesroles.game.halloween.village.client;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.halloween.HalloweenItems;
import org.agmas.noellesroles.game.halloween.village.VillagePackets;

/** 老式车票风格商店；价格来自服务端，客户端不做扣费或发物品。 */
@Environment(EnvType.CLIENT)
public final class VillageShopScreen extends Screen {
    private static final int TEXT = 0xFFFFF4DC, GOLD = 0xFFD4AF37, MUTED = 0xFF9E8B6E;
    private final VillagePackets.Open shop;
    private int x, y, w, h, scroll;
    private boolean pending;
    private float hover;

    public VillageShopScreen(VillagePackets.Open shop) {
        super(Component.translatable("halloween.village.shop_title"));
        this.shop = shop;
    }
    @Override protected void init() {
        w = Math.min(700, Math.max(180, (int) (width * .9)));
        h = Math.min(height - 16, Mth.clamp((int) (height * .78), 180, 360));
        x = (width - w) / 2; y = (height - h) / 2;
    }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(x, y, x + w, y + h, 0xD81A1008, 0xD820140A);
        g.renderOutline(x, y, w, h, 0xFF8B6914);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33FFE8C0);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, x + 10, y + 10, GOLD, false);
        if (minecraft.player != null) {
            int pumpkins = minecraft.player.getInventory().items.stream().filter(s -> s.is(HalloweenItems.PUMPKIN)).mapToInt(ItemStack::getCount).sum();
            int coins = SREPlayerShopComponent.KEY.get(minecraft.player).balance;
            g.drawString(font, Component.translatable("halloween.village.balance", pumpkins, coins), x + 10, y + 26, TEXT, false);
        }
        int top = y + 43, bottom = y + h - 39;
        g.enableScissor(x + 5, top, x + w - 5, bottom);
        int hovered = rowAt(mouseX, mouseY);
        hover += ((hovered >= 0 && !pending ? 1 : 0) - hover) * .22F;
        for (int i = 0; i < shop.offers().size(); i++) {
            int rowY = top + i * 42 - scroll;
            if (rowY + 40 < top || rowY >= bottom) continue;
            var offer = shop.offers().get(i);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(offer.item()));
            boolean active = hovered == i && !pending;
            g.fill(x + 7, rowY, x + w - 7, rowY + 39, active ? ((int) (hover * 45) << 24) | 0xC9A84C : 0x331A1008);
            g.renderOutline(x + 7, rowY, w - 14, 39, active ? GOLD : 0xFF5A4530);
            g.renderItem(stack, x + 13, rowY + 10);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable("halloween.village.product", stack.getHoverName(), offer.count()).getString(), w - 114),
                    x + 36, rowY + 7, TEXT, false);
            g.drawString(font, Component.translatable("halloween.village.price", offer.pumpkins(), offer.coins()), x + 36, rowY + 23, GOLD, false);
            g.drawString(font, Component.translatable(pending ? "halloween.village.wait" : "halloween.village.buy"), x + w - 65, rowY + 15, pending ? MUTED : GOLD, false);
        }
        if (shop.offers().isEmpty()) g.drawString(font, Component.translatable("halloween.village.no_trades"), x + 12, top + 10, MUTED, false);
        g.disableScissor();
        int content = shop.offers().size() * 42, view = bottom - top;
        if (content > view) {
            int thumb = Math.max(18, view * view / content);
            int barY = top + scroll * (view - thumb) / Math.max(1, content - view);
            g.fill(x + w - 5, barY, x + w - 2, barY + thumb, GOLD);
        }
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("halloween.village.shop_hint").getString(), w - 20), x + 10, y + h - 29, MUTED, false);
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("halloween.village.knock_hint").getString(), w - 20), x + 10, y + h - 16, MUTED, false);
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < x + 7 || mouseX >= x + w - 7 || mouseY < y + 43 || mouseY >= y + h - 39) return -1;
        int row = (int) (mouseY - y - 43 + scroll) / 42;
        return row < shop.offers().size() ? row : -1;
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = rowAt(mouseX, mouseY);
        if (button == 0 && index >= 0 && !pending) {
            pending = true;
            ClientPlayNetworking.send(new VillagePackets.Buy(shop.npc(), shop.nonce(), index));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Mth.clamp(scroll - (int) (vertical * 30), 0, Math.max(0, shop.offers().size() * 42 - (h - 82)));
        return true;
    }
    @Override public void tick() {
        if (minecraft.player == null || minecraft.level == null || minecraft.player.isSpectator()
                || minecraft.level.getEntitiesOfClass(org.agmas.noellesroles.game.halloween.village.VillageNpc.class,
                        minecraft.player.getBoundingBox().inflate(5), npc -> npc.getUUID().equals(shop.npc())).isEmpty()) onClose();
    }
}
