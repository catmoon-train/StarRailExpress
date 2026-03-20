package org.agmas.noellesroles.repack;

import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import org.jetbrains.annotations.NotNull;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class HSRConstants {
    public static int toxinPoisonTime = getInTicks(0, 30);
    float banditRevolverDropChance = 0.2F;
    public static Map<Item, Integer> ITEM_COOLDOWNS = new HashMap<>();
    public static List<ShopEntry> POISONER_SHOP_ENTRIES = new ArrayList<>();
    public static List<ShopEntry> BANDIT_SHOP_ENTRIES = new ArrayList<>();

    static {
        // 毒药/80
        POISONER_SHOP_ENTRIES.add(new ShopEntry(HSRItems.TOXIN.getDefaultInstance(), 80, ShopEntry.Type.POISON));
        // 毒药瓶/50
        POISONER_SHOP_ENTRIES.add(new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(), 50, ShopEntry.Type.POISON));
        // 马桶毒药/40
        POISONER_SHOP_ENTRIES.add(new ShopEntry(ModItems.TOILET_POISON.getDefaultInstance(), 40, ShopEntry.Type.POISON));
        // 毒蝎子/30
        POISONER_SHOP_ENTRIES.add(new ShopEntry(TMMItems.SCORPION.getDefaultInstance(), 30, ShopEntry.Type.POISON));
        // 催化剂/100
        POISONER_SHOP_ENTRIES.add(new ShopEntry(HSRItems.CATALYST.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
        // 假药丸/50
        POISONER_SHOP_ENTRIES.add(new ShopEntry(HSRItems.createPillStack(true), 50, ShopEntry.Type.TOOL));
        // 爆竹/10
        POISONER_SHOP_ENTRIES.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), 10, ShopEntry.Type.TOOL));
        // 开锁器/100
        POISONER_SHOP_ENTRIES.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
        // 氯气弹/300
        POISONER_SHOP_ENTRIES.add(new ShopEntry(ModItems.CHLORINE_BOMB.getDefaultInstance(), 300, ShopEntry.Type.POISON));
        // 黑暗降临/100
        POISONER_SHOP_ENTRIES.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 100, ShopEntry.Type.TOOL) {
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });
        POISONER_SHOP_ENTRIES.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), 10, ShopEntry.Type.TOOL));

        BANDIT_SHOP_ENTRIES
                .add(new ShopEntry(HSRItems.BANDIT_REVOLVER.getDefaultInstance(), 175, ShopEntry.Type.WEAPON));
        BANDIT_SHOP_ENTRIES.add(new ShopEntry(TMMItems.KNIFE.getDefaultInstance(), 250, ShopEntry.Type.WEAPON));
        BANDIT_SHOP_ENTRIES.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(), 350, ShopEntry.Type.WEAPON));
        BANDIT_SHOP_ENTRIES.add(new ShopEntry(TMMItems.SCORPION.getDefaultInstance(), 40, ShopEntry.Type.POISON));
        BANDIT_SHOP_ENTRIES.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(), 20, ShopEntry.Type.TOOL));
        BANDIT_SHOP_ENTRIES.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(), 10, ShopEntry.Type.TOOL));
        BANDIT_SHOP_ENTRIES.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(), 200, ShopEntry.Type.TOOL));
        BANDIT_SHOP_ENTRIES.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(), 200, ShopEntry.Type.TOOL) {
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });
        BANDIT_SHOP_ENTRIES.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), 10, ShopEntry.Type.TOOL));
    }

    public static void init() {
        ITEM_COOLDOWNS.put(HSRItems.ANTIDOTE, getInTicks(1, 0));  // 60秒冷却
        ITEM_COOLDOWNS.put(HSRItems.TOXIN, getInTicks(0, 50));
        ITEM_COOLDOWNS.put(HSRItems.BANDIT_REVOLVER, getInTicks(0, 40));
        ITEM_COOLDOWNS.put(TMMItems.SCORPION, getInTicks(0, 35));
        ITEM_COOLDOWNS.put(HSRItems.CATALYST, getInTicks(0, 75));
    }

    public static int getInTicks(int minutes, int seconds) {
        return (minutes * 60 + seconds) * 20;
    }
}
