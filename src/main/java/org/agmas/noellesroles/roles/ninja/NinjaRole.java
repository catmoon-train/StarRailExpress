package org.agmas.noellesroles.roles.ninja;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class NinjaRole extends NormalRole {

    public NinjaRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                     MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return super.getDefaultItems();
    }
}