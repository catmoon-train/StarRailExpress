package io.wifi.starrailexpress.event;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.entity.player.Player;

import static net.fabricmc.fabric.api.event.EventFactory.createArrayBacked;

public interface AllowNameRender {

    /**
     * Event callback to determine if a player is allowed to die for a specific death type.
     * The game currently has the following death type names defined:
     * 'fell_out_of_train', 'poison', 'grenade', 'bat_hit', 'gun_shot', 'knife_stab'.
     * Any other death type not explicitly defined will default to 'generic'.
     * @see io.wifi.starrailexpress.game.GameConstants.DeathReasons
     */
    Event<AllowNameRender> EVENT = createArrayBacked(AllowNameRender.class, listeners -> (player) -> {
        for (AllowNameRender listener : listeners) {
            if (!listener.allowRenderName(player)) {
                return false;
            }
        }
        return true;
    });

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean allowRenderName(Player player);
}