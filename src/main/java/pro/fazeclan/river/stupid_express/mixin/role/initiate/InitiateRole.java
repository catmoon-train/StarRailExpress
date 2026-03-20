package pro.fazeclan.river.stupid_express.mixin.role.initiate;

import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.resources.ResourceLocation;

public class InitiateRole extends SRERole {

    public InitiateRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }
    // 没啥用其实
}
