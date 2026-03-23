package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.Entity;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：判断实体是否被允许打开上锁的门。
 * 若任意监听器返回 {@code true}，则允许开门。
 *
 * <p>Event interface to determine whether an entity is allowed to open a locked door.
 * If any listener returns {@code true}, the door may be opened.
 */
public interface AllowPlayerOpenLockedDoor {

    /**
     * 判断实体是否允许打开上锁的门的事件。
     * 任意监听器返回 {@code true} 即允许。
     *
     * <p>Callback for determining whether a player can open a locked door.
     * Any listener returning {@code true} grants permission.
     */
    Event<AllowPlayerOpenLockedDoor> EVENT = createArrayBacked(AllowPlayerOpenLockedDoor.class, listeners -> player -> {
        for (AllowPlayerOpenLockedDoor listener : listeners) {
            if (listener.allowOpen(player)) {
                return true;
            }
        }
        return false;
    });

    /**
     * 判断指定实体是否被允许打开上锁的门。
     *
     * <p>Determines whether the given entity is allowed to open a locked door.
     *
     * @param player 尝试开门的实体 / the entity attempting to open the door
     * @return {@code true} 若允许开门 / {@code true} if opening the door is allowed
     */
    boolean allowOpen(Entity player);
}
