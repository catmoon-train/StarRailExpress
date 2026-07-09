package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 会议投票出局时触发的事件（可拦截）。
 * 返回 false 可拦截出局（如政客免疫投票出局）。
 */
public interface MeetingVoteOutEvent {

    Event<MeetingVoteOutEvent> EVENT = EventFactory.createArrayBacked(MeetingVoteOutEvent.class,
            listeners -> (level, player) -> {
                for (MeetingVoteOutEvent listener : listeners) {
                    if (!listener.onVoteOut(level, player)) return false;
                }
                return true;
            });

    /**
     * @return false 阻止该玩家被投票出局
     */
    boolean onVoteOut(ServerLevel level, ServerPlayer player);
}
