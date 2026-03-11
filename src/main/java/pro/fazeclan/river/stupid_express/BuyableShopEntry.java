package pro.fazeclan.river.stupid_express;

import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class BuyableShopEntry extends ShopEntry {
    public BuyableShopEntry(ItemStack stack, int price, Type type) {
        super(stack, price, type);
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        return insertStackInFreeSlot(player, stack().copy());
    }
}
