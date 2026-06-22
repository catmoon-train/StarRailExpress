package org.agmas.noellesroles.role.touhou;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.innocent.ayayaya.AyayayaPlayerComponent;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MountainRoles {
    public static final String NAMESPACE = "th_mount";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    public static final ResourceLocation AYA_ID = Noellesroles.id("ayayaya");
    public static final ResourceLocation HATATE_ID = Noellesroles.id("hatate");

    public static SRERole AYA = TMMRoles.registerRole(new TouhouRole(
            AYA_ID, // 角色 ID
            new Color(26, 42, 58).getRGB(), // 黑色 - 代表乌鸦
            false, // isInnocent = 乘客阵营
            true, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            true) {
        static ArrayList<ShopEntry> SHOP = new ArrayList<>();
        static {
            // 传递盒 - 100金币
            SHOP.add(new ShopEntry(
                    ModItems.DELIVERY_BOX.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));
            // 收纳袋 - 150金币
            SHOP.add(new ShopEntry(
                    Items.BUNDLE.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));
            // 报纸 - 50金币
            SHOP.add(new ShopEntry(
                    ModItems.NEWSPAPER.getDefaultInstance(),
                    50,
                    ShopEntry.Type.TOOL));
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

    }.setComponentKey(AyayayaPlayerComponent.KEY));

    public static SRERole HATATE = TMMRoles.registerRole(new TouhouRole(
            AYA_ID, // 角色 ID
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
            SHOP.add(new ShopEntry(
                    ModItems.DELIVERY_BOX.getDefaultInstance(),
                    100,
                    ShopEntry.Type.TOOL));
            // 收纳袋 - 150金币
            SHOP.add(new ShopEntry(
                    Items.BUNDLE.getDefaultInstance(),
                    150,
                    ShopEntry.Type.TOOL));
            // 报纸 - 50金币
            SHOP.add(new ShopEntry(
                    ModItems.NEWSPAPER.getDefaultInstance(),
                    50,
                    ShopEntry.Type.TOOL));
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

    }.setComponentKey(AyayayaPlayerComponent.KEY));

    public static void init() {
    }
}
