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

package io.wifi.starrailexpress.api.replay;

import io.wifi.starrailexpress.api.replay.ReplayEventTypes.EventDetails;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes.EventType;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 游戏回放记录器接口。
 * 允许第三方模组记录自定义事件到游戏回放中。
 */
public interface IGameReplayRecorder {

    /**
     * 记录一个通用事件。
     *
     * @param eventType 事件类型。
     * @param details   事件详情，必须是 {@link EventDetails} 的实现。
     */
    void recordEvent(EventType eventType, EventDetails details);

    /**
     * 记录一个通用事件，并控制它是否默认展示。
     */
    default void recordEvent(EventType eventType, EventDetails details, boolean hidden) {
        recordEvent(eventType, details);
    }

    /**
     * 记录一个自定义事件。
     *
     * @param customEventTypeId 自定义事件的唯一标识符。
     * @param playerUuid        触发事件的玩家UUID，可为null。
     * @param message           自定义事件的消息内容。
     */
    void recordCustomEvent(ResourceLocation customEventTypeId, UUID playerUuid, String message);

    /**
     * 记录一个自定义事件，并控制它是否默认展示。
     */
    default void recordCustomEvent(ResourceLocation customEventTypeId, UUID playerUuid, String message, boolean hidden) {
        recordCustomEvent(customEventTypeId, playerUuid, message);
    }

    // 可以根据需要添加更多记录方法，例如：
    // void recordPlayerAction(UUID playerUuid, PlayerActionType actionType, BlockPos pos);
    // void recordWorldChange(BlockPos pos, BlockState oldState, BlockState newState);
}
