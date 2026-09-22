package org.agmas.noellesroles.game.roles.vigilante.magic_apprentice;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.List;

public class MagicApprenticeRole extends NormalRole {
    public MagicApprenticeRole(ResourceLocation identifier, int color, boolean isInnocent,
            boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        List<ItemStack> items = new ArrayList<>(super.getDefaultItems());
        items.add(ModItems.APPRENTICE_WAND.getDefaultInstance());
        return items;
    }
}
