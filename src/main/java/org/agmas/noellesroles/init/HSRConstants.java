package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class HSRConstants {
    public static int toxinPoisonTime = getInTicks(0, 30);
    float banditRevolverDropChance = 0.2F;

    static {

    }

    public static void init() {

    }

    public static int getInTicks(int minutes, int seconds) {
        return (minutes * 60 + seconds) * 20;
    }
}
