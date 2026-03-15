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
    public static final Skin DEFAULT_SKIN = new Skin(Colors.LIGHT_GRAY, "default");
    
    // Revolver skins
    public static class RevolverSkin extends Skin {
        public static final Skin REVOLVER_DEFAULT_SKIN = new Skin(Colors.GRAY, "revolver");

    public static RevolverSkin fromString(String name) {
            Map<String, Skin> skins = SkinManager.getRevolverSkins();
            if (skins.containsKey(name.toLowerCase(Locale.ROOT))) {
                return (RevolverSkin) skins.get(name.toLowerCase(Locale.ROOT));
            }
            return (RevolverSkin) REVOLVER_DEFAULT_SKIN;
        }

        RevolverSkin(int color, String tooltipName) {
            super(color, tooltipName);
        }
    }
    
    // Grenade skins
    public static class GrenadeSkin extends Skin {
        public static final Skin GRENADE_DEFAULT_SKIN = new Skin(Colors.GRAY, "grenade");

        public static GrenadeSkin fromString(String name) {
            Map<String, Skin> skins = SkinManager.getGrenadeSkins();
            if (skins.containsKey(name.toLowerCase(Locale.ROOT))) {
                return (GrenadeSkin) skins.get(name.toLowerCase(Locale.ROOT));
            }
            return (GrenadeSkin) GRENADE_DEFAULT_SKIN;
        }

        GrenadeSkin(int color, String tooltipName) {
            super(color, tooltipName);
        }
    }
    
    // Bat skins
    public static class BatSkin extends Skin {
        public static final Skin BAT_DEFAULT_SKIN = new Skin(Colors.GRAY, "bat");

        public static BatSkin fromString(String name) {
            Map<String, Skin> skins = SkinManager.getBatSkins();
            if (skins.containsKey(name.toLowerCase(Locale.ROOT))) {
                return (BatSkin) skins.get(name.toLowerCase(Locale.ROOT));
            }
            return (BatSkin) BAT_DEFAULT_SKIN;
        }

        BatSkin(int color, String tooltipName) {
            super(color, tooltipName);
        }
    }
    
    protected static final Map<String, Skin> skinMap = new HashMap<>();
    protected static final Map<String, Skin> revolverSkinMap = new HashMap<>();
    protected static final Map<String, Skin> grenadeSkinMap = new HashMap<>();
    protected static final Map<String, Skin> batSkinMap = new HashMap<>();
    static {
        // toolTipName 请使用材质文件名（或者说材质文件请使用同名,除了default）
        skinMap.put("default", DEFAULT_SKIN);
        skinMap.put("ceremonial", new Skin(0xFFD98C28, "ceremonial"));
        skinMap.put("pick", new Skin(0xFF8D4A51, "pick"));
        skinMap.put("diamond_knife", new Skin(0xFF4AEDFF, "diamond_knife"));
        skinMap.put("dagger", new Skin(0xFF808080, "dagger"));
        skinMap.put("rainbow_knife", new Skin(0xFFFFFFFF, "rainbow_knife"));
        skinMap.put("fly_cutter", new Skin(0xFFE0E0E0, "fly_cutter"));
        skinMap.put("storm_blade", new Skin(0xFF4A90E2, "storm_blade"));
        skinMap.put("dragon_blade", new Skin(0xFFFF4444, "dragon_blade"));
        skinMap.put("chopper", new Skin(0xFF8B4513, "chopper"));
        skinMap.put("neptune_knife", new Skin(0xFF1E90FF, "neptune_knife"));
        skinMap.put("colorful_folding_knife", new Skin(0xFFFF69B4, "colorful_folding_knife"));
        skinMap.put("edge_knife", new Skin(0xFFC0C0C0, "edge_knife"));
        skinMap.put("blue_curved_knife", new Skin(0xFF1E90FF, "blue_curved_knife"));
        skinMap.put("balisong", new Skin(0xFFC0C0C0, "balisong"));
        skinMap.put("black_blade", new Skin(0xFF1A1A1A, "black_blade"));
        skinMap.put("blade_of_blood_red", new Skin(0xFF8B0000, "blade_of_blood_red"));
        skinMap.put("blue_knife", new Skin(0xFF4169E1, "blue_knife"));
        skinMap.put("carrot_knife", new Skin(0xFFFF8C00, "carrot_knife"));
        skinMap.put("cat_paw", new Skin(0xFFFFDAB9, "cat_paw"));
        skinMap.put("cultist", new Skin(0xFF2F4F4F, "cultist"));
        skinMap.put("cutter_knife", new Skin(0xFFA9A9A9, "cutter_knife"));
        skinMap.put("dart", new Skin(0xFF4682B4, "dart"));
        skinMap.put("diamond_knife", new Skin(0xFF4AEDFF, "diamond_knife"));
        skinMap.put("dusks_epitaph", new Skin(0xFF483D8B, "dusks_epitaph"));
        skinMap.put("fork", new Skin(0xFFC0C0C0, "fork"));
        skinMap.put("icicle", new Skin(0xFFADD8E6, "icicle"));
        skinMap.put("light_sword", new Skin(0xFFFFFF00, "light_sword"));
        skinMap.put("machete", new Skin(0xFF2E8B57, "machete"));
        skinMap.put("matchstick_sword", new Skin(0xFFDEB887, "matchstick_sword"));
        skinMap.put("missing_source", new Skin(0xFF808080, "missing_source"));
        skinMap.put("missing_sword", new Skin(0xFFA0A0A0, "missing_sword"));
        skinMap.put("moqingyu", new Skin(0xFF228B22, "moqingyu"));
        skinMap.put("nail", new Skin(0xFF696969, "nail"));
        skinMap.put("peach_stick", new Skin(0xFFFFE4B5, "peach_stick"));
        skinMap.put("red_light_sword", new Skin(0xFFFF4500, "red_light_sword"));
        skinMap.put("starlight", new Skin(0xFF87CEEB, "starlight"));
        skinMap.put("sword_in_stone", new Skin(0xFF778899, "sword_in_stone"));
        
        // Initialize revolver skins
        revolverSkinMap.put("default", RevolverSkin.REVOLVER_DEFAULT_SKIN);
        revolverSkinMap.put("double_pistol", new RevolverSkin(0xFF808080, "double_pistol"));
        revolverSkinMap.put("heavy_pistol", new RevolverSkin(0xFF404040, "heavy_pistol"));
        revolverSkinMap.put("knife_gun", new RevolverSkin(0xFF606060, "knife_gun"));
        revolverSkinMap.put("potato_launcher", new RevolverSkin(0xFFD2B48C, "potato_launcher"));
        revolverSkinMap.put("stick_gun", new RevolverSkin(0xFF8B4513, "stick_gun"));
        revolverSkinMap.put("water_gun", new RevolverSkin(0xFF4169E1, "water_gun"));
        revolverSkinMap.put("west_revolver", new RevolverSkin(0xFF8B7355, "west_revolver"));
        revolverSkinMap.put("white_gun", new RevolverColorSkin(0xFFFAFAFA, "white_gun"));
        
        // Initialize grenade skins
        grenadeSkinMap.put("default", GrenadeSkin.GRENADE_DEFAULT_SKIN);
        grenadeSkinMap.put("big_bomb", new GrenadeSkin(0xFF000000, "big_bomb"));
        grenadeSkinMap.put("fire_charge", new GrenadeSkin(0xFFFF4500, "fire_charge"));
        grenadeSkinMap.put("magnetic_bomb", new GrenadeSkin(0xFF0000FF, "magnetic_bomb"));
        grenadeSkinMap.put("mobile", new GrenadeSkin(0xFF00CED1, "mobile"));
        grenadeSkinMap.put("oppo", new GrenadeSkin(0xFF00FF7F, "oppo"));
        
        // Initialize bat skins
        batSkinMap.put("default", BatSkin.BAT_DEFAULT_SKIN);
        batSkinMap.put("bread", new BatSkin(0xFFF5DEB3, "bread"));
        batSkinMap.put("red_axe", new BatSkin(0xFFDC143C, "red_axe"));
        batSkinMap.put("steel_tube", new BatSkin(0xFF4682B4, "steel_tube"));
        batSkinMap.put("wolfteeth_mace", new BatSkin(0xFF708090, "wolfteeth_mace"));
    }
    
    // Helper class for revolver skins with special color
    private static class RevolverColorSkin extends RevolverSkin {
        RevolverColorSkin(int color, String tooltipName) {
            super(color, tooltipName);
        }
    }
    public static Map<String, Skin> getSkins() {
        return skinMap;
    }
    
    public static Map<String, Skin> getRevolverSkins() {
        return revolverSkinMap;
    }
    
    public static Map<String, Skin> getGrenadeSkins() {
        return grenadeSkinMap;
    }
    
    public static Map<String, Skin> getBatSkins() {
        return batSkinMap;
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