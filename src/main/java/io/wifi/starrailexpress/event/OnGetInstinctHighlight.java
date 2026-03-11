package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.Entity;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnGetInstinctHighlight {

    /**
     * Callback for highlight color.
     * -1 for no change (default),
     * -2 for no instinct.
     */
    Event<OnGetInstinctHighlight> EVENT = createArrayBacked(OnGetInstinctHighlight.class,
            listeners -> (stack, isInstinctEnabled) -> {
                for (OnGetInstinctHighlight listener : listeners) {
                    int color = listener.GetInstinctHighlight(stack, isInstinctEnabled);
                    if (color != -1) {
                        return color;
                    }
                }
                return -1;
            });

    int GetInstinctHighlight(Entity target, boolean isInstinctEnabled);
}
