package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

import org.jetbrains.annotations.Nullable;

public interface OnPlayerDeathWithKiller {

    /**
     * Event callback to when a player is allowed to die for a specific death type.
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot',
     * 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     * 
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<OnPlayerDeathWithKiller> EVENT = createArrayBacked(OnPlayerDeathWithKiller.class,
            listeners -> (player, killer, deathReason) -> {
                for (OnPlayerDeathWithKiller listener : listeners) {
                    listener.onPlayerDeath(player, killer, deathReason);
                }
                return;
            });

    void onPlayerDeath(Player player, @Nullable Player killer, ResourceLocation deathReason);
}