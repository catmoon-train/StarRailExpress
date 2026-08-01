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
 * 事件接口：判断实体（玩家）是否能看到饮料盘上的毒素粒子效果。
 * 若任意监听器返回 {@code true}，则该实体可以看到毒素。
 *
 * <p>Event interface to determine whether an entity (player) can see poison particles
 * on a beverage plate. If any listener returns {@code true}, the entity can see the poison.
 */
public interface CanSeePoison {

    /**
     * 判断实体是否能看到饮料盘毒素粒子效果的事件。
     * 任意监听器返回 {@code true} 即可见。
     *
     * <p>Callback for determining whether a player can see poison particles on a beverage plate.
     * Any listener returning {@code true} grants visibility.
     */
    Event<CanSeePoison> EVENT = createArrayBacked(CanSeePoison.class, listeners -> player -> {
        for (CanSeePoison listener : listeners) {
            if (listener.visible(player)) {
                return true;
            }
        }
        return false;
    });

    /**
     * 判断指定实体是否能看到饮料盘上的毒素粒子效果。
     *
     * <p>Determines whether the given entity can see poison particles on a beverage plate.
     *
     * @param player 需要判断的实体（通常为玩家） / the entity to check (typically a player)
     * @return {@code true} 若能看到毒素粒子 / {@code true} if the entity can see the poison particles
     */
    boolean visible(Entity player);
}
