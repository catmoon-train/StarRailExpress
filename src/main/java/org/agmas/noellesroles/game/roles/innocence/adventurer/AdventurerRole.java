package org.agmas.noellesroles.game.roles.innocence.adventurer;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.resources.ResourceLocation;

public final class AdventurerRole extends NormalRole {
    public AdventurerRole(ResourceLocation id, int color, boolean innocent, boolean killer,
                          MoodType mood, int sprint, boolean hide) {
        super(id, color, innocent, killer, mood, sprint, hide);
    }
}
