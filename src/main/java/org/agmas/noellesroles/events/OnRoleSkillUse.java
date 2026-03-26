package org.agmas.noellesroles.events;

import io.wifi.starrailexpress.api.SRERole;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

public interface OnRoleSkillUse {
    Event<OnRoleSkillUse> BEFORE = EventFactory.createArrayBacked(OnRoleSkillUse.class, listeners -> (player, role) -> {
        for (OnRoleSkillUse listener : listeners) {
            if (!listener.onUse(player, role)) {
                return false;
            }
        }
        return true;
    });

    Event<OnRoleSkillUse> AFTER = EventFactory.createArrayBacked(OnRoleSkillUse.class, listeners -> (player, role) -> {
        for (OnRoleSkillUse listener : listeners) {
            listener.onUse(player, role);
        }
        return true;
    });

    boolean onUse(ServerPlayer player, SRERole role);
}
