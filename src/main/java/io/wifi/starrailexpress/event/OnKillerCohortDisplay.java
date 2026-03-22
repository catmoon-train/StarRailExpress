package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnKillerCohortDisplay {

    /**
     * Callback for determining whether an {@link ItemStack} should drop when player
     * died
     */
    Event<OnKillerCohortDisplay> EVENT = createArrayBacked(OnKillerCohortDisplay.class,
            listeners -> (p) -> {
                MutableComponent result = null;
                for (OnKillerCohortDisplay listener : listeners) {
                    result = listener.onCohortRender(p);
                    if (result != null) {
                        return result;
                    }
                }
                return null;
            });

    MutableComponent onCohortRender(Player target);
}
