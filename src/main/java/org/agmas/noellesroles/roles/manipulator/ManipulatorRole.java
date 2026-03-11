package org.agmas.noellesroles.roles.manipulator;

import io.wifi.starrailexpress.api.NoramlRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class ManipulatorRole extends NoramlRole {
    public ManipulatorRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public boolean onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        final var manipulatorPlayerComponent = ManipulatorPlayerComponent.KEY.get(victim);
        final var target = manipulatorPlayerComponent.target;
        if (target !=null) {
            final var playerByUUID = victim.level().getPlayerByUUID(target);
            if (playerByUUID !=null) {
                final var inControlCCA = InControlCCA.KEY.get(playerByUUID);
                inControlCCA.isControlling = false;
                inControlCCA.sync();
            }

        }
        return super.onDeath(victim, spawnBody, killer, deathReason);
    }
}
