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
