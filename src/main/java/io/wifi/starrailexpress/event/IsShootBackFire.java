package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：判断某次枪击是否会对射击者造成反伤（自伤）。
 * 若任意监听器返回 {@code true}，则该次射击触发反伤。
 *
 * <p>Event interface to determine whether a revolver shot causes backfire damage to the shooter.
 * If any listener returns {@code true}, the shot causes backfire.
 */
public interface IsShootBackFire {

    /**
     * 判断枪击是否触发反伤的事件。
     * 任意监听器返回 {@code true} 即触发反伤。
     *
     * <p>Event callback to determine if a revolver shot causes backfire to the shooter.
     * Any listener returning {@code true} triggers backfire.
     */
    Event<IsShootBackFire> EVENT = createArrayBacked(IsShootBackFire.class, listeners -> (player, target) -> {
        for (IsShootBackFire listener : listeners) {
            if (listener.isShootBackFire(player, target)) {
                return true;
            }
        }
        return false;
    });

    /**
     * 判断指定射击者对目标射击时是否触发反伤。
     *
     * <p>Determines whether the given shooter's shot at the target causes backfire.
     *
     * @param player 射击者 / the player who fired
     * @param target 被射击的目标玩家 / the target player
     * @return {@code true} 若触发反伤 / {@code true} if the shot causes backfire
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isShootBackFire(Player player, Player target);
}