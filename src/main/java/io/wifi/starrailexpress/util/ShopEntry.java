package io.wifi.starrailexpress.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

public class ShopEntry extends dev.doctor4t.wathe.util.ShopEntry {
    public ShopEntry(ItemStack stack, int price, dev.doctor4t.wathe.util.ShopEntry.Type type) {
        super(stack, price, type);
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        return RoleUtils.insertStackInFreeSlot(player, this.stack().copy());
    }

    @Override
    public ItemStack stack() {
        return super.stack();
    }

    public boolean canBuy(@NotNull Player player) {
        return true;
    }
}