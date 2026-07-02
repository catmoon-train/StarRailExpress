package org.agmas.noellesroles.role.touhou;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.agmas.noellesroles.game.roles.innocence.ayayaya.AyayayaPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.touhou.roles.THNitoriRole;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MountainRoles {
    public static final String NAMESPACE = "th_mount";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    //
    public static final ResourceLocation NITORI_ID = id("kawashiro_nitori");
    public static final ResourceLocation AYA_ID = id("ayayaya");
    public static final ResourceLocation HATATE_ID = id("hatate");
    // 河城荷取。可以购买除了杀手道具外的各种东西，且可丢弃！但价格翻倍。
    public static SRERole NITORI = TMMRoles.registerRole(new THNitoriRole(
            NITORI_ID, // 角色 ID
            new Color(162, 221, 233).getRGB(),
            false, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.FAKE, // 真实心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true)).setNeutrals(true).setDefaultEnableNeededPlayerCount(12).setDefaultEnableChance(100)
            .setCanUseInstinct(false);
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
        }

        @Override
        public List<ItemStack> getDefaultItems() {
            ArrayList<ItemStack> itemStacks = new ArrayList<>();
            return itemStacks;
        }

        @Override
        public List<ShopEntry> getShopEntries() {
            var roleSpecShop = new ArrayList<>(SHOP);
            roleSpecShop.addAll(ShopContent.defaultKnifeEntries);
            return roleSpecShop;
        }

        @Override
        public InteractionResult onDropItem(Player player, ItemStack item) {
            if (item.is(Items.BUNDLE))
                return InteractionResult.SUCCESS;
            if (item.is(ModItems.NEWSPAPER)) {
                if (item.has(DataComponents.WRITTEN_BOOK_CONTENT))
                    return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
    }.setComponentKey(AyayayaPlayerComponent.KEY), "th_mountain");

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
            if (item.is(Items.BUNDLE))
                return InteractionResult.SUCCESS;
            if (item.is(ModItems.NEWSPAPER)) {
                if (item.has(DataComponents.WRITTEN_BOOK_CONTENT))
                    return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

    }.setComponentKey(AyayayaPlayerComponent.KEY), "th_mountain");

    public static void init() {
        // 强制交易：Nitori
        RoleSkill.register(NITORI, RoleSkill.skill(SRE.id("nitori_exchange"),
                "skill.noellesroles.nitori_exchange",
                context -> {
                    if (context.target() == null) {
                        return false;
                    }

                    var target = context.player().level().getPlayerByUUID(context.target());
                    if (target == null) {
                        context.player().displayClientMessage(Component.translatable(
                                "message.noellesroles.nitori_exchange.failed.no_target"), true);
                        return false;
                    }
                    ItemStack it = context.player().getMainHandItem();
                    if (it == null || it.isEmpty()) {
                        context.player().displayClientMessage(Component.translatable(
                                "message.noellesroles.nitori_exchange.failed.noitem"), true);
                        return false;
                    }
                    var targetShop = SREPlayerShopComponent.KEY.get(target);
                    var selfShop = SREPlayerShopComponent.KEY.get(context.player());
                    if (targetShop.balance < 200) {

                        context.player().displayClientMessage(Component.translatable(
                                "message.noellesroles.nitori_exchange.failed.nomoney"), true);
                        return false;
                    }

                    if (RoleUtils.insertStackInFreeSlot(target, it.copy())) {
                        targetShop.addToBalance(-200);
                        selfShop.addToBalance(200);
                        context.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        context.player().displayClientMessage(Component.translatable(
                                "message.noellesroles.nitori_exchange.success", it.getDisplayName()), true);
                        return true;
                    }
                    return false;
                }).cooldownSeconds(30).build());
    }
}
