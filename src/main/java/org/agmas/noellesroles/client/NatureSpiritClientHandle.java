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

package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.event.AllowOtherCameraType;

/**
 * 自然精灵伪装成方块时的视角处理。
 *
 * <p>
 * 之前为方便看清对齐的格子会强制切到第三人称；按现需求改为<b>不改变人称</b>，
 * 变身时保持玩家原本的视角（默认第一人称）。
 */
public class NatureSpiritClientHandle {

    public static void register() {
        // 不再强制切换人称：变身成方块时保持玩家原本的视角（第一人称）。
        AllowOtherCameraType.EVENT.register((original, localPlayer) ->
                AllowOtherCameraType.ReturnCameraType.NO_CHANGE);
    }
}
