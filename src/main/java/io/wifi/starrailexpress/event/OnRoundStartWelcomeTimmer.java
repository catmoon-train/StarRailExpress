package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface OnRoundStartWelcomeTimmer {

    Event<OnRoundStartWelcomeTimmer> EVENT = createArrayBacked(OnRoundStartWelcomeTimmer.class,
            listeners -> (player, deathReason) -> {
                for (OnRoundStartWelcomeTimmer listener : listeners) {
                    listener.onWelcome(player, deathReason);
                }
                return;
            });

    void onWelcome(Player player, int welcomeTime);
}