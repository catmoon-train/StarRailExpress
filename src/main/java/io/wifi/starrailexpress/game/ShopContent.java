package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShopContent {
    public static List<ShopEntry> defaultKnifeEntries = new ArrayList<>();
    public static void register(){
        {
            defaultKnifeEntries.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), SREConfig.instance().knifePrice, ShopEntry.Type.WEAPON));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(), SREConfig.instance().revolverPrice, ShopEntry.Type.WEAPON));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), SREConfig.instance().grenadePrice, ShopEntry.Type.WEAPON));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(), SREConfig.instance().psychoModePrice, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.usePsychoMode(player);
                }
            });
            // defaultEntries.add(new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(), TMMConfig.poisonVialPrice, ShopEntry.Type.POISON));
//            defaultEntries.add(new ShopEntry(TMMItems.SCORPION.getDefaultInstance(), TMMConfig.scorpionPrice, ShopEntry.Type.POISON));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), SREConfig.instance().firecrackerPrice, ShopEntry.Type.TOOL));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), SREConfig.instance().lockpickPrice, ShopEntry.Type.TOOL));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), SREConfig.instance().bodyBagPrice, ShopEntry.Type.TOOL));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.instance().blackoutPrice, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
            defaultKnifeEntries.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), SREConfig.instance().notePrice, ShopEntry.Type.TOOL));
        }
    }
    public static Map<ResourceLocation, List<ShopEntry>> customEntries = new HashMap<>();
    public static List<ShopEntry> getShopEntries(ResourceLocation role) {
        final var shopEntries = TMMRoles.ROLES.get(role).getShopEntries();
        if (shopEntries != null && !shopEntries.isEmpty()){
            return shopEntries;
        }
        if (customEntries.containsKey(role)) {
            return customEntries.get(role);
        }

        return List.of();
    }
}
