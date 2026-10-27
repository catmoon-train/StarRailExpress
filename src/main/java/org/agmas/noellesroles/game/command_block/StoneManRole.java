package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 方块石头人：硬直、成长、长枪。 */
public class StoneManRole extends NormalRole {
    public StoneManRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood, int sprint,
            boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of(ModItems.STONE_SPEAR.getDefaultInstance());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.INFERIOR_LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        shop.add(new ShopEntry(ModItems.THROW_STONE.getDefaultInstance(), 65, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.SUMMON_ROLLING_STONE.getDefaultInstance(), 300, ShopEntry.Type.WEAPON));
        return shop;
    }
}
