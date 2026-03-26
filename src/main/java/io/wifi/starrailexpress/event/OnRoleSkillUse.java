package io.wifi.starrailexpress.event;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.player.Player;

public class OnRoleSkillUse {
    public static Event<UseSkillEventInterface> BEFORE = EventFactory.createArrayBacked(UseSkillEventInterface.class,
            listeners -> (player, deathReason) -> {
                for (UseSkillEventInterface listener : listeners) {
                    if (!listener.onUse(player, deathReason))
                        return false;
                }
                return true;
            });
    public static Event<UseSkillEventInterface> AFTER = EventFactory.createArrayBacked(UseSkillEventInterface.class,
            listeners -> (player, deathReason) -> {
                for (UseSkillEventInterface listener : listeners) {
                    if (!listener.onUse(player, deathReason))
                        return false;
                }
                return true;
            });

    public interface UseSkillEventInterface {
        boolean onUse(Player player, SRERole role);
    }
}
