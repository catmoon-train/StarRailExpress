package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** him：只能杀死看着自己的人，技能切换隐身。 */
public class HimRole extends NormalRole {
    public HimRole(ResourceLocation id, int color, boolean innocent, boolean killer, SRERole.MoodType mood, int sprint,
            boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.HIM, RoleSkill.skill(
                SRE.id("him_vanish"),
                "skill.noellesroles.him.vanish",
                context -> CommandBlockCombat.toggleHimInvisibility(context.player()))
                .cooldownSeconds(15).announceToSelf().showOnHud(true).build());
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new ShopEntry(ModItems.HIM_FIREBALL.getDefaultInstance(), 75, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.DREAM_DIAMOND_SWORD.getDefaultInstance(), 120, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
        return shop;
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }
}
