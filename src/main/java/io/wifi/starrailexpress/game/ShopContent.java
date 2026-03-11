package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.PlayerShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.SREConfig;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class ShopContent {
    public static List<ShopEntry> defaultEntries = new ArrayList<>();
    public static void register(){
        {
            defaultEntries.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), SREConfig.knifePrice, ShopEntry.Type.WEAPON));
            defaultEntries.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(), SREConfig.revolverPrice, ShopEntry.Type.WEAPON));
            defaultEntries.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), SREConfig.grenadePrice, ShopEntry.Type.WEAPON));
            defaultEntries.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(), SREConfig.psychoModePrice, ShopEntry.Type.WEAPON) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return PlayerShopComponent.usePsychoMode(player);
                }
            });
            // defaultEntries.add(new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(), TMMConfig.poisonVialPrice, ShopEntry.Type.POISON));
//            defaultEntries.add(new ShopEntry(TMMItems.SCORPION.getDefaultInstance(), TMMConfig.scorpionPrice, ShopEntry.Type.POISON));
            defaultEntries.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), SREConfig.firecrackerPrice, ShopEntry.Type.TOOL));
            defaultEntries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), SREConfig.lockpickPrice, ShopEntry.Type.TOOL));
            defaultEntries.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), SREConfig.crowbarPrice, ShopEntry.Type.TOOL));
            defaultEntries.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), SREConfig.bodyBagPrice, ShopEntry.Type.TOOL));
            defaultEntries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), SREConfig.blackoutPrice, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return PlayerShopComponent.useBlackout(player);
                }
            });
            defaultEntries.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), SREConfig.notePrice, ShopEntry.Type.TOOL));
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
