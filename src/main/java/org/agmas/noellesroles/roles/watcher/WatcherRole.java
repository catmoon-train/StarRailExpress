package org.agmas.noellesroles.roles.watcher;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class WatcherRole extends SRERole {

    public WatcherRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void onFinishQuest(Player player, String quest) {
        SREPlayerShopComponent.KEY.get(player).addToBalance(25);
    }
}
