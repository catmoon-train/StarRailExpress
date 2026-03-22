package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnRevolverUsed {

    Event<OnRevolverUsed> EVENT = createArrayBacked(OnRevolverUsed.class, listeners -> (player, target) -> {
        for (OnRevolverUsed listener : listeners) {
            listener.onPlayerShoot(player, target);
        }
        return;
    });

    void onPlayerShoot(ServerPlayer player, @Nullable ServerPlayer target);
}