package org.agmas.noellesroles.game.command_block;

import java.util.List;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 由死者玩家操控的铁傀儡，与芥末同属杀手阵营。 */
public class MustardPuppetRole extends NormalRole {
    public MustardPuppetRole(ResourceLocation id) {
        super(id, 0xB4BDA3, false, true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of();
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}
