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

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：游戏结束时触发。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired when the game ends.
 * All listeners are invoked (non-cancellable event).
 */
public interface OnGameEnd {

    /**
     * 游戏结束时触发的事件。
     *
     * <p>Event callback fired when the game ends.
     */
    Event<OnGameEnd> EVENT = createArrayBacked(OnGameEnd.class,
            listeners -> (serverLevel, gameWorldComponent) -> {
                for (OnGameEnd listener : listeners) {
                    listener.onGameEnd(serverLevel, gameWorldComponent);
                }
            });

    /**
     * 游戏结束时的回调方法。
     *
     * <p>Callback invoked when the game ends.
     *
     * @param serverLevel        游戏所在的服务端世界 / the server level where the game took place
     * @param gameWorldComponent 游戏世界组件，包含本局游戏状态信息 /
     *                           the game world component carrying game state information
     */
    void onGameEnd(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent);
}
