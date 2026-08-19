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
