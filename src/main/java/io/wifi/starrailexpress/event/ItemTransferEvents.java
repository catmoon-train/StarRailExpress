package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 原地合堆前的通用物品插入许可，不允许监听器修改待插入栈。 */
public final class ItemTransferEvents {
    private ItemTransferEvents() {
    }

    public static final Event<AllowInsert> ALLOW_HOTBAR_INSERT = EventFactory.createArrayBacked(AllowInsert.class,
            listeners -> (player, stack) -> {
                for (AllowInsert listener : listeners) if (!listener.allow(player, stack)) return false;
                return true;
            });

    @FunctionalInterface
    public interface AllowInsert {
        boolean allow(Player player, ItemStack stack);
    }
}
