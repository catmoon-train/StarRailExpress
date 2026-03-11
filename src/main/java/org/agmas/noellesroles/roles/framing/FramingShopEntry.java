package org.agmas.noellesroles.roles.framing;

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class FramingShopEntry extends ShopEntry {

    public FramingShopEntry(ItemStack stack, int price, Type type) {
        super(stack, price, type);
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        return insertStackInFreeSlot(player, stack().copy());
    }
}
