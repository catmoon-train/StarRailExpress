package org.agmas.noellesroles.game.roles.innocence.insurance;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

/** Civilian role that can buy one insurance policy from its shop. */
public class InsuranceRole extends NormalRole {
    public InsuranceRole(ResourceLocation identifier, int color) {
        super(identifier, color, RoleType.CIVILIAN, MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), true);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(new ItemStack(ModItems.INSURANCE), 165, ShopEntry.Type.TOOL));
    }
}
