package org.agmas.noellesroles.game.roles.killer.wraith_assassin;

import io.wifi.starrailexpress.api.ExtraEffectRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.RoleShopHandler;

import java.util.List;

public class WraithAssassinRole extends ExtraEffectRole {
    public WraithAssassinRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return RoleShopHandler.WRAITH_ASSASSIN_SHOP;
    }

    @Override
    public InteractionResult onPickUpItem(Player player, ItemStack item) {
        var comp = WraithAssassinPlayerComponent.KEY.maybeGet(player).orElse(null);
        if (comp != null && comp.isInDimension()) {
            return InteractionResult.FAIL;
        }
        return super.onPickUpItem(player, item);
    }
}
