/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role.touhou;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.api.NormalRole.RoleType;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.DiscountShopEntry;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.game.roles.innocence.ayayaya.AyayayaPlayerComponent;
import org.agmas.noellesroles.handler.TouhouHandlers;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.touhou.roles.THIbarakiKasenRole;
import org.agmas.noellesroles.role.touhou.roles.THKyoukoRole;
import org.agmas.noellesroles.role.touhou.roles.THRinnosukeRole;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class THMountainRoles {
    public static final String NAMESPACE = "th_mount";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static final ResourceLocation NITORI_ID = id("kawashiro_nitori");
    public static final ResourceLocation AYA_ID = id("ayayaya");
    public static final ResourceLocation HATATE_ID = id("hatate");
    // 河城荷取。可以购买除了杀手道具外的各种东西，且可丢弃！但价格翻倍。
    public static SRERole NITORI = TMMRoles.registerRole(new THRinnosukeRole(
            NITORI_ID, // 角色 ID
            new Color(162, 221, 233).getRGB(),
            false, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true) {
        private static final List<ShopEntry> NITORI_SHOP = List.of(
                new ShopEntry(ModItems.DEALER_PACKAGE.getDefaultInstance(), 100, ShopEntry.Type.TOOL),
                new ShopEntry(TMMItems.DEFENSE_VIAL.getDefaultInstance(), 400, ShopEntry.Type.TOOL));

        @Override
        public List<ShopEntry> getShopEntries() {
            return NITORI_SHOP;
        }
    }, "th_mountain").setNeutrals(true).setDefaultEnableNeededPlayerCount(12).setDefaultEnableChance(100)
            .setCanUseInstinctAndNightVision(false).setCanPickUpRevolver(false);
    public static SRERole AYA = TMMRoles.registerRole(new TouhouRole(AYA_ID, // 角色 ID
            new Color(26, 42, 58).getRGB(), // 黑色 - 代表乌鸦
            false, // isInnocent = 乘客阵营
            true, // canUseKiller = 无杀手能力
            SRERole.MoodType.FAKE, // 真实心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true) {
        static ArrayList<ShopEntry> SHOP = new ArrayList<>();
        static {
            // 传递盒 - 100金币
            SHOP.add(new ShopEntry(ModItems.DELIVERY_BOX.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
            // 收纳袋 - 150金币
            SHOP.add(new ShopEntry(Items.BUNDLE.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
            // 报纸 - 50金币
            SHOP.add(new ShopEntry(ModItems.NEWSPAPER.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
            SHOP.add(new DiscountShopEntry(SREConfig.instance().knifePrice));

            SHOP.add(new ShopEntry(TMMItems.REVOLVER.getDefaultInstance(),
                    (int) ((float) SREConfig.instance().revolverPrice * 1.005f), ShopEntry.Type.WEAPON));
            SHOP.add(new ShopEntry(TMMItems.GRENADE.getDefaultInstance(),
                    SREConfig.instance().grenadePrice, ShopEntry.Type.WEAPON));

            SHOP.add(new ShopEntry(ModItems.SHORT_SHOTGUN.getDefaultInstance(),
                    SREConfig.instance().shortShotgunPrice, ShopEntry.Type.WEAPON));
            SHOP.add(new ShopEntry(TMMItems.PSYCHO_MODE.getDefaultInstance(),
                    SREConfig.instance().psychoModePrice, ShopEntry.Type.WEAPON) {
                @Override
                public boolean canBuy(@NotNull Player player) {
                    if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)) {
                        return false;
                    }
                    return super.canBuy(player);
                }

                @Override
                public boolean onBuy(@NotNull Player player) {
                    if (player.getCooldowns().isOnCooldown(TMMItems.PSYCHO_MODE)) {
                        return false;
                    }

                    return SREPlayerShopComponent.usePsychoMode(player);
                }
            });
            // defaultEntries.add(new ShopEntry(TMMItems.POISON_VIAL.getDefaultInstance(),
            // TMMConfig.poisonVialPrice, ShopEntry.Type.POISON));
            // defaultEntries.add(new ShopEntry(TMMItems.SCORPION.getDefaultInstance(),
            // TMMConfig.scorpionPrice, ShopEntry.Type.POISON));
            SHOP.add(new ShopEntry(TMMItems.FIRECRACKER.getDefaultInstance(),
                    SREConfig.instance().firecrackerPrice, ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(),
                    SREConfig.instance().lockpickPrice, ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                    SREConfig.instance().crowbarPrice, ShopEntry.Type.TOOL));
            SHOP.add(new ShopEntry(TMMItems.BODY_BAG.getDefaultInstance(),
                    SREConfig.instance().bodyBagPrice, ShopEntry.Type.TOOL));

            // defaultKnifeEntries.add(new
            // ShopEntry(TMMItems.MONITOR_BROKEN.getDefaultInstance(),
            // SREConfig.instance().monitorBrokenPrice, ShopEntry.Type.TOOL) {
            // @Override
            // public boolean onBuy(@NotNull Player player) {
            // return SREPlayerShopComponent.useMonitorBroken(player,
            // SREConfig.instance().monitorBrokenDuration * 20);
            // }
            // });
            SHOP.add(new ShopEntry(TMMItems.BLACKOUT.getDefaultInstance(),
                    SREConfig.instance().blackoutPrice, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player player) {
                    return SREPlayerShopComponent.useBlackout(player);
                }
            });
            SHOP.add(new ShopEntry(new ItemStack(TMMItems.NOTE, 4), SREConfig.instance().notePrice,
                    ShopEntry.Type.TOOL));
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            ArrayList<ItemStack> itemStacks = new ArrayList<>();
            return itemStacks;
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            var roleSpecShop = new ArrayList<>(SHOP);
            return roleSpecShop;
        }

        @Override
        public InteractionResult onDropItem(Player player, ItemStack item) {
            return InteractionResult.PASS;
        }
    }.setHiddenForRoleRotation(true).setComponentKey(AyayayaPlayerComponent.KEY), "th_mountain");

    public static SRERole HATATE = TMMRoles.registerRole(new TouhouRole(HATATE_ID, // 角色 ID
            new Color(123, 63, 158).getRGB(), // 黑色 - 代表乌鸦
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ) {
        static ArrayList<ShopEntry> SHOP = new ArrayList<>();
        static {
            // 传递盒 - 100金币
            SHOP.add(new ShopEntry(ModItems.DELIVERY_BOX.getDefaultInstance(), 100, ShopEntry.Type.TOOL));
            // 收纳袋 - 150金币
            SHOP.add(new ShopEntry(Items.BUNDLE.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
            // 报纸 - 50金币
            SHOP.add(new ShopEntry(ModItems.NEWSPAPER.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            ArrayList<ItemStack> itemStacks = new ArrayList<>();
            return itemStacks;
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            return SHOP;
        }

        @Override
        public InteractionResult onDropItem(Player player, ItemStack item) {
            return InteractionResult.PASS;
        }

    }.setHiddenForRoleRotation(true).setComponentKey(AyayayaPlayerComponent.KEY), "th_mountain");

    // 茨木华扇 Ibaraki Kasen
    public static SRERole IBARAKI_KASEN = TMMRoles.registerRole(new THIbarakiKasenRole(id("ibaraki_kasen"),
            new Color(216, 158, 159).getRGB(), true, false, MoodType.REAL,
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false), "th_mountain")
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(5000)
            .setAddedVersion("4.4");

    // 幽谷响子 kasodani_kyouko
    public static SRERole KASODANI_KYOUKO = TMMRoles.registerRole(new THKyoukoRole(id("kasodani_kyouko"),
            new Color(216, 158, 159).getRGB(), RoleType.CIVILIAN, MoodType.REAL,
            TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false), "th_mountain")
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(5000)
            .setAddedVersion("4.4");

    public static void init() {
        // 强制交易：Nitori
        TouhouHandlers.register();
    }

    static {
        NITORI.setAddedVersion("4.3");
        AYA.setAddedVersion("4.3");
        HATATE.setAddedVersion("4.3");
    }
}
