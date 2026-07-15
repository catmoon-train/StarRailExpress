package io.wifi.starrailexpress.api;

import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.init.InitModRolesMax;

public class TouhouRole extends NormalRole {
    /**
     * @param identifier    the mod id and name of the role
     * @param color         the role announcement color
     * @param isInnocent    whether the gun drops when a person with this role is
     *                      shot and is considered a civilian to the win conditions
     * @param canUseKiller  can see and use the killer features
     * @param moodType      the mood type a role has
     * @param maxSprintTime the maximum sprint time in ticks
     * @param canSeeTime    if the role can see the game timer
     */
    public TouhouRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.addFlag("touhou");
    }

    @Override
    public boolean canBeRandomed() {
        if (InitModRolesMax.isTouhouEnabled)
            return super.canBeRandomed();
        return false;
    }
}
