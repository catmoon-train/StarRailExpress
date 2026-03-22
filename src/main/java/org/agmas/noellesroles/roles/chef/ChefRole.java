package org.agmas.noellesroles.roles.chef;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.utils.RoleUtils;

public class ChefRole extends NormalRole {
    public ChefRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void onFinishQuest(Player player, String quest) {
        for (var it : player.getInventory().items) {
            if (it.is(ModItems.FOOD_STUFF)) {
                int count = it.getCount();
                if (count >= 16) {
                    RoleUtils.insertStackInFreeSlot(player, ModItems.FOOD_STUFF.getDefaultInstance());
                    return;
                } else {
                    it.setCount(count + 1);
                    return;
                }
            }
        }
        RoleUtils.insertStackInFreeSlot(player, ModItems.FOOD_STUFF.getDefaultInstance());

    }
}
