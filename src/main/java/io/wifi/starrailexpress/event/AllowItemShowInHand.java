package io.wifi.starrailexpress.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口（仅客户端）：决定玩家手持的物品是否应在手部渲染显示。
 * 首个非 null 结果优先；若所有监听器均返回 null，则使用默认物品。
 *
 * <p>Client-only event interface that determines which {@link ItemStack} should be rendered
 * in the player's hand. First non-null result wins; if all listeners return null, the default
 * item is used.
 */
@Environment(EnvType.CLIENT)
public interface AllowItemShowInHand {

    /**
     * 决定手部渲染物品的事件（仅客户端）。
     * 首个非 null 结果优先。
     *
     * <p>Client-only event to determine the item rendered in hand.
     * First non-null result wins.
     */
    Event<AllowItemShowInHand> EVENT = createArrayBacked(AllowItemShowInHand.class,
            listeners -> (player, itemStack, mainHand) -> {
                for (AllowItemShowInHand listener : listeners) {
                    var a = listener.allowShowInHand(player, itemStack, mainHand);
                    if (a != null) {
                        return a;
                    }
                }
                return null;
            });

    /**
     * 判断指定玩家手持的物品应渲染为何物。
     *
     * <p>Determines the {@link ItemStack} that should be rendered in the player's hand.
     *
     * @param player    持有物品的玩家 / the player holding the item
     * @param itemStack 当前手持的物品 / the currently held item stack
     * @param mainHand  {@code true} 表示主手，{@code false} 表示副手 /
     *                  {@code true} for main hand, {@code false} for off hand
     * @return 应渲染的物品，若不覆盖则返回 null / the item stack to render, or null to not override
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    ItemStack allowShowInHand(Player player, ItemStack itemStack, boolean mainHand);
}