package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.Entity;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：判断实体是否可以被近战攻击（拳击）。
 * 若任意监听器返回 {@code true}，则该实体可被攻击。
 *
 * <p>Event interface to determine whether an entity can be punched (melee attacked).
 * If any listener returns {@code true}, the entity is punchable.
 */
public interface IsPlayerPunchable {

    /**
     * 判断实体是否可被近战攻击的事件。
     * 任意监听器返回 {@code true} 即可攻击。
     *
     * <p>Callback for determining whether a player can be punched.
     * Any listener returning {@code true} makes the entity punchable.
     */
    Event<IsPlayerPunchable> EVENT = createArrayBacked(IsPlayerPunchable.class, listeners -> player -> {
        for (IsPlayerPunchable listener : listeners) {
            if (listener.gotPunchable(player)) {
                return true;
            }
        }
        return false;
    });

    /**
     * 判断指定实体是否可被近战攻击。
     *
     * <p>Determines whether the given entity can be punched.
     *
     * @param player 需要判断的实体 / the entity to check
     * @return {@code true} 若该实体可被攻击 / {@code true} if the entity can be punched
     */
    boolean gotPunchable(Entity player);
}
