package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：判断物品在玩家死亡时是否应掉落。
 * 若任意监听器返回 {@code true}，则该物品会掉落。
 *
 * <p>Event interface to determine whether an {@link ItemStack} should drop when a player dies.
 * If any listener returns {@code true}, the item will be dropped.
 */
public interface ShouldDropOnDeath {

    /**
     * 判断物品在玩家死亡时是否应掉落的事件。
     * 任意监听器返回 {@code true} 即触发掉落。
     *
     * <p>Callback for determining whether an {@link ItemStack} should drop when the player dies.
     * Any listener returning {@code true} causes the item to drop.
     */
    Event<ShouldDropOnDeath> EVENT = createArrayBacked(ShouldDropOnDeath.class, listeners -> stack -> {
        for (ShouldDropOnDeath listener : listeners) {
            if (listener.shouldDrop(stack)) {
                return true;
            }
        }
        return false;
    });

    /**
     * 判断指定物品是否应在玩家死亡时掉落。
     *
     * <p>Determines whether the given item stack should drop when the player dies.
     *
     * @param stack 需要判断的物品 / the item stack to check
     * @return {@code true} 若该物品应掉落 / {@code true} if the item should drop
     */
    boolean shouldDrop(ItemStack stack);
}
