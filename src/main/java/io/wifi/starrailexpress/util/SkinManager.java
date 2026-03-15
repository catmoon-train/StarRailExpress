package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.item.Colors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 皮肤管理工具类，用于处理物品皮肤相关的操作
 */
public class SkinManager {
    public static class Skin {
        public final int color;
        public final String tooltipName;
        public final Random random;

        Skin(int color, String tooltipName) {
            this.color = color;
            this.tooltipName = tooltipName;
            this.random = new Random();
        }

        public String getName() {
            return this.tooltipName.toLowerCase(Locale.ROOT);
        }

        public int getColor() {
            return this.color;
        }

        public static Skin fromString(String name) {
            if (skinMap.containsKey(name.toLowerCase(Locale.ROOT))) {
                return skinMap.get(name.toLowerCase(Locale.ROOT));
            }
            return skinMap.get("default");
        }

//        public static Skin getNext(Skin skin) {
//            Skin[] values = Skin.values();
//            return values[(skin.ordinal() + 1) % values.length];
//        }
    }
    public static final Skin DEFAULT_SKIN = new Skin(Colors.LIGHT_GRAY, "Kitchen Knife");
    protected static final Map<String, Skin> skinMap = new HashMap<>();
    static {
        // toolTipName 请使用材质文件名（或者说材质文件请使用同名,除了default）
        skinMap.put("default", DEFAULT_SKIN);
        skinMap.put("ceremonial", new Skin(0xFFD98C28, "ceremonial_dagger"));
        skinMap.put("pick", new Skin(0xFF8D4A51, "knife_pick"));
        skinMap.put("diagonal_blade", new Skin(0xFF4AEDFF, "knife_diamond_knife"));
        skinMap.put("dagger", new Skin(0xFF808080, "knife_dagger"));
        skinMap.put("rainbow_knife", new Skin(0xFFFFFFFF, "knife_rainbow_knife"));
        skinMap.put("fly_cutter", new Skin(0xFFE0E0E0, "knife_fly_cutter"));
        skinMap.put("storm_blade", new Skin(0xFF4A90E2, "knife_storm_blade"));
        skinMap.put("dragon_blade", new Skin(0xFFFF4444, "knife_dragon_blade"));
        skinMap.put("chopper", new Skin(0xFF8B4513, "knife_chopper"));
        skinMap.put("neptune_knife", new Skin(0xFF1E90FF, "knife_neptune_knife"));
        skinMap.put("colorful_folding_knife", new Skin(0xFFFF69B4, "knife_colorful_folding_knife"));
        skinMap.put("edge_knife", new Skin(0xFFC0C0C0, "knife_edge_knife"));
    }
    public static Map<String, Skin> getSkins() {
        return skinMap;
    }
    /**
     * 获取玩家当前装备的皮肤名称
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @return 皮肤名称
     */
    public static String getEquippedSkin(Player player, ItemStack itemStack) {
        // ItemStack数据优先级高于玩家自身
        CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData != null)
            if (customData.contains("train_custom_skin")) {
                var tags = customData.copyTag();
                var skin_tag = tags.get("train_custom_skin");

                String skinName = skin_tag.getAsString();
                if (skinName != null) {
                    return skinName;
                }
            }
        // 从玩家component获取
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        return skinsComponent.getSkinFromDataSync(itemStack);
    }

    /**
     * 设置玩家当前装备的皮肤
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     */
    public static void setEquippedSkin(Player player, ItemStack itemStack, String skinName) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.setEquippedSkin(itemStack, skinName);
        skinsComponent.setSkinInDataSync(itemStack, skinName);
        skinsComponent.syncSkinsToClient();
    }

    /**
     * 检查玩家是否解锁了某个皮肤
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     * @return 是否解锁
     */
    public static boolean isSkinUnlocked(Player player, ItemStack itemStack, String skinName) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        return skinsComponent.isSkinUnlocked(itemStack, skinName);

    }

    /**
     * 解锁皮肤给玩家
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     */
    public static void unlockSkin(Player player, ItemStack itemStack, String skinName) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.unlockSkin(itemStack, skinName);
        skinsComponent.syncSkinsToClient();
    }

    /**
     * 锁定皮肤（移除解锁状态）
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     */
    public static void lockSkin(Player player, ItemStack itemStack, String skinName) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.lockSkin(itemStack, skinName);
        skinsComponent.syncSkinsToClient();
    }

    /**
     * 解锁指定物品类型的皮肤
     *
     * @param player       玩家
     * @param itemTypeName 物品类型名称
     * @param skinName     皮肤名称
     */
    public static void unlockSkinForItemType(Player player, String itemTypeName, String skinName) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.unlockSkinForItemType(itemTypeName, skinName);
        skinsComponent.syncSkinsToClient();
        // skinsComponent.syncSkinsToNetwork();
    }

    /**
     * 设置指定物品类型的装备皮肤
     *
     * @param player       玩家
     * @param itemTypeName 物品类型名称
     * @param skinName     皮肤名称
     */
    public static void setEquippedSkinForItemType(Player player, String itemTypeName, String skinName) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.setEquippedSkinForItemType(itemTypeName, skinName);
        skinsComponent.syncSkinsToClient();

    }

    /**
     * 从物品堆栈获取物品类型名称
     *
     * @param itemStack 物品堆栈
     * @return 物品类型名称
     */
    public static String getItemTypeName(ItemStack itemStack) {
        Item item = itemStack.getItem();
        String itemId = BuiltInRegistries.ITEM.getKey(item).getPath();
        return itemId.toLowerCase();
    }
}