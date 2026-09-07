package org.agmas.noellesroles.game.roles.neutral.raven;

import org.agmas.noellesroles.role_data.neutral.RavenRoleData;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class RavenRole extends NormalRole {

    public RavenRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        var data = RoleData.getOptional(RavenRoleData.class, player);
        if (data.isEmpty())
            return TrueFalseResult.PASS;
        var cca = data.get();
        if (cca.isHunting()) {
            // HUNTING 时不准捡东西
            return TrueFalseResult.FALSE;
        }
        return TrueFalseResult.PASS;
    }

}
