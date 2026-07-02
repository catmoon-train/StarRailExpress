package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.item.LockpickItem;
import net.minecraft.world.item.Item;

public class InferiorLockpickItem extends LockpickItem {
    public static final int COOLDOWN_TICKS = 15 * 20;

    public InferiorLockpickItem(Item.Properties settings) {
        super(settings);
    }
}
