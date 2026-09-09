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

import io.wifi.starrailexpress.morph.MorphAppearance;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

/**
 * 玩家变形前事件（可拦截）。任意监听器返回 {@code false} 则取消本次变形。
 */
public interface AllowPlayerMorph {

    Event<AllowPlayerMorph> EVENT = createArrayBacked(AllowPlayerMorph.class,
            listeners -> (player, appearance) -> {
                for (AllowPlayerMorph listener : listeners) {
                    if (!listener.allowMorph(player, appearance)) {
                        return false;
                    }
                }
                return true;
            });

    /**
     * @param player     即将变形的玩家
     * @param appearance 目标外观；{@link MorphAppearance#NONE} 表示解除变形
     * @return {@code false} 取消本次变形 / 解除
     */
    boolean allowMorph(ServerPlayer player, MorphAppearance appearance);
}
