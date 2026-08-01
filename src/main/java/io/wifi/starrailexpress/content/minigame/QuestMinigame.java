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

package io.wifi.starrailexpress.content.minigame;

import net.minecraft.network.chat.Component;

/**
 * 小游戏任务点的小游戏接口（纯服务端安全）
 * 只存储小游戏的 ID 和显示名称，不引用任何客户端类
 */
public record QuestMinigame(String id, Component displayName) {

    public static QuestMinigame of(String id, String translationKey) {
        return new QuestMinigame(id, Component.translatable(translationKey));
    }
}
