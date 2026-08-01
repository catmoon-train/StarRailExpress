/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
public interface DisallowPlayerOpenDoor {

    /**
     * 判断实体是否允许打开上锁的门的事件。
     * 任意监听器返回 {@code true} 即允许。
     *
     * <p>Callback for determining whether a player can open a locked door.
     * Any listener returning {@code true} grants permission.
     */
    Event<DisallowPlayerOpenDoor> EVENT = createArrayBacked(DisallowPlayerOpenDoor.class, listeners -> player -> {
        for (DisallowPlayerOpenDoor listener : listeners) {
            if (listener.cantOpen(player)) {
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
    boolean cantOpen(Entity player);
}
