package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface OnGiveKillerBalance {

    /**
     * Event callback to when a player is allowed to die for a specific death type.
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot',
     * 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     * 
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<OnGiveKillerBalance> EVENT = createArrayBacked(OnGiveKillerBalance.class,
            listeners -> (victim, killer, deathReason) -> {
                int balance = 0;
                for (OnGiveKillerBalance listener : listeners) {
                    balance += listener.onGiveKillerBalance(victim, killer, deathReason);
                }
                return balance;
            });

    int onGiveKillerBalance(Player victim, Player killer, ResourceLocation deathReason);
}