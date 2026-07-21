package io.wifi.starrailexpress.util;

import io.wifi.starrailexpress.content.item.Colors;
import io.wifi.starrailexpress.data.PlayerEconomyManager;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
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
public class ItemSkinManager {
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

    // Hat skins
    public static class HatSkin {
        public static final Skin HAT_DEFAULT_SKIN = new Skin(Colors.GRAY, "default");
    }

    public static void registerACustomSkin(String skinType, String skinID, int color) {
        skinMap.putIfAbsent(skinType, new HashMap<>());
        skinMap.get(skinType).put(skinID, new Skin(color, skinID));
        // 分配皮肤整数ID（如未分配），用于网络包的高效同步
        skinIdByTypeMap.computeIfAbsent(skinType, k -> new HashMap<>());
        skinByIdTypeMap.computeIfAbsent(skinType, k -> new HashMap<>());
        if (!skinIdByTypeMap.get(skinType).containsKey(skinID)) {
            int id = skinIdByTypeMap.get(skinType).size();
            skinIdByTypeMap.get(skinType).put(skinID, id);
            skinByIdTypeMap.get(skinType).put(id, skinID);
        }
    }

    /**
     * 获取皮肤类型的整数ID
     */
    public static int getSkinTypeId(String typeName) {
        return skinTypeIdMap.getOrDefault(typeName, -1);
    }

    /**
     * 根据整数ID获取皮肤类型名称
     */
    public static String getSkinTypeById(int id) {
        return skinTypeByIdMap.get(id);
    }

    /**
     * 获取指定类型中皮肤的整数ID
     */
    public static int getSkinId(String typeName, String skinName) {
        HashMap<String, Integer> typeMap = skinIdByTypeMap.get(typeName);
        if (typeMap == null)
            return -1;
        return typeMap.getOrDefault(skinName, -1);
    }

    /**
     * 根据整数ID获取指定类型中皮肤的名称
     */
    public static String getSkinById(String typeName, int id) {
        HashMap<Integer, String> typeMap = skinByIdTypeMap.get(typeName);
        if (typeMap == null)
            return null;
        return typeMap.get(id);
    }

    public static class SkinTypes {
        public static final String KNIFE = "knife";
        public static final String REVOLVER = "revolver";
        public static final String BAT = "bat";
        public static final String GRENADE = "grenade";
        public static final String HAT = "hat";
    }

    protected static final HashMap<String, HashMap<String, Skin>> skinMap = new HashMap<>();
    // 皮肤类型ID映射（type name → int ID），用于网络包的高效同步
    private static final HashMap<String, Integer> skinTypeIdMap = new HashMap<>();
    // 皮肤类型反向映射（int ID → type name）
    private static final HashMap<Integer, String> skinTypeByIdMap = new HashMap<>();
    // 皮肤名称ID映射（type name → (skin name → int ID)）
    private static final HashMap<String, HashMap<String, Integer>> skinIdByTypeMap = new HashMap<>();
    // 皮肤名称反向映射（type name → (int ID → skin name)）
    private static final HashMap<String, HashMap<Integer, String>> skinByIdTypeMap = new HashMap<>();
    private static int registerId = 0;

    public static void registerType(String skinType) {
        skinTypeIdMap.putIfAbsent(skinType, registerId);
        skinTypeByIdMap.putIfAbsent(registerId, skinType);
        skinIdByTypeMap.putIfAbsent(skinType, new HashMap<>());
        skinByIdTypeMap.putIfAbsent(skinType, new HashMap<>());
        skinMap.putIfAbsent(skinType, new HashMap<>());
        registerId++;
    }

    static {
        // 初始化皮肤类型ID映射（顺序固定，确保服务端和客户端一致）
        String[] typeOrder = { SkinTypes.KNIFE, SkinTypes.REVOLVER, SkinTypes.BAT, SkinTypes.GRENADE, SkinTypes.HAT };
        for (int i = 0; i < typeOrder.length; i++) {
            registerType(typeOrder[i]);
        }
        // 更新：可以不提供默认材质
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
        return PlayerEconomyManager.getLootChance(player);
    }

    public static void addLootChance(Player player, Integer chance) {
        PlayerEconomyManager.addLootChance(player, chance == null ? 0 : chance);
    }

    public static Integer getCoinNum(Player player) {
        return PlayerEconomyManager.getCoinNum(player);
    }

    public static void addCoinNum(Player player, Integer num) {
        PlayerEconomyManager.addCoinNum(player, num == null ? 0 : num);
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
        return PlayerEconomyManager.getEquippedSkin(player, itemStack);
    }

    /**
     * 设置玩家当前装备的皮肤
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     */
    public static void setEquippedSkin(Player player, ItemStack itemStack, String skinName) {
        PlayerEconomyManager.setEquippedSkinForItemType(player, getItemTypeName(itemStack), skinName);
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
        return PlayerEconomyManager.isSkinUnlocked(player, itemStack, skinName);

    }

    /**
     * 解锁皮肤给玩家
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     */
    public static void unlockSkin(Player player, ItemStack itemStack, String skinName) {
        PlayerEconomyManager.unlockSkin(player, itemStack, skinName);
    }

    /**
     * 锁定皮肤（移除解锁状态）
     *
     * @param player    玩家
     * @param itemStack 物品堆栈
     * @param skinName  皮肤名称
     */
    public static void lockSkin(Player player, ItemStack itemStack, String skinName) {
        PlayerEconomyManager.lockSkinForItemType(player, getItemTypeName(itemStack), skinName);
    }

    /**
     * 解锁指定物品类型的皮肤
     *
     * @param player       玩家
     * @param itemTypeName 物品类型名称
     * @param skinName     皮肤名称
     */
    public static void sync(Player player) {
        PlayerEconomyManager.flushBlocking(player.getUUID());
    }

    public static void unlockSkinForItemTypeNoSync(Player player, String itemTypeName, String skinName) {
        PlayerEconomyManager.unlockSkinForItemType(player, itemTypeName, skinName);
    }

    public static void unlockSkinForItemType(Player player, String itemTypeName, String skinName) {
        PlayerEconomyManager.unlockSkinForItemType(player, itemTypeName, skinName);
    }

    /**
     * 设置指定物品类型的装备皮肤
     *
     * @param player       玩家
     * @param itemTypeName 物品类型名称
     * @param skinName     皮肤名称
     */
    public static void setEquippedSkinForItemType(Player player, String itemTypeName, String skinName) {
        PlayerEconomyManager.setEquippedSkinForItemType(player, itemTypeName, skinName);
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
