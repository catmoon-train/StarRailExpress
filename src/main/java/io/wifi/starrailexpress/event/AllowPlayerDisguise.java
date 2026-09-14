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
 * 玩家实体伪装前事件（可拦截）。任意监听器返回 {@code false} 则取消本次伪装 / 解除。
 * <p>
 * 注意：开局 / 结束时的生命周期清理不走此事件——清理不应被监听器否决。
 */
public interface AllowPlayerDisguise {

    Event<AllowPlayerDisguise> EVENT = createArrayBacked(AllowPlayerDisguise.class,
            listeners -> (player, state) -> {
                for (AllowPlayerDisguise listener : listeners) {
                    if (!listener.allowDisguise(player, state)) {
                        return false;
                    }
                }
                return true;
            });

    /**
     * @param player 即将伪装的玩家
     * @param state  目标状态；{@link EntityDisguiseState#NONE} 表示解除伪装
     * @return {@code false} 取消本次伪装 / 解除
     */
    boolean allowDisguise(ServerPlayer player, EntityDisguiseState state);
}
