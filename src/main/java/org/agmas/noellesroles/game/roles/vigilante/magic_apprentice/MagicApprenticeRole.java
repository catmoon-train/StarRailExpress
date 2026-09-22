package org.agmas.noellesroles.game.roles.vigilante.magic_apprentice;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
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

    @Override
    public void onDeathWithBody(Player victim, boolean spawnBody, Player killer,
            ResourceLocation deathReason, PlayerBodyEntity body) {
        if (body == null) return;
        var inventory = PlayerBodyEntityComponent.KEY.get(body).getCorpseInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.APPRENTICE_WAND)) {
                inventory.setItem(slot, TMMItems.REVOLVER.getDefaultInstance());
            }
        }
        PlayerBodyEntityComponent.KEY.get(body).sync();
    }
}
