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

import io.wifi.starrailexpress.disguise.EntityDisguiseState;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 玩家实体伪装变更后的通知事件（状态已写入、尺寸已刷新、开始同步）。
 * {@code next} 为 {@link EntityDisguiseState#NONE} 时表示解除伪装。
 */
public interface OnPlayerDisguise {

    Event<OnPlayerDisguise> EVENT = createArrayBacked(OnPlayerDisguise.class,
            listeners -> (player, previous, next) -> {
                for (OnPlayerDisguise listener : listeners) {
                    listener.onDisguise(player, previous, next);
                }
            });

    /**
     * @param player   伪装的玩家
     * @param previous 伪装前状态（从未伪装则为 {@link EntityDisguiseState#NONE}）
     * @param next     伪装后状态
     */
    void onDisguise(ServerPlayer player, EntityDisguiseState previous, EntityDisguiseState next);
}
