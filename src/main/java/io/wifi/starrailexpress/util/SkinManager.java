package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.cca.SREPlayerSkinsComponent;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.item.Colors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.awt.*;
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
    public enum QualityColor {
        COMMON(new Color(0xFFEEEEEE).getRGB()),
        UNCOMMON(new Color(0xFF33FF55).getRGB()),
        RARE(new Color(0xFFAAAAFF).getRGB()),
        EPIC(new Color(0xFFAA55FF).getRGB()),
        LEGENDARY(new Color(0xFFFFAA55).getRGB()),
        UNBELIEVABLE(new Color(0xFFFF3F3F).getRGB());

        QualityColor(int i) {
            color = i;
        }
        private final int color;

        public int getColor() {
            return color;
        }
    }
    public static Skin getSkinFromName(String itemType, String name) {
        if (!skinMap.containsKey(itemType)) {
            return null;
        }
        var childSkinMap = skinMap.get(itemType);
        if (childSkinMap.containsKey(name.toLowerCase(Locale.ROOT))) {
            return childSkinMap.get(name.toLowerCase(Locale.ROOT));
        }
        return childSkinMap.get("default");
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
        // 更新：可以不提供默认材质

        // API
        registerSkin(SkinTypes.KNIFE, "ceremonial", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "pick", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "diamond_knife", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "dagger", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "rainbow_knife", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "fly_cutter", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "storm_blade", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "dragon_blade", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "chopper", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "neptune_knife", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "colorful_folding_knife", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "edge_knife", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "blue_curved_knife", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.KNIFE, "balisong", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "black_blade", QualityColor.COMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "blade_of_blood_red", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.KNIFE, "blue_knife", QualityColor.COMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "carrot_knife", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "cat_paw", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "cultist", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.KNIFE, "cutter_knife", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "dart", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "diamond_knife", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "dusks_epitaph", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "fork", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "icicle", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "light_sword", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "machete", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.KNIFE, "matchstick_sword", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "missing_source", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "missing_sword", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "herring_sword_fish", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "nail", QualityColor.COMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "peach_stick", QualityColor.COMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "red_light_sword", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.KNIFE, "starlight", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "sword_in_stone", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "harpy_star", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.KNIFE, "quenched_titanium", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "tianjie_bit", QualityColor.LEGENDARY.getColor());

        // New knife skins
        registerSkin(SkinTypes.KNIFE, "bear_claw", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.KNIFE, "broken_bottle", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "chicken_sword", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "ew_knife", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "flaying_knife", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "flesh_and_blood_resonance", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "foxy_blade", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "ice_fish", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "katar", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "kunai", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.KNIFE, "ninja_claw", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "real_sword", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "small_real_knife", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "steel_claw", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "swiss_army_knife", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "tenet", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "thousands_source", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "zenith_knife", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "gamma_doppler_claw_knife", QualityColor.UNBELIEVABLE.getColor());

        // New knife skins 2025
        registerSkin(SkinTypes.KNIFE, "crystalline", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "folly_stick", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "glass", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.KNIFE, "golden_shear", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "jolly_stick", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.KNIFE, "makeshift", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.KNIFE, "roze", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "sweet_tooth", QualityColor.UNBELIEVABLE.getColor());

        // New knife skins 2026
        registerSkin(SkinTypes.KNIFE, "echoium_sword", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "giant_roasted_chicken", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "sacrificial_dagger", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "tianyuan_fairy", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "unconscious_knife", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.KNIFE, "tenet", QualityColor.LEGENDARY.getColor());

        // Initialize revolver skins
        registerSkin(SkinTypes.REVOLVER, "double_pistol", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "heavy_pistol", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "knife_gun", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "potato_launcher", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "stick_gun", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "water_gun", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.REVOLVER, "west_revolver", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.REVOLVER, "white_gun", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.REVOLVER, "desert_eagle", QualityColor.EPIC.getColor());

        // New gun skins (registerSkin uses REVOLVER type)
        registerSkin(SkinTypes.REVOLVER, "anshidian", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "cannon", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "caplock_pistol", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "coal_gun", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "colt_45", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "dragon_fractal", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "european_long_revolver", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "golden_gun", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "g18", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "habilis", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "hummingbird", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "infinity", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "izumo_41_style", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "lengcui", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "m3", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "margas_flintlock", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "minimalist_line", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "nail_gun", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "pixel_gun", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "potato_launcher", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "rust_lake", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "shengxuan_white", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "signal_gun", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.REVOLVER, "sine_wave", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.REVOLVER, "soul_cairn", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "stick_gun", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "time", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "uzi", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "water_gun", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.REVOLVER, "west_revolver", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.REVOLVER, "white_gun", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.REVOLVER, "wood_gun", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "woodcarving_pistol", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "carved_emperor", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "kekedi", QualityColor.EPIC.getColor());

        // New gun skins 2026
        registerSkin(SkinTypes.REVOLVER, "art_tyrant", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "burn_out_sulfur", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.REVOLVER, "electrodynamics", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.REVOLVER, "qianxia", QualityColor.EPIC.getColor());

        // PVZ gun skins
        registerSkin(SkinTypes.REVOLVER, "pvz_peashooter", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.REVOLVER, "pvz_icemelon_gun", QualityColor.UNBELIEVABLE.getColor());

        // Initialize grenade skins
        registerSkin(SkinTypes.GRENADE, "big_bomb", QualityColor.COMMON.getColor());
        registerSkin(SkinTypes.GRENADE, "minecraft_tnt", QualityColor.COMMON.getColor());
        registerSkin(SkinTypes.GRENADE, "fire_charge", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.GRENADE, "magnetic_bomb", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "mobile", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "phone", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.GRENADE, "gas_cylinder", QualityColor.RARE.getColor());

        // New grenade skins
        registerSkin(SkinTypes.GRENADE, "bottled_flame", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.GRENADE, "brown_substance", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.GRENADE, "coordinate_system", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.GRENADE, "detonator", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.GRENADE, "exponential_explosion", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.GRENADE, "flying_knife_grenade", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.GRENADE, "fragmentation_grenade", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.GRENADE, "king_ball", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.GRENADE, "markov_chain", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.GRENADE, "mini_nuke", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "naval_mine", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "nugrenade", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "o_god_grenade", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.GRENADE, "pisces", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "rainbow_crepper_grenade", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.GRENADE, "rainbow_fireworks", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "rocket", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.GRENADE, "scorpio", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.GRENADE, "shiguimian", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.GRENADE, "submunition_mine", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "tnt", QualityColor.COMMON.getColor());

        // New grenade skins 2026
        registerSkin(SkinTypes.GRENADE, "lemon_grenade", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.GRENADE, "molotov_cocktail", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.GRENADE, "null_grenade", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "poop", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.GRENADE, "rock", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "voice_star", QualityColor.EPIC.getColor());

        // PVZ grenade skins
        registerSkin(SkinTypes.GRENADE, "pvz_cherrybomb", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "pvz_destruction_mushrooms", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.GRENADE, "pvz_jalapeno", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.GRENADE, "pvz_joke_box", QualityColor.LEGENDARY.getColor());

        // New grenade skins 2026
        registerSkin(SkinTypes.GRENADE, "slime_redstone_torch", QualityColor.RARE.getColor());

        // Initialize bat skins
        registerSkin(SkinTypes.BAT, "bread", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.BAT, "red_axe", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.BAT, "steel_tube", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.BAT, "wolfteeth_mace", QualityColor.EPIC.getColor());

        // New bat skins
        registerSkin(SkinTypes.BAT, "astral_defense", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.BAT, "advanced_crowbar", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.BAT, "anvil", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.BAT, "bamboo_bat", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.BAT, "bamboo", QualityColor.COMMON.getColor());
        registerSkin(SkinTypes.BAT, "baseball_bat", QualityColor.COMMON.getColor());
        registerSkin(SkinTypes.BAT, "battlesign", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.BAT, "between_limits", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.BAT, "blood_bat", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.BAT, "composite_club", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.BAT, "cylinder", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.BAT, "diamond_pickaxe", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.BAT, "fried_legs", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.BAT, "hammer", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.BAT, "huaqiangbei", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.BAT, "ice_bat", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.BAT, "iron_hammer", QualityColor.EPIC.getColor());
        registerSkin(SkinTypes.BAT, "ore_pickaxe", QualityColor.RARE.getColor());
        registerSkin(SkinTypes.BAT, "pipe", QualityColor.UNCOMMON.getColor());
        registerSkin(SkinTypes.BAT, "plasma_axe", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.BAT, "road_roller", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.BAT, "sfa", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.BAT, "slippers", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.BAT, "wrench", QualityColor.UNCOMMON.getColor());

        // New bat skins 2026
        registerSkin(SkinTypes.BAT, "guitar", QualityColor.LEGENDARY.getColor());

        // PVZ bat skins
        registerSkin(SkinTypes.BAT, "pvz_newspaper", QualityColor.UNBELIEVABLE.getColor());
        registerSkin(SkinTypes.BAT, "pvz_tall_peanut", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.BAT, "pvz_wire_pole", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.BAT, "pvz_zombie_bat", QualityColor.LEGENDARY.getColor());
        registerSkin(SkinTypes.BAT, "pvz_zombie_skin_bat", QualityColor.UNBELIEVABLE.getColor());
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
    public static void sync(Player player) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.syncSkinsToNetwork();
    }

    public static void unlockSkinForItemTypeNoSync(Player player, String itemTypeName, String skinName) {
        SREPlayerSkinsComponent skinsComponent = SREPlayerSkinsComponent.KEY.get(player);
        skinsComponent.unlockSkinForItemType(itemTypeName, skinName);
        // skinsComponent.syncSkinsToNetwork();
    }

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