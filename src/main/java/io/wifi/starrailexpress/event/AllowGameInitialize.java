package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.api.GameMode;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** 开局破坏性初始化前的准入闸门；拒绝者负责向玩家解释原因，不修改名单。 */
@FunctionalInterface
public interface AllowGameInitialize {
    Event<AllowGameInitialize> EVENT = EventFactory.createArrayBacked(AllowGameInitialize.class,
            listeners -> (level, mode, players) -> {
                for (AllowGameInitialize listener : listeners) {
                    if (!listener.allow(level, mode, players)) return false;
                }
                return true;
            });

    boolean allow(ServerLevel level, GameMode mode, List<ServerPlayer> players);
}
