package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import io.wifi.starrailexpress.util.TrueFalseResult;

/**
 * 事件接口：判断手枪射击击中时是否允许掉落物品（例如目标的物品）。
 * 采用首次非 PASS 结果优先语义：监听器按注册顺序依次被调用，
 * 第一个返回非 PASS 结果的监听器决定最终结果。
 *
 * <p>Event interface to determine whether items should drop when a revolver shot hits.
 * Uses first-non-PASS semantics: listeners are invoked in registration order and
 * the first listener that returns a non-PASS result determines the outcome.
 */
public interface AllowShootRevolverDrop {

    /**
     * 判断手枪射击击中时是否允许掉落物品的事件。
     * 采用首次非 PASS 结果优先语义。
     *
     * <p>Event to determine if items should drop when a revolver shot hits a target.
     * Uses first-non-PASS semantics.
     */
    Event<AllowShootRevolverDrop> EVENT = createArrayBacked(AllowShootRevolverDrop.class,
            listeners -> (player, target) -> {
                for (AllowShootRevolverDrop listener : listeners) {
                    var re = listener.allowDrop(player, target);
                    if (re != null && re != TrueFalseResult.PASS) {
                        return re;
                    }
                }
                return TrueFalseResult.PASS;
            });

    /**
     * 判断射击者击中目标时是否允许掉落物品。
     *
     * <p>Determines whether items are allowed to drop when the shooter hits the target.
     *
     * @param player 射击者 / the player who fired the shot
     * @param target 被击中的玩家 / the player who was hit
     * @return 掉落决策结果 / the drop decision result
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    TrueFalseResult allowDrop(Player player, Player target);
}