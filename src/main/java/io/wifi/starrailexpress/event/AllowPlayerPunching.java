package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：判断玩家是否被允许进行近战攻击（例如持刀时）。
 * 若任意监听器返回 {@code true}，则允许攻击。
 *
 * <p>Event interface to determine whether a player is allowed to perform a melee punch,
 * for example when holding a knife.
 * If any listener returns {@code true}, the punch is allowed.
 */
public interface AllowPlayerPunching {

    /**
     * 判断玩家是否允许进行近战攻击的事件（例如持刀时）。
     * 任意监听器返回 {@code true} 即允许。
     *
     * <p>Callback for determining whether a player is allowed to punch another player,
     * for example when holding a knife.
     * Any listener returning {@code true} grants permission.
     */
    Event<AllowPlayerPunching> EVENT = createArrayBacked(AllowPlayerPunching.class, listeners -> player -> {
        for (AllowPlayerPunching listener : listeners) {
            if (listener.allowPunching(player)) {
                return true;
            }
        }
        return false;
    });

    /**
     * 判断指定玩家是否被允许进行近战攻击。
     *
     * <p>Determines whether the given player is allowed to perform a melee punch.
     *
     * @param player 尝试进行近战攻击的玩家 / the player attempting to punch
     * @return {@code true} 若允许攻击 / {@code true} if punching is allowed
     */
    boolean allowPunching(Player player);
}
