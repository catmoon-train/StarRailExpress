package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface AllowItemShowInHand {

    /**
     * ClientOnly!
     */
    Event<AllowItemShowInHand> EVENT = createArrayBacked(AllowItemShowInHand.class,
            listeners -> (player, itemStack) -> {
                for (AllowItemShowInHand listener : listeners) {
                    var a = listener.allowShowInHand(player, itemStack);
                    if (a != null) {
                        return a;
                    }
                }
                return null;
            });

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    ItemStack allowShowInHand(Player player, ItemStack itemStack);
}