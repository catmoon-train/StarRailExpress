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
 * 玩家变形完成后的通知事件（外观已写入并开始同步）。
 * {@code next} 为 {@link MorphAppearance#NONE} 时表示解除变形。
 */
public interface OnPlayerMorph {

    Event<OnPlayerMorph> EVENT = createArrayBacked(OnPlayerMorph.class,
            listeners -> (player, previous, next) -> {
                for (OnPlayerMorph listener : listeners) {
                    listener.onMorph(player, previous, next);
                }
            });

    /**
     * @param player   变形的玩家
     * @param previous 变形前外观（从未变形则为 {@link MorphAppearance#NONE}）
     * @param next     变形后外观
     */
    void onMorph(ServerPlayer player, MorphAppearance previous, MorphAppearance next);
}
