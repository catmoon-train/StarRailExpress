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

import io.wifi.starrailexpress.util.TrueFalseResult;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public class CanCollideWith {
    /**
     * 是否允许二者碰撞
     *
     * @param player 玩家1
     * @param entity 实体
     * @return 掉落决策结果（TRUE/FALSE/SKIP）
     */
    public static Event<CollidePlayerEvent> PLAYER = EventFactory.createArrayBacked(CollidePlayerEvent.class,
            listeners -> (player, target) -> {
                for (var listener : listeners) {
                    var re = listener.allowCollideWith(player, target);
                    if (re != null && re != TrueFalseResult.PASS) {
                        return re;
                    }
                }
                return TrueFalseResult.PASS;
            });
    /**
     * 是否允许二者碰撞
     *
     * @param entity  实体1
     * @param entity2 实体2
     * @return 掉落决策结果（TRUE/FALSE/SKIP）
     */
    public static Event<CollideEntityEvent> ENTITY = EventFactory.createArrayBacked(CollideEntityEvent.class,
            listeners -> (player, target) -> {
                for (var listener : listeners) {
                    var re = listener.allowCollideWith(player, target);
                    if (re != null && re != TrueFalseResult.PASS) {
                        return re;
                    }
                }
                return TrueFalseResult.PASS;
            });

    public static interface CollideEntityEvent {
        /**
         * 是否允许二者碰撞
         *
         * @param entity  实体1
         * @param entity2 实体2
         * @return 掉落决策结果（TRUE/FALSE/SKIP）
         */
        TrueFalseResult allowCollideWith(Entity entity, Entity entity2);
    }

    public static interface CollidePlayerEvent {
        /**
         * 是否允许二者碰撞
         *
         * @param player 玩家1
         * @param entity 实体
         * @return 掉落决策结果（TRUE/FALSE/SKIP）
         */
        TrueFalseResult allowCollideWith(Player player, Entity entity);
    }
}
