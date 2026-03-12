package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface AllowShootRevolverDrop {
    public static enum ShouldDropResult {
        TRUE, FALSE, PASS
    }

    Event<AllowShootRevolverDrop> EVENT = createArrayBacked(AllowShootRevolverDrop.class,
            listeners -> (player, target) -> {
                for (AllowShootRevolverDrop listener : listeners) {
                    var re = listener.allowDrop(player, target);
                    if (re != null && re != ShouldDropResult.PASS) {
                        return re;
                    }
                }
                return ShouldDropResult.PASS;
            });

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    ShouldDropResult allowDrop(Player player, Player target);
}