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
    public static abstract class Skin {
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

    public static class KnifeSkin extends Skin {
        public static final KnifeSkin DEFAULT_SKIN = new KnifeSkin(Colors.LIGHT_GRAY, "default");

        KnifeSkin(int color, String tooltipName) {
            super(color, tooltipName);
        }
    }

    // Revolver skins
    public static class RevolverSkin extends Skin {
        public static final RevolverSkin REVOLVER_DEFAULT_SKIN = new RevolverSkin(Colors.GRAY, "default");

        RevolverSkin(int color, String tooltipName) {
            super(color, tooltipName);
        }
    }

    // Grenade skins
    public static class GrenadeSkin extends Skin {
        public static final GrenadeSkin GRENADE_DEFAULT_SKIN = new GrenadeSkin(Colors.GRAY, "default");

        GrenadeSkin(int color, String tooltipName) {
            super(color, tooltipName);
        }
    }

    // Bat skins
    public static class BatSkin extends Skin {
        public static final BatSkin BAT_DEFAULT_SKIN = new BatSkin(Colors.GRAY, "default");

        BatSkin(int color, String tooltipName) {
            super(color, tooltipName);
        }
    }

    protected static final HashMap<String, HashMap<String, Skin>> skinMap = new HashMap<>();
    static {
        HashMap<String, Skin> knifeSkin = new HashMap<>();
        HashMap<String, Skin> revolverknifeSkin = new HashMap<>();
        HashMap<String, Skin> batSkin = new HashMap<>();
        HashMap<String, Skin> grenadeSkin = new HashMap<>();

        // toolTipName 请使用材质文件名（或者说材质文件请使用同名,除了default）
        knifeSkin.put("default", KnifeSkin.DEFAULT_SKIN);
        knifeSkin.put("ceremonial", new KnifeSkin(0xFFD98C28, "ceremonial"));
        knifeSkin.put("pick", new KnifeSkin(0xFF8D4A51, "pick"));
        knifeSkin.put("diamond_knife", new KnifeSkin(0xFF4AEDFF, "diamond_knife"));
        knifeSkin.put("dagger", new KnifeSkin(0xFF808080, "dagger"));
        knifeSkin.put("rainbow_knife", new KnifeSkin(0xFFFFFFFF, "rainbow_knife"));
        knifeSkin.put("fly_cutter", new KnifeSkin(0xFFE0E0E0, "fly_cutter"));
        knifeSkin.put("storm_blade", new KnifeSkin(0xFF4A90E2, "storm_blade"));
        knifeSkin.put("dragon_blade", new KnifeSkin(0xFFFF4444, "dragon_blade"));
        knifeSkin.put("chopper", new KnifeSkin(0xFF8B4513, "chopper"));
        knifeSkin.put("neptune_knife", new KnifeSkin(0xFF1E90FF, "neptune_knife"));
        knifeSkin.put("colorful_folding_knife", new KnifeSkin(0xFFFF69B4, "colorful_folding_knife"));
        knifeSkin.put("edge_knife", new KnifeSkin(0xFFC0C0C0, "edge_knife"));
        knifeSkin.put("blue_curved_knife", new KnifeSkin(0xFF1E90FF, "blue_curved_knife"));
        knifeSkin.put("balisong", new KnifeSkin(0xFFC0C0C0, "balisong"));
        knifeSkin.put("black_blade", new KnifeSkin(0xFF1A1A1A, "black_blade"));
        knifeSkin.put("blade_of_blood_red", new KnifeSkin(0xFF8B0000, "blade_of_blood_red"));
        knifeSkin.put("blue_knife", new KnifeSkin(0xFF4169E1, "blue_knife"));
        knifeSkin.put("carrot_knife", new KnifeSkin(0xFFFF8C00, "carrot_knife"));
        knifeSkin.put("cat_paw", new KnifeSkin(0xFFFFDAB9, "cat_paw"));
        knifeSkin.put("cultist", new KnifeSkin(0xFF2F4F4F, "cultist"));
        knifeSkin.put("cutter_knife", new KnifeSkin(0xFFA9A9A9, "cutter_knife"));
        knifeSkin.put("dart", new KnifeSkin(0xFF4682B4, "dart"));
        knifeSkin.put("diamond_knife", new KnifeSkin(0xFF4AEDFF, "diamond_knife"));
        knifeSkin.put("dusks_epitaph", new KnifeSkin(0xFF483D8B, "dusks_epitaph"));
        knifeSkin.put("fork", new KnifeSkin(0xFFC0C0C0, "fork"));
        knifeSkin.put("icicle", new KnifeSkin(0xFFADD8E6, "icicle"));
        knifeSkin.put("light_sword", new KnifeSkin(0xFFFFFF00, "light_sword"));
        knifeSkin.put("machete", new KnifeSkin(0xFF2E8B57, "machete"));
        knifeSkin.put("matchstick_sword", new KnifeSkin(0xFFDEB887, "matchstick_sword"));
        knifeSkin.put("missing_source", new KnifeSkin(0xFF808080, "missing_source"));
        knifeSkin.put("missing_sword", new KnifeSkin(0xFFA0A0A0, "missing_sword"));
        knifeSkin.put("moqingyu", new KnifeSkin(0xFF228B22, "moqingyu"));
        knifeSkin.put("nail", new KnifeSkin(0xFF696969, "nail"));
        knifeSkin.put("peach_stick", new KnifeSkin(0xFFFFE4B5, "peach_stick"));
        knifeSkin.put("red_light_sword", new KnifeSkin(0xFFFF4500, "red_light_sword"));
        knifeSkin.put("starlight", new KnifeSkin(0xFF87CEEB, "starlight"));
        knifeSkin.put("sword_in_stone", new KnifeSkin(0xFF778899, "sword_in_stone"));

        // Initialize revolver skins
        revolverknifeSkin.put("default", RevolverSkin.REVOLVER_DEFAULT_SKIN);
        revolverknifeSkin.put("double_pistol", new RevolverSkin(0xFF808080, "double_pistol"));
        revolverknifeSkin.put("heavy_pistol", new RevolverSkin(0xFF404040, "heavy_pistol"));
        revolverknifeSkin.put("knife_gun", new RevolverSkin(0xFF606060, "knife_gun"));
        revolverknifeSkin.put("potato_launcher", new RevolverSkin(0xFFD2B48C, "potato_launcher"));
        revolverknifeSkin.put("stick_gun", new RevolverSkin(0xFF8B4513, "stick_gun"));
        revolverknifeSkin.put("water_gun", new RevolverSkin(0xFF4169E1, "water_gun"));
        revolverknifeSkin.put("west_revolver", new RevolverSkin(0xFF8B7355, "west_revolver"));
        revolverknifeSkin.put("white_gun", new RevolverColorSkin(0xFFFAFAFA, "white_gun"));

        // Initialize grenade skins
        grenadeSkin.put("default", GrenadeSkin.GRENADE_DEFAULT_SKIN);
        grenadeSkin.put("big_bomb", new GrenadeSkin(0xFF000000, "big_bomb"));
        grenadeSkin.put("fire_charge", new GrenadeSkin(0xFFFF4500, "fire_charge"));
        grenadeSkin.put("magnetic_bomb", new GrenadeSkin(0xFF0000FF, "magnetic_bomb"));
        grenadeSkin.put("mobile", new GrenadeSkin(0xFF00CED1, "mobile"));
        grenadeSkin.put("oppo", new GrenadeSkin(0xFF00FF7F, "oppo"));

        // Initialize bat skins
        batSkin.put("default", BatSkin.BAT_DEFAULT_SKIN);
        batSkin.put("bread", new BatSkin(0xFFF5DEB3, "bread"));
        batSkin.put("red_axe", new BatSkin(0xFFDC143C, "red_axe"));
        batSkin.put("steel_tube", new BatSkin(0xFF4682B4, "steel_tube"));
        batSkin.put("wolfteeth_mace", new BatSkin(0xFF708090, "wolfteeth_mace"));
        skinMap.put("knife", knifeSkin);
        skinMap.put("bat", batSkin);
        skinMap.put("revolver", revolverknifeSkin);
        skinMap.put("grenade", grenadeSkin);
    }

    public static ResourceLocation getResourceLocationOfItem(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    // Helper class for revolver skins with special color
    private static class RevolverColorSkin extends RevolverSkin {
        RevolverColorSkin(int color, String tooltipName) {
            super(color, tooltipName);
        }
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

    /**
     * 获取玩家当前装备的皮肤名称
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @return 皮肤名称
     */
    public static String getEquippedSkin(Player player, ItemStack itemStack) {
        // ItemStack数据优先级高于玩家自身
        if(itemStack.has(SREDataComponentTypes.SKIN)){
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