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
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 事件接口：当玩家护盾被击破时触发。
 * 所有监听器均会被调用（非拦截型事件）。
 *
 * <p>Event interface fired when a player's shield is broken.
 * All listeners are invoked (non-cancellable event).
 */
public interface OnShieldBroken {

    /**
     * 玩家护盾被击破时触发的事件。
     * 参数：受害者（被击破护盾的玩家）、击杀者（击破护盾的玩家）。
     *
     * <p>Event fired when a player's shield is broken.
     * Parameters: victim (the player whose shield was broken), killer (the player who broke it).
     */
    Event<OnShieldBroken> EVENT = createArrayBacked(OnShieldBroken.class,
            listeners -> (a, b) -> {
                for (OnShieldBroken listener : listeners) {
                    listener.onShieldBroken(a, b);
                }
            });

    /**
     * 护盾被击破时的回调方法。
     *
     * <p>Callback invoked when a player's shield is broken.
     *
     * @param victim 护盾被击破的玩家 / the player whose shield was broken
     * @param killer 击破护盾的玩家 / the player who broke the shield
     */
    void onShieldBroken(Player victim, Player killer);
}
