package org.agmas.noellesroles.role.touhou.roles;

import java.util.ArrayList;

import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import io.wifi.starrailexpress.util.ShopEntry;

public class THShikieikiRole extends TouhouRole {
    public THShikieikiRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(TMMItems.DERRINGER.getDefaultInstance());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        ArrayList<ShopEntry> SHOP = new ArrayList<>();
        {
            // "itemstack.shikieiki.bullet.item_name"
            var t = TMMItems.MAGNUM_BULLET.getDefaultInstance();
            t.set(DataComponents.ITEM_NAME, Component.translatable("itemstack.shikieiki.bullet.item_name"));
            SHOP.add(new ShopEntry(t,
                    250, ShopEntry.Type.WEAPON) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    return super.canBuy(player);
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (!GameUtils.haveUsedoutDerringer(player)) {
                        this.setFailedMessage(Component.translatable("message.tip.purchase_failed.derringer_full"));
                        return false;
                    }
                    return GameUtils.refillDerringer(player, true);
                }
            });
        }
        {
            // "itemstack.shikieiki.bullet.item_name"
            var t = TMMItems.MAGNUM_BULLET.getDefaultInstance();
            t.set(DataComponents.ITEM_NAME, Component.translatable("itemstack.shikieiki.alive_or_dead_book.item_name"));
            t.set(DataComponents.LORE, new ItemLore(
                    List.of(Component.translatable("itemstack.shikieiki.alive_or_dead_book.item_lore.1"))));
            SHOP.add(new ShopEntry(t,
                    400, ShopEntry.Type.WEAPON) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    var cca = SREAbilityPlayerComponent.KEY.get(player);
                    if (cca.status == 1) {
                        return false;
                    }
                    return true;
                }

                @Override
                public boolean canDisplay(@NotNull Player player) {
                    return super.canBuy(player);
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    var cca = SREAbilityPlayerComponent.KEY.get(player);
                    if (cca.status == 1) {
                        return false;
                    }
                    cca.status = 1;
                    player.displayClientMessage(Component.translatable("message.shikieiki.alive_or_dead_book.bought")
                            .withStyle(ChatFormatting.GREEN), true);
                    return true;
                }
            });
        }
        return SHOP;
    }

}
