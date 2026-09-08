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

import org.jetbrains.annotations.Nullable;

import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public class OnMeetingStart {

    /**
     * 游戏tick事件。当会议时会暂停（即不会调用）。
     */
    public static final Event<InnerOnMeetingStart> ALLOW_MEETING = EventFactory.createArrayBacked(
            InnerOnMeetingStart.class,
            listeners -> (serverLevel, reporter, victim,
                    emergency) -> {
                for (InnerOnMeetingStart listener : listeners) {
                    TrueFalseResult result = listener.allowMeeting(serverLevel, reporter, victim, emergency);
                    if (result != null && result != TrueFalseResult.PASS) {
                        return result;
                    }
                }
                return TrueFalseResult.PASS;
            });

    public interface InnerOnMeetingStart {

        /**
         * 游戏tick事件。当会议时会暂停（即不会调用）。
         */
        TrueFalseResult allowMeeting(ServerLevel serverLevel, ServerPlayer reporter, @Nullable String victim,
                boolean emergency);

    }
}
