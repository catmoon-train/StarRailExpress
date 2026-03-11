package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface AfterShieldAllowPlayerDeathWithKiller {

    /**
     * Event callback to determine if a player is allowed to die for a specific
     * death type, after activing the shield.
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot',
     * 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     * 
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<AfterShieldAllowPlayerDeathWithKiller> EVENT = createArrayBacked(AfterShieldAllowPlayerDeathWithKiller.class,
            listeners -> (player, killer, deathReason) -> {
                for (AfterShieldAllowPlayerDeathWithKiller listener : listeners) {
                    if (!listener.allowDeath(player, killer, deathReason)) {
                        return false;
                    }
                }
                return true;
            });

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean allowDeath(Player player, Player killer, ResourceLocation deathReason);
}