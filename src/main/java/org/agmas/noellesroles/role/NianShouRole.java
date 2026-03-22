package org.agmas.noellesroles.role;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.NianShouPlayerComponent;

public class NianShouRole extends NormalRole {

    public NianShouRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void onFinishQuest(Player player, String quest) {
        NianShouPlayerComponent nianShouComponent = NianShouPlayerComponent.KEY.get(player);
        if (nianShouComponent != null)
            nianShouComponent.onTaskCompleted();
    }

}
