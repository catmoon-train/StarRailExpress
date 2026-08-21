package org.agmas.noellesroles.game.roles.neutral.jester;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class JesterRole extends NormalRole {

    public JesterRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        return SRE.id("textures/entity/custom_psycho/jester.png");
    };

    @Override

    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        return TrueFalseResult.FALSE;
    }

}
