package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.GhostArtItem;

/** 布袋鬼专属物品及死亡清理，不修改通用SRERole。 */
public final class SackGhostRole extends NormalRole {
    public SackGhostRole() {
        super(Noellesroles.id("ma_chen_xu"), 0x4B0082, false, true, MoodType.FAKE, Integer.MAX_VALUE, true);
    }

    @Override public InteractionResult onDropItem(Player player, ItemStack stack) {
        if (stack.getItem() instanceof GhostArtItem) {
            stack.setCount(0);
            return InteractionResult.FAIL;
        }
        return super.onDropItem(player, stack);
    }

    @Override public void onDeath(Player victim, boolean spawnBody, Player killer,
            net.minecraft.resources.ResourceLocation reason, boolean forceDeath) {
        var data = SackGhostItems.data(victim);
        if (data != null) data.clear();
    }
}
