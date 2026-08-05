package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.EventFactory;
import io.wifi.starrailexpress.content.vote.VoteSession;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;

public interface MeetingVoteEndEvent {

    Event<MeetingVoteEndEvent> EVENT = EventFactory.createArrayBacked(MeetingVoteEndEvent.class,
            listeners -> (level, session) -> {
                for (MeetingVoteEndEvent listener : listeners) {
                    listener.onVoteOver(level, session);
                }
            });

    /**
     * @return false 阻止该玩家被投票出局
     */
    void onVoteOver(ServerLevel level, VoteSession session);
}
