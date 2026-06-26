package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import org.agmas.noellesroles.init.ModItems;
import io.wifi.starrailexpress.util.SREItemUtils;
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

    public static void register() {
        {
            // 杀手刀使用动态商店条目：murder 模式下带 3 点耐久、可替换耗尽的刀、首购后 -50%。
            // Killer knife uses the dynamic entry: 3 durability in murder modes, replaces depleted
            // knives, and -50% after the first purchase. See KillerKnifeShopEntry / DynamicShopComponent.
            defaultKnifeEntries.add(new KillerKnifeShopEntry(SREConfig.instance().knifePrice));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(),
                    SREConfig.instance().revolverPrice, ShopEntry.Type.WEAPON));
                defaultKnifeEntries.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(),
                    SREConfig.instance().grenadePrice, ShopEntry.Type.WEAPON));

                defaultKnifeEntries.add(new ShopEntry(ModItems.SHORT_SHOTGUN.getDefaultInstance(), SREConfig.instance().shortShotgunPrice, ShopEntry.Type.WEAPON));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(),
                    SREConfig.instance().psychoModePrice, ShopEntry.Type.WEAPON) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)){
                        return false;
                    }
                    return super.canBuy(player);
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)){
                        return false;
                    }
                    player.level().players().forEach(
                            player1 -> {
                                if (!player1.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)){
                                    player1.getCooldowns().addCooldown(TMMItems.PSYCHO_MODE,
                                            SREConfig.instance().psychoGlobalCooldown);
                                }
                            }
                    );
                    return SREPlayerShopComponent.usePsychoMode(player);
                }
            });
            // defaultEntries.add(new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(),
            // TMMConfig.poisonVialPrice, ShopEntry.Type.POISON));
            // defaultEntries.add(new ShopEntry(TMMItems.SCORPION.getDefaultInstance(),
            // TMMConfig.scorpionPrice, ShopEntry.Type.POISON));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(),
                    SREConfig.instance().firecrackerPrice, ShopEntry.Type.TOOL));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(),
                    SREConfig.instance().lockpickPrice, ShopEntry.Type.TOOL));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                    SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL));
            defaultKnifeEntries.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(),
                    SREConfig.instance().bodyBagPrice, ShopEntry.Type.TOOL));

//            defaultKnifeEntries.add(new ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(),
//                    SREConfig.instance().monitorBrokenPrice, ShopEntry.Type.TOOL) {
//                @Override
//                public boolean onBuy(@NotNull Player player) {
//                    return SREPlayerShopComponent.useMonitorBroken(player,
//                            SREConfig.instance().monitorBrokenDuration * 20);
//                }
//            });
            defaultKnifeEntries.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(),
                    SREConfig.instance().blackoutPrice, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
            defaultKnifeEntries.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), SREConfig.instance().notePrice,
                    ShopEntry.Type.TOOL));
        }
    }

    public static Map<ResourceLocation, List<ShopEntry>> customEntries = new HashMap<>();

    public static List<ShopEntry> getShopEntries(ResourceLocation role) {
        SRERole sreRole = TMMRoles.ROLES.get(role);
        if (sreRole == null) {
            return List.of();
        }

        final var shopEntries = sreRole.getShopEntries();
        List<ShopEntry> result;
        if (shopEntries != null && !shopEntries.isEmpty()) {
            result = shopEntries;
        } else if (customEntries.containsKey(role)) {
            result = customEntries.get(role);
        } else {
            result = List.of();
        }


            return result;

    }

    public static ShopEntry createSheriffBulletEntry() {
        return new ShopEntry(ModItems.BULLET.getDefaultInstance(), SREConfig.instance().sheriffBulletPrice,
                ShopEntry.Type.WEAPON) {
            @Override
            public boolean canBuy(@NotNull Player player) {
                int maxCarry = Math.max(0, SREConfig.instance().sheriffBulletMaxCarry);
                return super.canBuy(player) && SREItemUtils.countItem(player, ModItems.BULLET) < maxCarry;
            }
        };
    }
}
