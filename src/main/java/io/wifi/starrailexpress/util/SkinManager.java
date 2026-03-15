package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.item.Colors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Locale;

/**
 * 皮肤管理工具类，用于处理物品皮肤相关的操作
 */
public class SkinManager {
    public static class Skin {
        public final int color;
        public final String tooltipName;

        Skin(int color, String tooltipName) {
            this.color = color;
            this.tooltipName = tooltipName;
        }

        public String getName() {
            return this.tooltipName.toLowerCase(Locale.ROOT);
        }

        public int getColor() {
            return this.color;
        }

        public static Skin fromString(String itemType, String name) {
            if (!skinMap.containsKey(itemType)) {
                return null;
            }
            var childSkinMap = skinMap.get(itemType);
            if (childSkinMap.containsKey(name.toLowerCase(Locale.ROOT))) {
                return childSkinMap.get(name.toLowerCase(Locale.ROOT));
            }
            return childSkinMap.get("default");
        }

        // public static Skin getNext(Skin skin) {
        // Skin[] values = Skin.values();
        // return values[(skin.ordinal() + 1) % values.length];
        // }
    }

    public static class KnifeSkin {
        public static final Skin DEFAULT_SKIN = new Skin(Colors.LIGHT_GRAY, "default");
    }

    // Revolver skins
    public static class RevolverSkin {
        public static final Skin REVOLVER_DEFAULT_SKIN = new Skin(Colors.GRAY, "default");
    }

    // Grenade skins
    public static class GrenadeSkin {
        public static final Skin GRENADE_DEFAULT_SKIN = new Skin(Colors.GRAY, "default");
    }

    // Bat skins
    public static class BatSkin {
        public static final Skin BAT_DEFAULT_SKIN = new Skin(Colors.GRAY, "default");
    }

    public static void registerSkin(String skinType, String skinID, int color) {
        skinMap.putIfAbsent(skinType, new HashMap<>());
        skinMap.get(skinType).put(skinID, new Skin(color, skinID));
    }
    public static class SkinTypes {
        public static final String KNIFE = "knife";
        public static final String REVOLVER = "revolver";
        public static final String BAT = "bat";
        public static final String GRENADE = "grenade";
    }

    protected static final HashMap<String, HashMap<String, Skin>> skinMap = new HashMap<>();
    static {
        skinMap.put(SkinTypes.KNIFE, new HashMap<>());
        skinMap.put(SkinTypes.REVOLVER, new HashMap<>());
        skinMap.put(SkinTypes.BAT, new HashMap<>());
        skinMap.put(SkinTypes.GRENADE, new HashMap<>());
        // 默认材质
        skinMap.get(SkinTypes.KNIFE).put("default", KnifeSkin.DEFAULT_SKIN);
        
        skinMap.get(SkinTypes.BAT).put("default", BatSkin.BAT_DEFAULT_SKIN);
        skinMap.get(SkinTypes.GRENADE).put("default", GrenadeSkin.GRENADE_DEFAULT_SKIN);
        skinMap.get(SkinTypes.REVOLVER).put("default", RevolverSkin.REVOLVER_DEFAULT_SKIN);

        // API
        registerSkin(SkinTypes.KNIFE, "ceremonial", 0xFFD98C28);
        registerSkin(SkinTypes.KNIFE, "pick", 0xFF8D4A51);
        registerSkin(SkinTypes.KNIFE, "diamond_knife", 0xFF4AEDFF);
        registerSkin(SkinTypes.KNIFE, "dagger", 0xFF808080);
        registerSkin(SkinTypes.KNIFE, "rainbow_knife", 0xFFFFFFFF);
        registerSkin(SkinTypes.KNIFE, "fly_cutter", 0xFFE0E0E0);
        registerSkin(SkinTypes.KNIFE, "storm_blade", 0xFF4A90E2);
        registerSkin(SkinTypes.KNIFE, "dragon_blade", 0xFFFF4444);
        registerSkin(SkinTypes.KNIFE, "chopper", 0xFF8B4513);
        registerSkin(SkinTypes.KNIFE, "neptune_knife", 0xFF1E90FF);
        registerSkin(SkinTypes.KNIFE, "colorful_folding_knife", 0xFFFF69B4);
        registerSkin(SkinTypes.KNIFE, "edge_knife", 0xFFC0C0C0);
        registerSkin(SkinTypes.KNIFE, "blue_curved_knife", 0xFF1E90FF);
        registerSkin(SkinTypes.KNIFE, "balisong", 0xFFC0C0C0);
        registerSkin(SkinTypes.KNIFE, "black_blade", 0xFF1A1A1A);
        registerSkin(SkinTypes.KNIFE, "blade_of_blood_red", 0xFF8B0000);
        registerSkin(SkinTypes.KNIFE, "blue_knife", 0xFF4169E1);
        registerSkin(SkinTypes.KNIFE, "carrot_knife", 0xFFFF8C00);
        registerSkin(SkinTypes.KNIFE, "cat_paw", 0xFFFFDAB9);
        registerSkin(SkinTypes.KNIFE, "cultist", 0xFF2F4F4F);
        registerSkin(SkinTypes.KNIFE, "cutter_knife", 0xFFA9A9A9);
        registerSkin(SkinTypes.KNIFE, "dart", 0xFF4682B4);
        registerSkin(SkinTypes.KNIFE, "diamond_knife", 0xFF4AEDFF);
        registerSkin(SkinTypes.KNIFE, "dusks_epitaph", 0xFF483D8B);
        registerSkin(SkinTypes.KNIFE, "fork", 0xFFC0C0C0);
        registerSkin(SkinTypes.KNIFE, "icicle", 0xFFADD8E6);
        registerSkin(SkinTypes.KNIFE, "light_sword", 0xFFFFFF00);
        registerSkin(SkinTypes.KNIFE, "machete", 0xFF2E8B57);
        registerSkin(SkinTypes.KNIFE, "matchstick_sword", 0xFFDEB887);
        registerSkin(SkinTypes.KNIFE, "missing_source", 0xFF808080);
        registerSkin(SkinTypes.KNIFE, "missing_sword", 0xFFA0A0A0);
        registerSkin(SkinTypes.KNIFE, "moqingyu", 0xFF228B22);
        registerSkin(SkinTypes.KNIFE, "nail", 0xFF696969);
        registerSkin(SkinTypes.KNIFE, "peach_stick", 0xFFFFE4B5);
        registerSkin(SkinTypes.KNIFE, "red_light_sword", 0xFFFF4500);
        registerSkin(SkinTypes.KNIFE, "starlight", 0xFF87CEEB);
        registerSkin(SkinTypes.KNIFE, "sword_in_stone", 0xFF778899);
        registerSkin(SkinTypes.KNIFE, "astral_defense", 0xFF9370DB);
        registerSkin(SkinTypes.KNIFE, "harpy_star", 0xFFFFF8DC);
        registerSkin(SkinTypes.KNIFE, "quenched_titanium", 0xFFB87333);
        registerSkin(SkinTypes.KNIFE, "tianjie_bit", 0xFFFF6347);

        // Initialize revolver skins
        registerSkin(SkinTypes.REVOLVER, "double_pistol", 0xFF808080);
        registerSkin(SkinTypes.REVOLVER, "heavy_pistol", 0xFF404040);
        registerSkin(SkinTypes.REVOLVER, "knife_gun", 0xFF606060);
        registerSkin(SkinTypes.REVOLVER, "potato_launcher", 0xFFD2B48C);
        registerSkin(SkinTypes.REVOLVER, "stick_gun", 0xFF8B4513);
        registerSkin(SkinTypes.REVOLVER, "water_gun", 0xFF4169E1);
        registerSkin(SkinTypes.REVOLVER, "west_revolver", 0xFF8B7355);
        registerSkin(SkinTypes.REVOLVER, "white_gun", 0xFFFAFAFA);
        registerSkin(SkinTypes.REVOLVER, "desert_eagle", 0xFFC0C0C0);

        // Initialize grenade skins
        registerSkin(SkinTypes.GRENADE, "big_bomb", 0xFF000000);
        registerSkin(SkinTypes.GRENADE, "fire_charge", 0xFFFF4500);
        registerSkin(SkinTypes.GRENADE, "magnetic_bomb", 0xFF0000FF);
        registerSkin(SkinTypes.GRENADE, "mobile", 0xFF00CED1);
        registerSkin(SkinTypes.GRENADE, "oppo", 0xFF00FF7F);
        registerSkin(SkinTypes.GRENADE, "gas_cylinder", 0xFF808080);

        // Initialize bat skins
        registerSkin(SkinTypes.BAT, "bread", 0xFFF5DEB3);
        registerSkin(SkinTypes.BAT, "red_axe", 0xFFDC143C);
        registerSkin(SkinTypes.BAT, "steel_tube", 0xFF4682B4);
        registerSkin(SkinTypes.BAT, "wolfteeth_mace", 0xFF708090);
    }

    public static ResourceLocation getResourceLocationOfItem(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    public static HashMap<String, Skin> getSkins(Item it) {
        var itr = getResourceLocationOfItem(it);
        String itemName = null;
        if (itr != null) {
            itemName = itr.getPath();
        }
        return skinMap.getOrDefault(itemName, new HashMap<>());
    }

    public static HashMap<String, Skin> getSkins(String itemName) {
        return skinMap.getOrDefault(itemName, new HashMap<>());
    }

    public static HashMap<String, HashMap<String, Skin>> getSkins() {
        return skinMap;
    }

    public static Integer getLootChance(Player player) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        return skinsComponent.getLootChance();
    }
    public static void addLootChance(Player player, Integer chance) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.addLootChance(chance);
        skinsComponent.syncSkinsToClient();
    }
    public static Integer getCoinNum(Player player) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        return skinsComponent.getCoinNum();
    }
    public static void addCoinNum(Player player, Integer num) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.addCoinNum(num);
        skinsComponent.syncSkinsToClient();
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
        if (itemStack.has(SREDataComponentTypes.SKIN)) {
            return itemStack.get(SREDataComponentTypes.SKIN);
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